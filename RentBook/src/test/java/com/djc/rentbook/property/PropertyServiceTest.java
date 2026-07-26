package com.djc.rentbook.property;

import com.djc.rentbook.payment.PaymentMapper;
import com.djc.rentbook.payment.PaymentRecord;
import com.djc.rentbook.roomimage.RoomImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropertyServiceTest {
    private PropertyMapper propertyMapper;
    private PaymentMapper paymentMapper;
    private PropertyService service;

    @BeforeEach
    void setUp() {
        propertyMapper = mock(PropertyMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        service = new PropertyService(
                propertyMapper,
                paymentMapper,
                mock(RoomImageService.class),
                7
        );
    }

    @Test
    void collectKeepsPlannedCollectionDateSeparateFromCoveredPeriod() {
        LocalDate paidDate = LocalDate.now();
        LocalDate dueDate = paidDate.minusDays(1);
        LocalDate periodStart = dueDate.withDayOfMonth(1);
        RoomRecord room = rentedRoom(dueDate, periodStart);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findLatestCoveredDate(8L, 80L)).thenReturn(periodStart.minusDays(1));
        when(paymentMapper.findOverlappingPaymentId(
                8L, 80L, periodStart, periodStart.plusMonths(1).minusDays(1), null
        )).thenReturn(null);
        when(propertyMapper.moveRoomSchedule(any(), any(), any(), any(), any())).thenReturn(1);
        when(propertyMapper.moveRentalSchedule(any(), any(), any(), any())).thenReturn(1);

        service.collectRoomRent(
                8L, new PropertyDtos.RoomCollectRentRequest(1, paidDate, null, "WECHAT", null)
        );

        ArgumentCaptor<PaymentRecord> payment = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentMapper).createRoomPayment(payment.capture());
        assertThat(payment.getValue().getRentalId()).isEqualTo(80L);
        assertThat(payment.getValue().getDueDate()).isEqualTo(dueDate);
        assertThat(payment.getValue().getPaidDate()).isEqualTo(paidDate);
        assertThat(payment.getValue().getPeriodStart()).isEqualTo(periodStart);
        assertThat(payment.getValue().getPeriodEnd()).isEqualTo(periodStart.plusMonths(1).minusDays(1));
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("1000.00");
        verify(propertyMapper).moveRoomSchedule(
                8L, 80L, dueDate.plusMonths(1), periodStart.plusMonths(1), paidDate
        );
        verify(propertyMapper).moveRentalSchedule(
                8L, 80L, dueDate.plusMonths(1), periodStart.plusMonths(1)
        );
    }

    @Test
    void collectUsesChangedThreeMonthCycleWithoutMovingCoverageStart() {
        LocalDate dueDate = LocalDate.now();
        LocalDate periodStart = dueDate.withDayOfMonth(1);
        RoomRecord room = rentedRoom(dueDate, periodStart);
        room.setPayCycleMonths(3);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findOverlappingPaymentId(
                8L, 80L, periodStart, periodStart.plusMonths(3).minusDays(1), null
        )).thenReturn(null);
        when(propertyMapper.moveRoomSchedule(any(), any(), any(), any(), any())).thenReturn(1);
        when(propertyMapper.moveRentalSchedule(any(), any(), any(), any())).thenReturn(1);

        service.collectRoomRent(
                8L, new PropertyDtos.RoomCollectRentRequest(3, LocalDate.now(), null, "WECHAT", null)
        );

        ArgumentCaptor<PaymentRecord> payment = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentMapper).createRoomPayment(payment.capture());
        assertThat(payment.getValue().getCycleMonths()).isEqualTo(3);
        assertThat(payment.getValue().getPeriodStart()).isEqualTo(periodStart);
        assertThat(payment.getValue().getPeriodEnd()).isEqualTo(periodStart.plusMonths(3).minusDays(1));
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("3000.00");
        verify(propertyMapper).moveRoomSchedule(
                8L, 80L, dueDate.plusMonths(3), periodStart.plusMonths(3), LocalDate.now()
        );
    }

    @Test
    void collectRejectsAnOverlapInsideCurrentRental() {
        LocalDate dueDate = LocalDate.now();
        LocalDate periodStart = dueDate.withDayOfMonth(1);
        RoomRecord room = rentedRoom(dueDate, periodStart);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findOverlappingPaymentId(
                8L, 80L, periodStart, periodStart.plusMonths(1).minusDays(1), null
        )).thenReturn(99L);

        assertThatThrownBy(() -> service.collectRoomRent(
                8L, new PropertyDtos.RoomCollectRentRequest(1, LocalDate.now(), null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经登记");

        verify(paymentMapper, never()).createRoomPayment(any());
        verify(propertyMapper, never()).moveRoomSchedule(any(), any(), any(), any(), any());
    }

    @Test
    void newRentalCreatesAnIndependentCycleWithoutReadingOldRoomPayments() {
        RoomRecord room = new RoomRecord();
        room.setId(8L);
        room.setStatus("VACANT");
        room.setRentAmount(new BigDecimal("1000.00"));
        room.setDepositAmount(new BigDecimal("1000.00"));
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        doAnswer(invocation -> {
            RoomRentalRecord rental = invocation.getArgument(0);
            rental.setId(81L);
            return null;
        }).when(propertyMapper).createRoomRental(any());
        when(propertyMapper.startNewRoomRent(any(), any(), any(), any(), any())).thenReturn(1);
        LocalDate leaseStart = LocalDate.now().minusDays(1);
        PropertyDtos.RoomRentRequest request = rentRequest(leaseStart, leaseStart.plusYears(1).minusDays(1), leaseStart, 1);

        service.startRoomRent(8L, request);

        verify(paymentMapper, never()).findLatestCoveredDate(any(), any());
        verify(propertyMapper).startNewRoomRent(8L, 81L, leaseStart, leaseStart.getDayOfMonth(), request);
    }

    @Test
    void currentRentalChangesCycleButRequiresDedicatedActionForCollectionDate() {
        LocalDate currentDueDate = LocalDate.now().plusMonths(1);
        RoomRecord room = rentedRoom(currentDueDate, LocalDate.now().withDayOfMonth(1));
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findLatestCoveredDate(8L, 80L)).thenReturn(room.getNextPeriodStartDate().minusDays(1));
        PropertyDtos.RoomRentRequest request = rentRequest(
                LocalDate.now().minusMonths(3),
                LocalDate.now().plusYears(1),
                currentDueDate.plusDays(1),
                3
        );

        assertThatThrownBy(() -> service.startRoomRent(8L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调整收租日");

        verify(propertyMapper, never()).updateCurrentRoomRent(any(), any(), any(), any());
    }

    @Test
    void adjustCollectionDateMayBeEarlierThanLatestCoveredDate() {
        LocalDate currentDueDate = LocalDate.now().plusMonths(1);
        LocalDate adjustedDueDate = currentDueDate.minusDays(5);
        RoomRecord room = rentedRoom(currentDueDate, LocalDate.now().plusMonths(1).withDayOfMonth(1));
        room.setLeaseStartDate(LocalDate.now().minusMonths(3));
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(propertyMapper.adjustRoomNextDueDate(8L, currentDueDate, adjustedDueDate)).thenReturn(1);
        when(propertyMapper.adjustRentalCollectionDate(8L, 80L, adjustedDueDate)).thenReturn(1);

        service.adjustRoomNextDueDate(
                8L,
                new PropertyDtos.RoomNextDueDateRequest(
                        currentDueDate, adjustedDueDate, "SCHEDULE_CHANGE", "改为每月提前收"
                )
        );

        verify(propertyMapper).adjustRoomNextDueDate(8L, currentDueDate, adjustedDueDate);
        verify(propertyMapper).adjustRentalCollectionDate(8L, 80L, adjustedDueDate);
        verify(paymentMapper, never()).createRoomPayment(any());
    }

    @Test
    void settlementCalculatesRefundAndEndsOnlyCurrentRental() {
        LocalDate today = LocalDate.now();
        RoomRecord room = rentedRoom(today, today.withDayOfMonth(1));
        room.setLeaseStartDate(today.minusMonths(2));
        room.setDepositAmount(new BigDecimal("1000.00"));
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(propertyMapper.listRefundablePayments(80L, today)).thenReturn(List.of(
                Map.of(
                        "period_start", today.withDayOfMonth(1),
                        "period_end", today.withDayOfMonth(1).plusMonths(1).minusDays(1),
                        "amount", new BigDecimal("1000.00")
                )
        ));
        when(propertyMapper.endRental(8L, 80L, today)).thenReturn(1);
        when(propertyMapper.settleRoomToVacant(8L, 80L)).thenReturn(1);
        PropertyDtos.RoomSettlementRequest request = new PropertyDtos.RoomSettlementRequest(
                today, today, "EARLY_TERMINATION",
                new BigDecimal("100.00"), new BigDecimal("200.00"), "扣除清洁费"
        );

        service.settleRoomRent(8L, request);

        ArgumentCaptor<RentSettlementRecord> settlement = ArgumentCaptor.forClass(RentSettlementRecord.class);
        verify(propertyMapper).createSettlement(settlement.capture());
        assertThat(settlement.getValue().getDepositRefundAmount()).isEqualByComparingTo("800.00");
        assertThat(settlement.getValue().getTotalRefundAmount()).isEqualByComparingTo("900.00");
        verify(propertyMapper).endRental(8L, 80L, today);
        verify(propertyMapper).settleRoomToVacant(8L, 80L);
    }

    @Test
    void rentedRoomCannotBypassSettlementAndBecomeVacant() {
        RoomRecord room = rentedRoom(LocalDate.now(), LocalDate.now());
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);

        assertThatThrownBy(() -> service.updateRoomStatus(8L, "VACANT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("退租结算");

        verify(propertyMapper, never()).updateRoomStatus(8L, "VACANT");
    }

    private PropertyDtos.RoomRentRequest rentRequest(
            LocalDate leaseStart, LocalDate leaseEnd, LocalDate nextDueDate, int months
    ) {
        return new PropertyDtos.RoomRentRequest(
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                months,
                leaseStart,
                leaseEnd,
                nextDueDate,
                null
        );
    }

    private RoomRecord rentedRoom(LocalDate dueDate, LocalDate nextPeriodStartDate) {
        RoomRecord room = new RoomRecord();
        room.setId(8L);
        room.setStatus("RENTED");
        room.setRentAmount(new BigDecimal("1000.00"));
        room.setDepositAmount(new BigDecimal("1000.00"));
        room.setPayCycleMonths(1);
        room.setNextDueDate(dueDate);
        room.setCollectionDay(dueDate.getDayOfMonth());
        room.setNextPeriodStartDate(nextPeriodStartDate);
        room.setCurrentRentalId(80L);
        room.setLeaseStartDate(nextPeriodStartDate.minusMonths(2));
        room.setLeaseEndDate(nextPeriodStartDate.plusYears(1));
        return room;
    }
}
