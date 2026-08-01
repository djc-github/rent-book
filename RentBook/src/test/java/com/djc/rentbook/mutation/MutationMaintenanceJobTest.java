package com.djc.rentbook.mutation;

import com.djc.rentbook.config.AuditProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MutationMaintenanceJobTest {

    @Test
    void cleanupExpiredAuditLogsUsesConfiguredRetentionAndDeletesDetailsFirst() {
        MutationMapper mapper = mock(MutationMapper.class);
        AuditProperties properties = new AuditProperties();
        properties.setRetentionYears(3);
        properties.setZone("Asia/Shanghai");
        Clock clock = Clock.fixed(Instant.parse("2026-07-31T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
        OffsetDateTime expectedCutoff = OffsetDateTime.now(clock).minusYears(3);
        when(mapper.deleteExpiredDataChangeLogs(any())).thenReturn(8);
        when(mapper.deleteExpiredOperationLogs(any())).thenReturn(3);

        MutationMaintenanceJob job = new MutationMaintenanceJob(mapper, properties, clock);
        job.cleanupExpiredAuditLogs();

        InOrder inOrder = inOrder(mapper);
        inOrder.verify(mapper).deleteExpiredDataChangeLogs(expectedCutoff);
        inOrder.verify(mapper).deleteExpiredOperationLogs(expectedCutoff);
    }

    @Test
    void cleanupExpiredAuditLogsKeepsRecordsInsideRetentionWindow() {
        MutationMapper mapper = mock(MutationMapper.class);
        AuditProperties properties = new AuditProperties();
        properties.setRetentionYears(5);
        properties.setZone("Asia/Shanghai");
        Clock clock = Clock.fixed(Instant.parse("2026-07-31T01:00:00Z"), ZoneId.of("Asia/Shanghai"));

        MutationMaintenanceJob job = new MutationMaintenanceJob(mapper, properties, clock);
        job.cleanupExpiredAuditLogs();

        OffsetDateTime expectedCutoff = OffsetDateTime.now(clock).minusYears(5);
        verify(mapper).deleteExpiredDataChangeLogs(expectedCutoff);
        verify(mapper).deleteExpiredOperationLogs(expectedCutoff);
        assertThat(expectedCutoff.getYear()).isEqualTo(2021);
    }
}
