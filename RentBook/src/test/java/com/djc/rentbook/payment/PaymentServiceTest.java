package com.djc.rentbook.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

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
