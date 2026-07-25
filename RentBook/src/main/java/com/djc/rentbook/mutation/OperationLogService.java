package com.djc.rentbook.mutation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class OperationLogService {
    private static final int MAX_PAGE_SIZE = 100;
    private final MutationMapper mapper;

    public OperationLogService(MutationMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(OperationLogWrite write) {
        mapper.insertOperationLog(
                write.traceId(), write.idempotencyKey(), write.module(), write.action(),
                write.httpMethod(), write.requestPath(), write.requestPayload(),
                write.responsePayload(), write.status(), write.errorMessage(),
                write.durationMs(), write.clientIp(), write.userAgent()
        );
    }

    public List<Map<String, Object>> list(String module, String status, Long cursorId, int limit) {
        int pageSize = limit <= 0 ? 20 : Math.min(limit, MAX_PAGE_SIZE);
        return mapper.listOperationLogs(blankToNull(module), blankToNull(status), cursorId, pageSize);
    }

    public List<Map<String, Object>> listChanges(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId不能为空");
        }
        return mapper.listDataChanges(traceId.trim());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record OperationLogWrite(
            String traceId,
            String idempotencyKey,
            String module,
            String action,
            String httpMethod,
            String requestPath,
            String requestPayload,
            String responsePayload,
            String status,
            String errorMessage,
            long durationMs,
            String clientIp,
            String userAgent
    ) {
    }
}
