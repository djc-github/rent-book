package com.djc.rentbook.mutation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MutationMaintenanceJob {
    private static final Logger log = LoggerFactory.getLogger(MutationMaintenanceJob.class);
    private final MutationMapper mapper;

    public MutationMaintenanceJob(MutationMapper mapper) {
        this.mapper = mapper;
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
}
