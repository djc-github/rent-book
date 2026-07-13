package com.djc.rentbook.property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class RoomLeaseExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(RoomLeaseExpiryJob.class);

    private final PropertyService propertyService;
    private final ZoneId zoneId;

    public RoomLeaseExpiryJob(PropertyService propertyService,
                              @Value("${rentbook.lease-expire.zone:Asia/Shanghai}") String zone) {
        this.propertyService = propertyService;
        this.zoneId = ZoneId.of(zone);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void expireOnStartup() {
        expire("startup");
    }

    @Scheduled(cron = "${rentbook.lease-expire.cron:0 10 0 * * *}", zone = "${rentbook.lease-expire.zone:Asia/Shanghai}")
    public void expireDaily() {
        expire("schedule");
    }

    private void expire(String trigger) {
        LocalDate today = LocalDate.now(zoneId);
        log.debug("Checking ended room leases trigger={}, today={}", trigger, today);
        propertyService.expireEndedRoomLeases(today);
    }
}
