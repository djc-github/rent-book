package com.djc.rentbook.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {
    private PaymentMapper mapper;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PaymentMapper.class);
        service = new PaymentService(mapper);
    }

    @Test
    void deleteRejectsAnOlderPaymentWhenLaterCoverageExists() {
        PaymentRecord payment = roomRentalPayment();
        when(mapper.find(12L)).thenReturn(payment);
        when(mapper.lockRoom(8L)).thenReturn(8L);
        when(mapper.findForUpdate(12L)).thenReturn(payment);
        when(mapper.hasLaterRentalPayment(80L, 12L, payment.getPeriodEnd())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(12L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("从最新一笔开始");

        verify(mapper, never()).delete(12L);
        verify(mapper, never()).rollbackCurrentRentalSchedule(payment);
    }

    @Test
    void deleteLatestPaymentRollsBackOnlyItsRentalSchedule() {
        PaymentRecord payment = roomRentalPayment();
        when(mapper.find(12L)).thenReturn(payment);
        when(mapper.lockRoom(8L)).thenReturn(8L);
        when(mapper.findForUpdate(12L)).thenReturn(payment);
        when(mapper.hasLaterRentalPayment(80L, 12L, payment.getPeriodEnd())).thenReturn(false);
        when(mapper.delete(12L)).thenReturn(1);

        service.delete(12L);

        verify(mapper).rollbackCurrentRentalSchedule(payment);
        verify(mapper).syncRentalScheduleFromRoom(payment);
        verify(mapper, never()).rollbackRoomNextDueDate(payment);
    }

    @Test
    void listReturnsFilteredRowsAndAccurateSummary() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        BigDecimal minAmount = new BigDecimal("500");
        BigDecimal maxAmount = new BigDecimal("3000");
        Map<String, Object> row = Map.of(
                "id", 12L,
                "paid_date", LocalDate.of(2026, 7, 28),
                "created_at", OffsetDateTime.parse("2026-07-28T14:26:00+08:00")
        );
        when(mapper.listPage(from, to, 6L, minAmount, maxAmount, null, null, null, 21))
                .thenReturn(List.of(row));
        when(mapper.summarize(from, to, 6L, minAmount, maxAmount))
                .thenReturn(Map.of("total_count", 128L, "total_amount", new BigDecimal("16480.00")));

        PaymentDtos.PaymentPage page = service.list(from, to, 6L, minAmount, maxAmount, null, 20);

        assertThat(page.rows()).containsExactly(row);
        assertThat(page.totalCount()).isEqualTo(128L);
        assertThat(page.totalAmount()).isEqualByComparingTo("16480.00");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void listRejectsAnInvalidDateOrAmountRange() {
        assertThatThrownBy(() -> service.list(
                LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1), null, null, null, null, 20
        )).hasMessageContaining("开始日期");

        assertThatThrownBy(() -> service.list(
                null, null, null, new BigDecimal("2000"), new BigDecimal("1000"), null, 20
        )).hasMessageContaining("最低金额");
    }

    private PaymentRecord roomRentalPayment() {
        PaymentRecord payment = new PaymentRecord();
        payment.setId(12L);
        payment.setRoomId(8L);
        payment.setRentalId(80L);
        payment.setDueDate(LocalDate.of(2026, 7, 25));
        payment.setPeriodStart(LocalDate.of(2026, 7, 1));
        payment.setPeriodEnd(LocalDate.of(2026, 7, 31));
        payment.setPaidDate(LocalDate.of(2026, 7, 26));
        return payment;
    }
}
