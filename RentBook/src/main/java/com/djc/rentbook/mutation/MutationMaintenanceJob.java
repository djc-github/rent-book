package com.djc.rentbook.mutation;

import com.djc.rentbook.config.AuditProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class MutationMaintenanceJob {
    private static final Logger log = LoggerFactory.getLogger(MutationMaintenanceJob.class);
    private final MutationMapper mapper;
    private final AuditProperties auditProperties;
    private final Clock clock;

    @Autowired
    public MutationMaintenanceJob(MutationMapper mapper, AuditProperties auditProperties) {
        this(mapper, auditProperties, Clock.system(auditProperties.zoneId()));
    }

    MutationMaintenanceJob(MutationMapper mapper, AuditProperties auditProperties, Clock clock) {
        this.mapper = mapper;
        this.auditProperties = auditProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${rentbook.idempotency.cleanup-cron:0 30 3 * * *}",
            zone = "${rentbook.lease-expire.zone:Asia/Shanghai}")
    @Transactional
    public void cleanupExpiredRecords() {
        int deleted = mapper.deleteExpiredIdempotencyRecords();
        if (deleted > 0) {
            log.info("Cleaned expired idempotency records count={}", deleted);
        }
    }

    @Scheduled(cron = "${rentbook.audit.cleanup-cron:0 45 3 * * *}",
            zone = "${rentbook.audit.zone:Asia/Shanghai}")
    @Transactional
    public void cleanupExpiredAuditLogs() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minusYears(auditProperties.getRetentionYears());
        int dataChangesDeleted = mapper.deleteExpiredDataChangeLogs(cutoff);
        int operationsDeleted = mapper.deleteExpiredOperationLogs(cutoff);
        if (dataChangesDeleted > 0 || operationsDeleted > 0) {
            log.info(
                    "Cleaned expired audit logs retentionYears={}, cutoff={}, operationLogsDeleted={}, dataChangeLogsDeleted={}",
                    auditProperties.getRetentionYears(), cutoff, operationsDeleted, dataChangesDeleted
            );
            return;
        }
        log.debug(
                "Audit log cleanup completed with no expired records retentionYears={}, cutoff={}",
                auditProperties.getRetentionYears(), cutoff
        );
    }
}
