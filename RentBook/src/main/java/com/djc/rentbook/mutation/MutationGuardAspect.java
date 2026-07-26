package com.djc.rentbook.mutation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Aspect
@Component
public class MutationGuardAspect {
    private static final Logger log = LoggerFactory.getLogger(MutationGuardAspect.class);
    private static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final int MAX_CLIENT_KEY_LENGTH = 120;
    private static final int AUTO_DEBOUNCE_SECONDS = 3;
    private static final int CLIENT_KEY_HOURS = 24;
    private static final int MAX_JSON_LENGTH = 20_000;
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "secret", "token", "authorization", "cookie"
    );

    private final MutationMapper mapper;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public MutationGuardAspect(MutationMapper mapper,
                               OperationLogService operationLogService,
                               ObjectMapper objectMapper,
                               PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Around("@annotation(operation)")
    public Object guard(ProceedingJoinPoint joinPoint, MutationOperation operation) throws Throwable {
        HttpServletRequest request = currentRequest();
        RequestSnapshot snapshot = snapshot(joinPoint, request);
        long startedAt = System.currentTimeMillis();
        String status = "FAILED";
        String responsePayload = null;
        String errorMessage = null;
        try {
            GuardResult result = transactionTemplate.execute(tx -> executeInTransaction(joinPoint, snapshot));
            if (result == null) {
                throw new IllegalStateException("写操作未返回执行结果");
            }
            status = result.replayed() ? "REPLAYED" : "SUCCESS";
            responsePayload = result.responsePayload();
            return result.value();
        } catch (ProceedingRuntimeException ex) {
            errorMessage = safeError(ex.getCause());
            throw ex.getCause();
        } catch (RuntimeException ex) {
            errorMessage = safeError(ex);
            throw ex;
        } finally {
            try {
                operationLogService.record(new OperationLogService.OperationLogWrite(
                        MDC.get("traceId"),
                        snapshot.idempotencyKey(),
                        operation.module(),
                        operation.action(),
                        snapshot.httpMethod(),
                        snapshot.requestPath(),
                        snapshot.requestPayload(),
                        responsePayload,
                        status,
                        errorMessage,
                        System.currentTimeMillis() - startedAt,
                        snapshot.clientIp(),
                        snapshot.userAgent()
                ));
            } catch (RuntimeException auditError) {
                log.error("Failed to persist operation audit traceId={}, module={}, action={}, status={}",
                        MDC.get("traceId"), operation.module(), operation.action(), status, auditError);
            }
        }
    }

    private GuardResult executeInTransaction(ProceedingJoinPoint joinPoint, RequestSnapshot snapshot) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime processingExpiry = now.plusMinutes(5);
        int inserted = mapper.tryCreateIdempotency(
                snapshot.idempotencyKey(), snapshot.requestHash(), snapshot.httpMethod(),
                snapshot.requestPath(), processingExpiry
        );
        if (inserted == 0) {
            IdempotencyRecord existing = mapper.findIdempotencyForUpdate(snapshot.idempotencyKey());
            if (existing == null) {
                throw new IllegalStateException("幂等记录读取失败，请稍后重试");
            }
            if (!existing.getRequestHash().equals(snapshot.requestHash())) {
                throw new IllegalArgumentException("页面内容已经变化，请刷新后重新操作");
            }
            if ("SUCCEEDED".equals(existing.getStatus()) && existing.getExpiresAt().isAfter(now)) {
                return new GuardResult(deserializeResponse(joinPoint, existing.getResponsePayload()),
                        existing.getResponsePayload(), true);
            }
            if ("PROCESSING".equals(existing.getStatus()) && existing.getExpiresAt().isAfter(now)) {
                throw new IllegalArgumentException("正在处理，请稍候");
            }
            mapper.restartIdempotency(
                    snapshot.idempotencyKey(), snapshot.requestHash(), snapshot.httpMethod(),
                    snapshot.requestPath(), processingExpiry
            );
        }

        try {
            mapper.setAuditContext(MDC.get("traceId"), snapshot.idempotencyKey());
            Object value = joinPoint.proceed();
            String serialized = serialize(value);
            OffsetDateTime completedAt = OffsetDateTime.now();
            OffsetDateTime successExpiry = snapshot.clientKey()
                    ? completedAt.plusHours(CLIENT_KEY_HOURS)
                    : completedAt.plusSeconds(AUTO_DEBOUNCE_SECONDS);
            if (mapper.completeIdempotency(snapshot.idempotencyKey(), serialized, successExpiry) != 1) {
                throw new IllegalStateException("幂等状态更新失败，操作已回滚");
            }
            return new GuardResult(value, serialized, false);
        } catch (Throwable ex) {
            throw new ProceedingRuntimeException(ex);
        }
    }

    private RequestSnapshot snapshot(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getRequestURI();
        String requestPayload = serializeArguments(joinPoint);
        String requestHash = sha256(method + "|" + path + "|" + requestPayload);
        String clientValue = trimToNull(request.getHeader(IDEMPOTENCY_HEADER));
        if (clientValue != null && clientValue.length() > MAX_CLIENT_KEY_LENGTH) {
            throw new IllegalArgumentException("操作没有提交成功，请刷新后重试");
        }
        boolean clientKey = clientValue != null;
        String rawKey = clientKey
                ? "client|" + clientValue
                : "auto|" + clientIp(request) + "|" + requestHash;
        return new RequestSnapshot(
                sha256(rawKey), requestHash, method, path, requestPayload,
                clientIp(request), trim(request.getHeader("User-Agent"), 500), clientKey
        );
    }

    private String serializeArguments(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            String name = names != null && i < names.length ? names[i] : "arg" + i;
            if (arg instanceof HttpServletRequest) {
                continue;
            }
            if (arg instanceof MultipartFile file) {
                values.put(name, Map.of(
                        "fileName", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                        "contentType", file.getContentType() == null ? "" : file.getContentType(),
                        "size", file.getSize()
                ));
            } else {
                values.put(name, arg);
            }
        }
        JsonNode tree = objectMapper.valueToTree(values);
        redact(tree);
        return compactJson(tree);
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fields().forEachRemaining(entry -> {
                if (SENSITIVE_KEYS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    objectNode.put(entry.getKey(), "***");
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }

    private String serialize(Object value) {
        try {
            return compactJson(objectMapper.valueToTree(value));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("接口响应序列化失败", ex);
        }
    }

    private String compactJson(JsonNode node) {
        try {
            String json = objectMapper.writeValueAsString(node);
            if (json.length() <= MAX_JSON_LENGTH) {
                return json;
            }
            ObjectNode truncated = objectMapper.createObjectNode();
            truncated.put("truncated", true);
            truncated.put("preview", json.substring(0, MAX_JSON_LENGTH));
            return objectMapper.writeValueAsString(truncated);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("操作参数序列化失败", ex);
        }
    }

    private Object deserializeResponse(ProceedingJoinPoint joinPoint, String json) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            JavaType returnType = objectMapper.getTypeFactory()
                    .constructType(signature.getMethod().getGenericReturnType());
            return objectMapper.readValue(json, returnType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("历史操作响应读取失败，请刷新页面", ex);
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new IllegalStateException("当前写操作不在HTTP请求中");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = trimToNull(request.getHeader("X-Forwarded-For"));
        return forwarded == null ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
    }

    private String safeError(Throwable throwable) {
        return trim(throwable == null ? null : throwable.getMessage(), 1000);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成请求摘要", ex);
        }
    }

    private record RequestSnapshot(
            String idempotencyKey,
            String requestHash,
            String httpMethod,
            String requestPath,
            String requestPayload,
            String clientIp,
            String userAgent,
            boolean clientKey
    ) {
    }

    private record GuardResult(Object value, String responsePayload, boolean replayed) {
    }

    private static final class ProceedingRuntimeException extends RuntimeException {
        private ProceedingRuntimeException(Throwable cause) {
            super(cause);
        }
    }
}
