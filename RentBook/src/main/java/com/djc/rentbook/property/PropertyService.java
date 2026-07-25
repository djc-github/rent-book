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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class PropertyService {
    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);
    private static final List<String> ROOM_STATUSES = List.of("VACANT", "RESERVED", "RENTED", "MAINTENANCE", "OFFLINE");
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
        if ("RENTED".equals(room.getStatus()) && "RESERVED".equals(status)) {
            throw new IllegalArgumentException("已出租房间不能直接改为预定，请先设为空置");
        }
        if (mapper.updateRoomStatus(roomId, status) == 0) {
            throw new IllegalArgumentException("房间不存在");
        }
        log.info("Updated room status roomId={}, status={}", roomId, status);
    }

    @Transactional
    public int expireEndedRoomLeases(LocalDate today) {
        LocalDate effectiveDate = today == null ? LocalDate.now() : today;
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
        validateRentPeriod(request.leaseStartDate(), request.leaseEndDate(), request.nextDueDate(), normalizeCycle(request.payCycleMonths()));
        LocalDate latestCoveredDate = paymentMapper.findLatestCoveredDate(roomId);
        if (latestCoveredDate != null && !request.nextDueDate().isAfter(latestCoveredDate)) {
            throw new IllegalArgumentException(
                    "下次应收日不能落在已经收过租的日期内，当前已收至" + latestCoveredDate + "，请先核对或撤销错误记录"
            );
        }
        if (mapper.startRoomRent(roomId, request) == 0) {
            throw new IllegalArgumentException("房间不存在");
        }
        log.info("Started room rent roomId={}, rentAmount={}, depositAmount={}, payCycleMonths={}, leaseStartDate={}, leaseEndDate={}, nextDueDate={}",
                roomId, request.rentAmount(), request.depositAmount(), request.payCycleMonths(),
                request.leaseStartDate(), request.leaseEndDate(), request.nextDueDate());
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
        LocalDate today = LocalDate.now();
        if (room.getNextDueDate() == null) {
            throw new IllegalArgumentException("请先设置下次应收日");
        }
        if (room.getNextDueDate().isAfter(today.plusDays(rentCollectAdvanceDays))) {
            throw new IllegalArgumentException("还没到可收租时间，请在应收日前" + rentCollectAdvanceDays + "天内再收");
        }
        LocalDate periodStart = room.getNextDueDate();
        int months = normalizeCycle(room.getPayCycleMonths());
        if (request.months() != null && request.months() != months) {
            throw new IllegalArgumentException("收租周期已经变化，请刷新页面后重新确认");
        }
        LocalDate periodEnd = periodStart.plusMonths(months).minusDays(1);
        if (room.getLeaseEndDate() != null && periodStart.isAfter(room.getLeaseEndDate())) {
            throw new IllegalArgumentException("租期已结束，不能继续收租");
        }
        if (room.getLeaseEndDate() != null && periodEnd.isAfter(room.getLeaseEndDate())) {
            throw new IllegalArgumentException("本次收租会超过租期结束日期，请先调整租期或减少收租月数");
        }
        Long overlappingId = paymentMapper.findOverlappingPaymentId(roomId, periodStart, periodEnd, null);
        if (overlappingId != null) {
            throw new IllegalArgumentException(
                    "该房间的" + periodStart + "至" + periodEnd + "租期已经登记过收租，请先核对收租记录"
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
        payment.setPeriodStart(periodStart);
        payment.setPeriodEnd(periodEnd);
        payment.setPaidDate(paidDate);
        payment.setAmount(amount);
        payment.setMethod(request.method());
        payment.setNotes(request.notes());
        paymentMapper.createRoomPayment(payment);
        mapper.moveRoomDueDate(roomId, periodEnd.plusDays(1), paidDate);
        log.info("Collected room rent paymentId={}, roomId={}, months={}, amount={}, periodStart={}, periodEnd={}, paidDate={}, nextDueDate={}",
                payment.getId(), roomId, months, amount, periodStart, periodEnd, paidDate, periodEnd.plusDays(1));
        return payment.getId();
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.countActiveContractsByProperty(id) > 0 || mapper.countRentedRoomsByProperty(id) > 0) {
            throw new IllegalArgumentException("房源下还有已出租房间，请先将房间设为空置");
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
            throw new IllegalArgumentException("已出租房间不能删除，请先将房间设为空置");
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

    private void validateRentPeriod(LocalDate leaseStartDate, LocalDate leaseEndDate, LocalDate nextDueDate, int payCycleMonths) {
        if (leaseStartDate == null || leaseEndDate == null) {
            throw new IllegalArgumentException("请填写租期开始日期和结束日期");
        }
        if (leaseEndDate.isBefore(leaseStartDate)) {
            throw new IllegalArgumentException("租期结束日期不能早于开始日期");
        }
        if (nextDueDate == null) {
            throw new IllegalArgumentException("请填写下次应收日");
        }
        if (nextDueDate.isBefore(leaseStartDate) || nextDueDate.isAfter(leaseEndDate)) {
            throw new IllegalArgumentException("下次应收日必须在租期范围内");
        }
        LocalDate nextCycleEnd = nextDueDate.plusMonths(payCycleMonths).minusDays(1);
        if (nextCycleEnd.isAfter(leaseEndDate)) {
            throw new IllegalArgumentException("从下次应收日起，本次收租周期不能超过租期结束日期");
        }
    }
}
