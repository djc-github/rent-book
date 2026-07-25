package com.djc.rentbook.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private final PaymentMapper mapper;

    public PaymentService(PaymentMapper mapper) {
        this.mapper = mapper;
    }

    public PaymentDtos.PaymentPage list(LocalDate from, LocalDate to, String cursor, int limit) {
        int pageSize = normalizeLimit(limit);
        PaymentCursor parsedCursor = parseCursor(cursor);
        log.debug("Listing rent payments from={}, to={}, cursorPresent={}, limit={}", from, to, parsedCursor != null, pageSize);
        List<Map<String, Object>> rows = mapper.listPage(
                from,
                to,
                parsedCursor == null ? null : parsedCursor.paidDate(),
                parsedCursor == null ? null : parsedCursor.createdAt(),
                parsedCursor == null ? null : parsedCursor.id(),
                pageSize + 1
        );
        boolean hasMore = rows.size() > pageSize;
        List<Map<String, Object>> pageRows = hasMore ? new ArrayList<>(rows.subList(0, pageSize)) : rows;
        String nextCursor = hasMore ? encodeCursor(pageRows.get(pageRows.size() - 1)) : null;
        return new PaymentDtos.PaymentPage(pageRows, nextCursor, hasMore);
    }

    @Transactional
    public Long create(PaymentDtos.PaymentRequest request) {
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new IllegalArgumentException("收租周期结束日期不能早于开始日期");
        }
        Long roomId = mapper.findContractRoomIdForUpdate(request.contractId());
        if (roomId == null) {
            throw new IllegalArgumentException("合同不存在或对应房间已删除");
        }
        Long overlappingId = mapper.findOverlappingPaymentId(
                roomId, request.periodStart(), request.periodEnd(), null
        );
        if (overlappingId != null) {
            throw new IllegalArgumentException("该房间对应租期已经登记过收租，请勿重复提交");
        }
        PaymentRecord record = new PaymentRecord();
        record.setContractId(request.contractId());
        record.setPeriodStart(request.periodStart());
        record.setPeriodEnd(request.periodEnd());
        record.setPaidDate(request.paidDate());
        record.setAmount(request.amount());
        record.setMethod(request.method());
        record.setReceiptNo(request.receiptNo());
        record.setNotes(request.notes());
        mapper.create(record);
        mapper.moveNextDueDate(record);
        log.info("Created rent payment id={}, contractId={}, amount={}, periodStart={}, periodEnd={}, paidDate={}, method={}",
                record.getId(), record.getContractId(), record.getAmount(), record.getPeriodStart(), record.getPeriodEnd(),
                record.getPaidDate(), record.getMethod());
        return record.getId();
    }

    @Transactional
    public void delete(Long id) {
        PaymentRecord payment = mapper.find(id);
        if (payment == null) {
            throw new IllegalArgumentException("收租记录不存在");
        }
        if (payment.getRoomId() != null) {
            if (mapper.lockRoom(payment.getRoomId()) == null) {
                throw new IllegalArgumentException("收租记录对应的房间不存在");
            }
        } else if (mapper.findContractRoomIdForUpdate(payment.getContractId()) == null) {
            throw new IllegalArgumentException("收租记录对应的合同不存在");
        }
        payment = mapper.findForUpdate(id);
        if (payment == null) {
            throw new IllegalArgumentException("收租记录已撤销，请刷新后查看");
        }
        if (mapper.delete(id) == 0) {
            throw new IllegalArgumentException("收租记录不存在");
        }
        if (payment.getRoomId() != null) {
            mapper.rollbackRoomNextDueDate(payment);
        } else {
            mapper.rollbackNextDueDate(payment);
        }
        log.info("Deleted rent payment id={}, contractId={}, roomId={}, amount={}, periodStart={}, periodEnd={}",
                id, payment.getContractId(), payment.getRoomId(), payment.getAmount(), payment.getPeriodStart(), payment.getPeriodEnd());
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private PaymentCursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid payment cursor");
            }
            return new PaymentCursor(LocalDate.parse(parts[0]), OffsetDateTime.parse(parts[1]), Long.parseLong(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new IllegalArgumentException("收租记录游标无效，请刷新后重试");
        }
    }

    private String encodeCursor(Map<String, Object> row) {
        LocalDate paidDate = asLocalDate(row.get("paid_date"));
        OffsetDateTime createdAt = asOffsetDateTime(row.get("created_at"));
        Long id = asLong(row.get("id"));
        String value = paidDate + "|" + createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private LocalDate asLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault());
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private record PaymentCursor(LocalDate paidDate, OffsetDateTime createdAt, Long id) {
    }
}
