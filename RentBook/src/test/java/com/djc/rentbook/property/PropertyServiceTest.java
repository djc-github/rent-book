package com.djc.rentbook.property;

import com.djc.rentbook.payment.PaymentMapper;
import com.djc.rentbook.payment.PaymentRecord;
import com.djc.rentbook.roomimage.RoomImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void collectRejectsAnOverlappingRentPeriod() {
        LocalDate dueDate = LocalDate.now();
        RoomRecord room = rentedRoom(dueDate);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findOverlappingPaymentId(
                8L, dueDate, dueDate.plusMonths(1).minusDays(1), null
        )).thenReturn(99L);

        assertThatThrownBy(() -> service.collectRoomRent(
                8L, new PropertyDtos.RoomCollectRentRequest(1, LocalDate.now(), null, null, null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经登记过收租");

        verify(paymentMapper, never()).createRoomPayment(any());
        verify(propertyMapper, never()).moveRoomDueDate(any(), any(), any());
    }

    @Test
    void collectUsesLockedDueDateAndMovesExactlyOneCycle() {
        LocalDate dueDate = LocalDate.now();
        RoomRecord room = rentedRoom(dueDate);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findOverlappingPaymentId(
                8L, dueDate, dueDate.plusMonths(1).minusDays(1), null
        )).thenReturn(null);

        Long paymentId = service.collectRoomRent(
                8L, new PropertyDtos.RoomCollectRentRequest(1, LocalDate.now(), null, "WECHAT", null)
        );

        assertThat(paymentId).isNull();
        ArgumentCaptor<PaymentRecord> payment = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentMapper).createRoomPayment(payment.capture());
        assertThat(payment.getValue().getPeriodStart()).isEqualTo(dueDate);
        assertThat(payment.getValue().getPeriodEnd()).isEqualTo(dueDate.plusMonths(1).minusDays(1));
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("1000.00");
        verify(propertyMapper).moveRoomDueDate(8L, dueDate.plusMonths(1), LocalDate.now());
    }

    @Test
    void collectUsesConfiguredThreeMonthCycle() {
        LocalDate dueDate = LocalDate.now();
        RoomRecord room = rentedRoom(dueDate);
        room.setPayCycleMonths(3);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findOverlappingPaymentId(
                8L, dueDate, dueDate.plusMonths(3).minusDays(1), null
        )).thenReturn(null);

        service.collectRoomRent(
                8L, new PropertyDtos.RoomCollectRentRequest(3, LocalDate.now(), null, "WECHAT", null)
        );

        ArgumentCaptor<PaymentRecord> payment = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentMapper).createRoomPayment(payment.capture());
        assertThat(payment.getValue().getPeriodStart()).isEqualTo(dueDate);
        assertThat(payment.getValue().getPeriodEnd()).isEqualTo(dueDate.plusMonths(3).minusDays(1));
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("3000.00");
        verify(propertyMapper).moveRoomDueDate(8L, dueDate.plusMonths(3), LocalDate.now());
    }

    @Test
    void collectRejectsStaleCycleFromAnotherPage() {
        LocalDate dueDate = LocalDate.now();
        RoomRecord room = rentedRoom(dueDate);
        room.setPayCycleMonths(3);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);

        assertThatThrownBy(() -> service.collectRoomRent(
                8L, new PropertyDtos.RoomCollectRentRequest(1, LocalDate.now(), null, "WECHAT", null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("收租周期已经变化");

        verify(paymentMapper, never()).createRoomPayment(any());
        verify(propertyMapper, never()).moveRoomDueDate(any(), any(), any());
    }

    @Test
    void rentSettingsRejectCycleLongerThanRemainingLease() {
        RoomRecord room = new RoomRecord();
        room.setId(8L);
        room.setStatus("RENTED");
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        LocalDate nextDueDate = LocalDate.now();
        PropertyDtos.RoomRentRequest request = new PropertyDtos.RoomRentRequest(
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                3,
                nextDueDate.minusMonths(9),
                nextDueDate.plusMonths(2).minusDays(1),
                nextDueDate,
                null
        );

        assertThatThrownBy(() -> service.startRoomRent(8L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本次收租周期不能超过租期结束日期");

        verify(propertyMapper, never()).startRoomRent(any(), any());
    }

    @Test
    void rentSettingsCannotRewindIntoAlreadyCollectedPeriod() {
        RoomRecord room = new RoomRecord();
        room.setId(8L);
        room.setStatus("RENTED");
        LocalDate lastCovered = LocalDate.now().plusMonths(1);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findLatestCoveredDate(8L)).thenReturn(lastCovered);
        PropertyDtos.RoomRentRequest request = new PropertyDtos.RoomRentRequest(
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                1,
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                lastCovered,
                null
        );

        assertThatThrownBy(() -> service.startRoomRent(8L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经收过租");

        verify(propertyMapper, never()).startRoomRent(any(), any());
    }

    @Test
    void rentSettingsCannotChangeDueDateAfterPaymentHistoryExists() {
        LocalDate currentNextDueDate = LocalDate.now().plusMonths(2);
        RoomRecord room = rentedRoom(currentNextDueDate);
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findLatestCoveredDate(8L)).thenReturn(currentNextDueDate.minusDays(1));
        PropertyDtos.RoomRentRequest request = new PropertyDtos.RoomRentRequest(
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                1,
                LocalDate.now().minusMonths(3),
                LocalDate.now().plusYears(1),
                currentNextDueDate.plusDays(1),
                null
        );

        assertThatThrownBy(() -> service.startRoomRent(8L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调整应收日");

        verify(propertyMapper, never()).startRoomRent(any(), any());
    }

    @Test
    void adjustNextDueDateChangesOnlyFutureSchedule() {
        LocalDate currentNextDueDate = LocalDate.now().plusMonths(2);
        LocalDate latestCoveredDate = currentNextDueDate.minusDays(1);
        RoomRecord room = rentedRoom(currentNextDueDate);
        room.setLeaseStartDate(LocalDate.now().minusMonths(3));
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findLatestCoveredDate(8L)).thenReturn(latestCoveredDate);
        when(propertyMapper.adjustRoomNextDueDate(
                8L, currentNextDueDate, currentNextDueDate.plusDays(7)
        )).thenReturn(1);

        service.adjustRoomNextDueDate(
                8L,
                new PropertyDtos.RoomNextDueDateRequest(
                        currentNextDueDate,
                        currentNextDueDate.plusDays(7),
                        "RENT_FREE_PERIOD",
                        "双方约定免租一周"
                )
        );

        verify(propertyMapper).adjustRoomNextDueDate(
                8L, currentNextDueDate, currentNextDueDate.plusDays(7)
        );
        verify(paymentMapper, never()).createRoomPayment(any());
    }

    @Test
    void adjustNextDueDateRejectsPaidPeriodAndStalePage() {
        LocalDate currentNextDueDate = LocalDate.now().plusMonths(2);
        RoomRecord room = rentedRoom(currentNextDueDate);
        room.setLeaseStartDate(LocalDate.now().minusMonths(3));
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);
        when(paymentMapper.findLatestCoveredDate(8L)).thenReturn(currentNextDueDate.minusDays(1));

        assertThatThrownBy(() -> service.adjustRoomNextDueDate(
                8L,
                new PropertyDtos.RoomNextDueDateRequest(
                        currentNextDueDate.minusDays(1),
                        currentNextDueDate.plusDays(1),
                        "ENTRY_ERROR",
                        null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经变化");

        assertThatThrownBy(() -> service.adjustRoomNextDueDate(
                8L,
                new PropertyDtos.RoomNextDueDateRequest(
                        currentNextDueDate,
                        currentNextDueDate.minusDays(1),
                        "ENTRY_ERROR",
                        null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已收租期");

        verify(propertyMapper, never()).adjustRoomNextDueDate(any(), any(), any());
    }

    @Test
    void adjustNextDueDateRequiresNotesForOtherReason() {
        LocalDate currentNextDueDate = LocalDate.now().plusMonths(2);
        RoomRecord room = rentedRoom(currentNextDueDate);
        room.setLeaseStartDate(LocalDate.now().minusMonths(3));
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);

        assertThatThrownBy(() -> service.adjustRoomNextDueDate(
                8L,
                new PropertyDtos.RoomNextDueDateRequest(
                        currentNextDueDate,
                        currentNextDueDate.plusDays(1),
                        "OTHER",
                        " "
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("填写简短说明");

        verify(propertyMapper, never()).adjustRoomNextDueDate(any(), any(), any());
    }

    @Test
    void vacantRoomCannotBeMarkedRentedWithoutRentSettings() {
        RoomRecord room = new RoomRecord();
        room.setId(8L);
        room.setStatus("VACANT");
        when(propertyMapper.findRoomRecordForUpdate(8L)).thenReturn(room);

        assertThatThrownBy(() -> service.updateRoomStatus(8L, "RENTED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出租/收租设置");

        verify(propertyMapper, never()).updateRoomStatus(8L, "RENTED");
    }

    private RoomRecord rentedRoom(LocalDate dueDate) {
        RoomRecord room = new RoomRecord();
        room.setId(8L);
        room.setStatus("RENTED");
        room.setRentAmount(new BigDecimal("1000.00"));
        room.setPayCycleMonths(1);
        room.setNextDueDate(dueDate);
        room.setLeaseEndDate(dueDate.plusYears(1));
        return room;
    }
}
