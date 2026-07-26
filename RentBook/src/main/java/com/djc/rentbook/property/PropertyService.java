package com.djc.rentbook.property;

import com.djc.rentbook.payment.PaymentMapper;
import com.djc.rentbook.payment.PaymentRecord;
import com.djc.rentbook.roomimage.RoomImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PropertyService {
    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);
    private static final List<String> ROOM_STATUSES = List.of("VACANT", "RESERVED", "RENTED", "MAINTENANCE", "OFFLINE");
    private static final Set<String> DUE_DATE_ADJUSTMENT_REASONS =
            Set.of("ENTRY_ERROR", "RENT_FREE_PERIOD", "SCHEDULE_CHANGE", "OTHER");
    private static final Set<String> SETTLEMENT_REASONS =
            Set.of("EARLY_TERMINATION", "NORMAL_END", "OTHER");
    private final PropertyMapper mapper;
    private final PaymentMapper paymentMapper;
    private final RoomImageService roomImageService;
    private final int rentCollectAdvanceDays;

    public PropertyService(PropertyMapper mapper,
                           PaymentMapper paymentMapper,
                           RoomImageService roomImageService,
                           @Value("${rentbook.rent-collect.advance-days:7}") int rentCollectAdvanceDays) {
        this.mapper = mapper;
        this.paymentMapper = paymentMapper;
        this.roomImageService = roomImageService;
        this.rentCollectAdvanceDays = Math.max(0, rentCollectAdvanceDays);
    }

    public List<Map<String, Object>> list(String keyword) {
        return mapper.listProperties(blankToNull(keyword));
    }

    public PropertyDtos.PropertyDetail detail(Long id) {
        Map<String, Object> property = mapper.findProperty(id);
        if (property == null) {
            throw new IllegalArgumentException("房源不存在");
        }
        return new PropertyDtos.PropertyDetail(property, mapper.listRooms(id));
    }

    public List<Map<String, Object>> listRooms(String status) {
        String normalized = blankToNull(status);
        if (normalized != null) {
            assertRoomStatus(normalized);
        }
        return mapper.listAllRooms(normalized);
    }

    @Transactional
    public Long create(PropertyDtos.PropertyCreateRequest request) {
        PropertyRecord record = new PropertyRecord();
        record.setName(propertyName(request));
        record.setAddress(request.address());
        record.setDistrict(request.district());
        record.setLandlordName(request.landlordName());
        record.setLandlordPhone(request.landlordPhone());
        record.setManager(request.manager());
        record.setNotes(request.notes());
        mapper.createProperty(record);
        log.info("Created property id={}, address={}", record.getId(), record.getAddress());
        return record.getId();
    }

    @Transactional
    public void update(Long id, PropertyDtos.PropertyCreateRequest request) {
        PropertyRecord record = new PropertyRecord();
        record.setId(id);
        record.setName(propertyName(request));
        record.setAddress(request.address());
        record.setDistrict(request.district());
        record.setLandlordName(request.landlordName());
        record.setLandlordPhone(request.landlordPhone());
        record.setManager(request.manager());
        record.setNotes(request.notes());
        if (mapper.updateProperty(record) == 0) {
            throw new IllegalArgumentException("房源不存在");
        }
        log.info("Updated property id={}, address={}", id, request.address());
    }

    @Transactional
    public Long createRoom(PropertyDtos.RoomCreateRequest request) {
        if (request.status() != null && !request.status().isBlank() && !"VACANT".equals(request.status())) {
            throw new IllegalArgumentException("新增房间默认空置，请新增后再使用房态或出租操作");
        }
        RoomRecord record = new RoomRecord();
        record.setPropertyId(request.propertyId());
        record.setRoomNo(request.roomNo());
        record.setFloor(request.floor());
        record.setArea(request.area());
        record.setRentAmount(request.rentAmount());
        record.setDepositAmount(request.depositAmount());
        record.setStatus("VACANT");
        record.setPayCycleMonths(1);
        record.setNextDueDate(null);
        record.setOrientation(request.orientation());
        record.setTags(request.tags());
        record.setNotes(request.notes());
        assertRoomStatus(record.getStatus());
        mapper.createRoom(record);
        log.info("Created room id={}, propertyId={}, roomNo={}, status={}, rentAmount={}, depositAmount={}, payCycleMonths={}, nextDueDate={}",
                record.getId(), record.getPropertyId(), record.getRoomNo(), record.getStatus(), record.getRentAmount(),
                record.getDepositAmount(), record.getPayCycleMonths(), record.getNextDueDate());
        return record.getId();
    }

    @Transactional
    public void updateRoom(Long roomId, PropertyDtos.RoomCreateRequest request) {
        RoomRecord existing = mapper.findRoomRecordForUpdate(roomId);
        if (existing == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        RoomRecord record = buildRoomRecord(request);
        record.setId(roomId);
        if (mapper.updateRoom(record) == 0) {
            throw new IllegalArgumentException("房间不存在");
        }
        log.info("Updated room id={}, propertyId={}, roomNo={}, status={}, rentAmount={}, depositAmount={}",
                roomId, record.getPropertyId(), record.getRoomNo(), existing.getStatus(),
                record.getRentAmount(), record.getDepositAmount());
    }

    @Transactional
    public void updateRoomStatus(Long roomId, String status) {
        assertRoomStatus(status);
        RoomRecord room = mapper.findRoomRecordForUpdate(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        if ("RENTED".equals(status) && !"RENTED".equals(room.getStatus())) {
            throw new IllegalArgumentException("出租房间请使用“出租/收租设置”，不能直接修改为已出租");
        }
        if ("RENTED".equals(room.getStatus()) && !"RENTED".equals(status)) {
            throw new IllegalArgumentException("已出租房间请使用“退租结算”，系统会保留本轮收租记录并自动设为空置");
        }
        if (mapper.updateRoomStatus(roomId, status) == 0) {
            throw new IllegalArgumentException("房间不存在");
        }
        log.info("Updated room status roomId={}, status={}", roomId, status);
    }

    @Transactional
    public int expireEndedRoomLeases(LocalDate today) {
        LocalDate effectiveDate = today == null ? LocalDate.now() : today;
        mapper.expireEndedRentals(effectiveDate);
        int count = mapper.expireEndedRoomLeases(effectiveDate);
        if (count > 0) {
            log.info("Expired ended room leases count={}, today={}", count, effectiveDate);
        } else {
            log.debug("No ended room leases to expire today={}", effectiveDate);
        }
        return count;
    }

    @Transactional
    public void startRoomRent(Long roomId, PropertyDtos.RoomRentRequest request) {
        RoomRecord room = mapper.findRoomRecordForUpdate(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        int cycleMonths = normalizeCycle(request.payCycleMonths());
        validateRentDates(request.leaseStartDate(), request.leaseEndDate(), request.nextDueDate());

        boolean updatingCurrentRental = "RENTED".equals(room.getStatus());
        Long rentalId = room.getCurrentRentalId();
        if (updatingCurrentRental && rentalId == null) {
            throw new IllegalStateException("这间房的出租信息不完整，请暂停操作并联系维护人员");
        }
        LocalDate latestCoveredDate = updatingCurrentRental
                ? paymentMapper.findLatestCoveredDate(roomId, rentalId)
                : null;
        if (latestCoveredDate != null
                && room.getNextDueDate() != null
                && !Objects.equals(request.nextDueDate(), room.getNextDueDate())) {
            throw new IllegalArgumentException("已有收租记录，请使用“调整收租日”修改下次收租日");
        }

        LocalDate nextPeriodStartDate = updatingCurrentRental
                ? firstNonNull(
                        room.getNextPeriodStartDate(),
                        latestCoveredDate == null ? null : latestCoveredDate.plusDays(1),
                        request.leaseStartDate()
                )
                : request.leaseStartDate();
        validateCoverageFits(nextPeriodStartDate, request.leaseEndDate(), cycleMonths);
        int collectionDay = request.nextDueDate().getDayOfMonth();

        if (updatingCurrentRental) {
            int roomUpdated = mapper.updateCurrentRoomRent(roomId, rentalId, collectionDay, request);
            int rentalUpdated = mapper.updateCurrentRental(roomId, rentalId, collectionDay, request);
            if (roomUpdated == 0 || rentalUpdated == 0) {
                throw new IllegalArgumentException("出租设置已变化，请刷新页面后重试");
            }
        } else {
            RoomRentalRecord rental = new RoomRentalRecord();
            rental.setRoomId(roomId);
            rental.setLeaseStartDate(request.leaseStartDate());
            rental.setLeaseEndDate(request.leaseEndDate());
            rental.setRentAmount(request.rentAmount());
            rental.setDepositAmount(request.depositAmount());
            rental.setPayCycleMonths(cycleMonths);
            rental.setNextCollectionDate(request.nextDueDate());
            rental.setCollectionDay(collectionDay);
            rental.setNextPeriodStartDate(nextPeriodStartDate);
            rental.setNotes(blankToNull(request.notes()));
            mapper.createRoomRental(rental);
            if (mapper.startNewRoomRent(
                    roomId, rental.getId(), nextPeriodStartDate, collectionDay, request
            ) == 0) {
                throw new IllegalArgumentException("房间不存在");
            }
            rentalId = rental.getId();
        }
        log.info(
                "Saved room rent roomId={}, rentalId={}, operation={}, rentAmount={}, depositAmount={}, payCycleMonths={}, leaseStartDate={}, leaseEndDate={}, nextCollectionDate={}, nextPeriodStartDate={}",
                roomId, rentalId, updatingCurrentRental ? "UPDATE" : "START", request.rentAmount(),
                request.depositAmount(), cycleMonths, request.leaseStartDate(), request.leaseEndDate(),
                request.nextDueDate(), nextPeriodStartDate
        );
    }

    @Transactional
    public void adjustRoomNextDueDate(Long roomId, PropertyDtos.RoomNextDueDateRequest request) {
        RoomRecord room = mapper.findRoomRecordForUpdate(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        if (!"RENTED".equals(room.getStatus())) {
            throw new IllegalArgumentException("只有已出租房间才能调整下次收租日");
        }
        if (room.getNextDueDate() == null) {
            throw new IllegalArgumentException("当前没有可调整的下次收租日，请先完成出租设置");
        }
        if (room.getCurrentRentalId() == null) {
            throw new IllegalStateException("这间房的出租信息不完整，请暂停操作并联系维护人员");
        }
        if (!Objects.equals(room.getNextDueDate(), request.expectedNextDueDate())) {
            throw new IllegalArgumentException("下次收租日已经变化，请刷新页面后重新调整");
        }
        if (Objects.equals(room.getNextDueDate(), request.nextDueDate())) {
            throw new IllegalArgumentException("新的收租日与当前日期相同，无需调整");
        }
        if (!DUE_DATE_ADJUSTMENT_REASONS.contains(request.reason())) {
            throw new IllegalArgumentException("请选择正确的调整原因");
        }
        if ("OTHER".equals(request.reason()) && blankToNull(request.notes()) == null) {
            throw new IllegalArgumentException("选择其他原因时，请填写简短说明");
        }

        validateCollectionDate(room.getLeaseStartDate(), room.getLeaseEndDate(), request.nextDueDate());
        if (mapper.adjustRoomNextDueDate(
                roomId, request.expectedNextDueDate(), request.nextDueDate()
        ) == 0) {
            throw new IllegalArgumentException("下次收租日已经变化，请刷新页面后重新调整");
        }
        if (mapper.adjustRentalCollectionDate(
                roomId, room.getCurrentRentalId(), request.nextDueDate()
        ) == 0) {
            throw new IllegalArgumentException("这间房的信息已经变化，请刷新后重新调整");
        }
        log.info(
                "Adjusted room collection date roomId={}, rentalId={}, oldCollectionDate={}, newCollectionDate={}, nextPeriodStartDate={}, reason={}, notes={}",
                roomId, room.getCurrentRentalId(), room.getNextDueDate(), request.nextDueDate(),
                room.getNextPeriodStartDate(),
                request.reason(), blankToNull(request.notes())
        );
    }

    @Transactional
    public Long collectRoomRent(Long roomId, PropertyDtos.RoomCollectRentRequest request) {
        RoomRecord room = mapper.findRoomRecordForUpdate(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        if (!"RENTED".equals(room.getStatus())) {
            throw new IllegalArgumentException("只有已出租的房间才能收租");
        }
        if (room.getCurrentRentalId() == null) {
            throw new IllegalStateException("这间房的出租信息不完整，请暂停操作并联系维护人员");
        }
        LocalDate today = LocalDate.now();
        if (room.getNextDueDate() == null) {
            throw new IllegalArgumentException("请先设置下次收租日");
        }
        if (room.getNextDueDate().isAfter(today.plusDays(rentCollectAdvanceDays))) {
            throw new IllegalArgumentException("还没到可收租时间，请在计划收租日前" + rentCollectAdvanceDays + "天内再收");
        }
        LocalDate latestCoveredDate = paymentMapper.findLatestCoveredDate(roomId, room.getCurrentRentalId());
        LocalDate periodStart = firstNonNull(
                room.getNextPeriodStartDate(),
                latestCoveredDate == null ? null : latestCoveredDate.plusDays(1),
                room.getLeaseStartDate()
        );
        if (periodStart == null) {
            throw new IllegalArgumentException("下次租金的开始日期不完整，请暂停操作并联系维护人员");
        }
        int months = normalizeCycle(room.getPayCycleMonths());
        if (request.months() != null && request.months() != months) {
            throw new IllegalArgumentException("收租周期已经变化，请刷新页面后重新确认");
        }
        LocalDate periodEnd = periodStart.plusMonths(months).minusDays(1);
        if (room.getLeaseEndDate() != null && periodStart.isAfter(room.getLeaseEndDate())) {
            throw new IllegalArgumentException("租期已结束，不能继续收租");
        }
        if (room.getLeaseEndDate() != null && periodEnd.isAfter(room.getLeaseEndDate())) {
            throw new IllegalArgumentException("本次会收到租期结束日之后，请先调整租期或收租间隔");
        }
        Long overlappingId = paymentMapper.findOverlappingPaymentId(
                roomId, room.getCurrentRentalId(), periodStart, periodEnd, null
        );
        if (overlappingId != null) {
            throw new IllegalArgumentException(
                    "这次出租期间，" + periodStart + "至" + periodEnd + "的租金已经登记，请核对收租记录"
            );
        }
        LocalDate paidDate = request.paidDate() == null ? LocalDate.now() : request.paidDate();
        BigDecimal expectedAmount = room.getRentAmount().multiply(BigDecimal.valueOf(months));
        if (request.amount() != null && request.amount().compareTo(expectedAmount) != 0) {
            throw new IllegalArgumentException("收租金额与房间租金和收租月数不一致，请先修改收租设置");
        }
        BigDecimal amount = expectedAmount;

        PaymentRecord payment = new PaymentRecord();
        payment.setRoomId(roomId);
        payment.setRentalId(room.getCurrentRentalId());
        payment.setDueDate(room.getNextDueDate());
        payment.setCycleMonths(months);
        payment.setPeriodStart(periodStart);
        payment.setPeriodEnd(periodEnd);
        payment.setPaidDate(paidDate);
        payment.setAmount(amount);
        payment.setMethod(request.method());
        payment.setNotes(request.notes());
        paymentMapper.createRoomPayment(payment);
        LocalDate nextCollectionDate = advanceCollectionDate(
                room.getNextDueDate(), months, room.getCollectionDay()
        );
        LocalDate nextPeriodStartDate = periodEnd.plusDays(1);
        int roomUpdated = mapper.moveRoomSchedule(
                roomId, room.getCurrentRentalId(), nextCollectionDate, nextPeriodStartDate, paidDate
        );
        int rentalUpdated = mapper.moveRentalSchedule(
                roomId, room.getCurrentRentalId(), nextCollectionDate, nextPeriodStartDate
        );
        if (roomUpdated == 0 || rentalUpdated == 0) {
            throw new IllegalArgumentException("房间收租设置已经变化，请刷新页面后重试");
        }
        log.info(
                "Collected room rent paymentId={}, roomId={}, rentalId={}, months={}, amount={}, dueDate={}, periodStart={}, periodEnd={}, paidDate={}, nextCollectionDate={}, nextPeriodStartDate={}",
                payment.getId(), roomId, room.getCurrentRentalId(), months, amount, room.getNextDueDate(),
                periodStart, periodEnd, paidDate, nextCollectionDate, nextPeriodStartDate
        );
        return payment.getId();
    }

    public Map<String, Object> settlementPreview(Long roomId, LocalDate moveOutDate) {
        RoomRecord room = mapper.findRoomRecord(roomId);
        validateSettlementRoom(room, moveOutDate);
        RefundAmounts amounts = calculateRefundAmounts(room.getCurrentRentalId(), moveOutDate);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("roomId", roomId);
        preview.put("rentalId", room.getCurrentRentalId());
        preview.put("moveOutDate", moveOutDate);
        preview.put("depositAmount", zeroIfNull(room.getDepositAmount()));
        preview.put("suggestedRentRefundAmount", amounts.suggested());
        preview.put("maximumRentRefundAmount", amounts.maximum());
        preview.put("latestCoveredDate", paymentMapper.findLatestCoveredDate(roomId, room.getCurrentRentalId()));
        preview.put("nextPeriodStartDate", room.getNextPeriodStartDate());
        return preview;
    }

    @Transactional
    public Long settleRoomRent(Long roomId, PropertyDtos.RoomSettlementRequest request) {
        RoomRecord room = mapper.findRoomRecordForUpdate(roomId);
        validateSettlementRoom(room, request.moveOutDate());
        if (request.settlementDate().isBefore(request.moveOutDate())) {
            throw new IllegalArgumentException("结算日期不能早于实际退租日期");
        }
        if (!SETTLEMENT_REASONS.contains(request.reason())) {
            throw new IllegalArgumentException("请选择正确的退租原因");
        }
        String notes = blankToNull(request.notes());
        if ("OTHER".equals(request.reason()) && notes == null) {
            throw new IllegalArgumentException("选择其他原因时，请填写简短说明");
        }

        BigDecimal depositAmount = zeroIfNull(room.getDepositAmount());
        if (request.depositDeductionAmount().compareTo(depositAmount) > 0) {
            throw new IllegalArgumentException("押金扣款不能超过当前押金");
        }
        if (request.depositDeductionAmount().signum() > 0 && notes == null) {
            throw new IllegalArgumentException("有押金扣款时，请填写扣款说明");
        }
        RefundAmounts refundable = calculateRefundAmounts(room.getCurrentRentalId(), request.moveOutDate());
        if (request.rentRefundAmount().compareTo(refundable.maximum()) > 0) {
            throw new IllegalArgumentException("退还租金不能超过这次出租期间已收但尚未使用的租金");
        }

        BigDecimal depositRefundAmount = depositAmount
                .subtract(request.depositDeductionAmount())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRefundAmount = request.rentRefundAmount()
                .add(depositRefundAmount)
                .setScale(2, RoundingMode.HALF_UP);

        RentSettlementRecord settlement = new RentSettlementRecord();
        settlement.setRentalId(room.getCurrentRentalId());
        settlement.setRoomId(roomId);
        settlement.setSettlementDate(request.settlementDate());
        settlement.setMoveOutDate(request.moveOutDate());
        settlement.setReason(request.reason());
        settlement.setRentRefundAmount(request.rentRefundAmount());
        settlement.setDepositAmount(depositAmount);
        settlement.setDepositDeductionAmount(request.depositDeductionAmount());
        settlement.setDepositRefundAmount(depositRefundAmount);
        settlement.setTotalRefundAmount(totalRefundAmount);
        settlement.setNotes(notes);
        mapper.createSettlement(settlement);
        if (mapper.endRental(roomId, room.getCurrentRentalId(), request.moveOutDate()) == 0
                || mapper.settleRoomToVacant(roomId, room.getCurrentRentalId()) == 0) {
            throw new IllegalArgumentException("房间出租状态已经变化，请刷新页面后重试");
        }
        log.info(
                "Settled room rental settlementId={}, roomId={}, rentalId={}, settlementDate={}, moveOutDate={}, reason={}, rentRefundAmount={}, depositAmount={}, depositDeductionAmount={}, depositRefundAmount={}, totalRefundAmount={}",
                settlement.getId(), roomId, room.getCurrentRentalId(), request.settlementDate(),
                request.moveOutDate(), request.reason(), request.rentRefundAmount(), depositAmount,
                request.depositDeductionAmount(), depositRefundAmount, totalRefundAmount
        );
        return settlement.getId();
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.countActiveContractsByProperty(id) > 0 || mapper.countRentedRoomsByProperty(id) > 0) {
            throw new IllegalArgumentException("房源下还有出租中的房间，请先办理退租");
        }
        List<Long> roomIds = mapper.listRoomIdsByProperty(id);
        if (mapper.deleteProperty(id) == 0) {
            throw new IllegalArgumentException("房源不存在");
        }
        mapper.deleteRoomsByProperty(id);
        roomImageService.deleteAllForRooms(roomIds);
        log.info("Deleted property id={} and its inactive rooms", id);
    }

    @Transactional
    public void deleteRoom(Long roomId) {
        if (mapper.countActiveContractsByRoom(roomId) > 0 || mapper.countRentedRoom(roomId) > 0) {
            throw new IllegalArgumentException("这个房间正在出租，请先办理退租");
        }
        if (mapper.deleteRoom(roomId) == 0) {
            throw new IllegalArgumentException("房间不存在");
        }
        roomImageService.deleteAllForRoom(roomId);
        log.info("Deleted room id={}", roomId);
    }

    private void assertRoomStatus(String status) {
        if (!ROOM_STATUSES.contains(status)) {
            throw new IllegalArgumentException("房间状态不正确");
        }
    }

    private RoomRecord buildRoomRecord(PropertyDtos.RoomCreateRequest request) {
        RoomRecord record = new RoomRecord();
        record.setPropertyId(request.propertyId());
        record.setRoomNo(request.roomNo());
        record.setFloor(request.floor());
        record.setArea(request.area());
        record.setRentAmount(request.rentAmount());
        record.setDepositAmount(request.depositAmount());
        record.setStatus(request.status() == null ? "VACANT" : request.status());
        record.setPayCycleMonths(normalizeCycle(request.payCycleMonths()));
        record.setNextDueDate(request.nextDueDate());
        record.setOrientation(request.orientation());
        record.setTags(request.tags());
        record.setNotes(request.notes());
        assertRoomStatus(record.getStatus());
        return record;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String propertyName(PropertyDtos.PropertyCreateRequest request) {
        return request.name() == null || request.name().isBlank() ? request.address() : request.name();
    }

    private int normalizeCycle(Integer months) {
        return months == null || months < 1 ? 1 : months;
    }

    private void validateRentDates(LocalDate leaseStartDate, LocalDate leaseEndDate, LocalDate nextDueDate) {
        if (leaseStartDate == null || leaseEndDate == null) {
            throw new IllegalArgumentException("请填写租期开始日期和结束日期");
        }
        if (leaseEndDate.isBefore(leaseStartDate)) {
            throw new IllegalArgumentException("租期结束日期不能早于开始日期");
        }
        if (nextDueDate == null) {
            throw new IllegalArgumentException("请填写下次收租日");
        }
        validateCollectionDate(leaseStartDate, leaseEndDate, nextDueDate);
    }

    private void validateCollectionDate(LocalDate leaseStartDate, LocalDate leaseEndDate, LocalDate nextDueDate) {
        if (nextDueDate.isBefore(leaseStartDate) || nextDueDate.isAfter(leaseEndDate)) {
            throw new IllegalArgumentException("下次收租日必须在租期范围内");
        }
    }

    private void validateCoverageFits(LocalDate nextPeriodStartDate, LocalDate leaseEndDate, int payCycleMonths) {
        if (nextPeriodStartDate == null) {
            throw new IllegalArgumentException("下次租金的开始日期不完整，请暂停操作并联系维护人员");
        }
        if (nextPeriodStartDate.isAfter(leaseEndDate)) {
            throw new IllegalArgumentException("下次租金开始日期已经超出租期结束日期");
        }
        LocalDate nextCycleEnd = nextPeriodStartDate.plusMonths(payCycleMonths).minusDays(1);
        if (nextCycleEnd.isAfter(leaseEndDate)) {
            throw new IllegalArgumentException("按这个收租间隔，最后一次收租会超出租期结束日期，请缩短间隔或延长租期");
        }
    }

    private LocalDate advanceCollectionDate(LocalDate currentDate, int months, Integer preferredDay) {
        int day = preferredDay == null ? currentDate.getDayOfMonth() : Math.max(1, Math.min(31, preferredDay));
        YearMonth targetMonth = YearMonth.from(currentDate).plusMonths(months);
        return targetMonth.atDay(Math.min(day, targetMonth.lengthOfMonth()));
    }

    private void validateSettlementRoom(RoomRecord room, LocalDate moveOutDate) {
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }
        if (!"RENTED".equals(room.getStatus()) || room.getCurrentRentalId() == null) {
            throw new IllegalArgumentException("只有当前已出租的房间才能办理退租");
        }
        if (moveOutDate == null) {
            throw new IllegalArgumentException("请选择实际退租日期");
        }
        if (room.getLeaseStartDate() != null && moveOutDate.isBefore(room.getLeaseStartDate())) {
            throw new IllegalArgumentException("实际退租日期不能早于租期开始日期");
        }
        if (moveOutDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("实际退租日期不能晚于今天");
        }
    }

    private RefundAmounts calculateRefundAmounts(Long rentalId, LocalDate moveOutDate) {
        BigDecimal suggested = BigDecimal.ZERO;
        BigDecimal maximum = BigDecimal.ZERO;
        LocalDate unusedFrom = moveOutDate.plusDays(1);
        for (Map<String, Object> payment : mapper.listRefundablePayments(rentalId, moveOutDate)) {
            LocalDate periodStart = asLocalDate(payment.get("period_start"));
            LocalDate periodEnd = asLocalDate(payment.get("period_end"));
            BigDecimal amount = asBigDecimal(payment.get("amount"));
            maximum = maximum.add(amount);
            LocalDate effectiveUnusedStart = periodStart.isAfter(unusedFrom) ? periodStart : unusedFrom;
            if (effectiveUnusedStart.isAfter(periodEnd)) {
                continue;
            }
            long totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
            long unusedDays = ChronoUnit.DAYS.between(effectiveUnusedStart, periodEnd) + 1;
            BigDecimal paymentRefund = amount
                    .multiply(BigDecimal.valueOf(unusedDays))
                    .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
            suggested = suggested.add(paymentRefund);
        }
        return new RefundAmounts(
                suggested.setScale(2, RoundingMode.HALF_UP),
                maximum.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private LocalDate asLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private LocalDate firstNonNull(LocalDate... values) {
        for (LocalDate value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record RefundAmounts(BigDecimal suggested, BigDecimal maximum) {
    }
}
