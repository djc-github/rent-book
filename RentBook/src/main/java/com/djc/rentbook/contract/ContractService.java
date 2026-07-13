package com.djc.rentbook.contract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ContractService {
    private static final Logger log = LoggerFactory.getLogger(ContractService.class);
    private final ContractMapper mapper;

    public ContractService(ContractMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> list(String status) {
        return mapper.list(status == null || status.isBlank() ? null : status);
    }

    @Transactional
    public Long create(ContractDtos.ContractRequest request) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw new IllegalArgumentException("合同结束日期必须晚于开始日期");
        }
        if (mapper.countActiveByRoom(request.roomId()) > 0) {
            throw new IllegalArgumentException("该房间已有生效合同，不能重复出租");
        }
        ContractRecord record = new ContractRecord();
        record.setRoomId(request.roomId());
        record.setTenantId(request.tenantId());
        record.setContractNo(request.contractNo());
        record.setStartDate(request.startDate());
        record.setEndDate(request.endDate());
        record.setRentAmount(request.rentAmount());
        record.setDepositAmount(request.depositAmount());
        record.setPayCycleMonths(request.payCycleMonths());
        record.setNextDueDate(request.nextDueDate());
        record.setNotes(request.notes());
        mapper.create(record);
        mapper.markRoomRented(record);
        log.info("Created contract id={}, roomId={}, tenantId={}", record.getId(), record.getRoomId(), record.getTenantId());
        return record.getId();
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        if (!List.of("ACTIVE", "ENDED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("合同状态不正确");
        }
        if (mapper.updateStatus(id, status) == 0) {
            throw new IllegalArgumentException("合同不存在");
        }
        log.info("Updated contract status id={}, status={}", id, status);
    }

    @Transactional
    public void terminate(Long id, ContractDtos.TerminateRequest request) {
        String status = request.status() == null || request.status().isBlank() ? "ENDED" : request.status();
        if (!List.of("ENDED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("合同结束状态不正确");
        }
        String roomStatus = request.roomStatus() == null || request.roomStatus().isBlank() ? "VACANT" : request.roomStatus();
        if (!List.of("VACANT", "MAINTENANCE", "OFFLINE").contains(roomStatus)) {
            throw new IllegalArgumentException("退租后的房态不正确");
        }
        Long oldRoomId = mapper.findActiveRoomId(id);
        if (oldRoomId == null) {
            throw new IllegalArgumentException("只允许处理生效中的合同");
        }
        if (mapper.terminate(id, status, request.endDate(), request.notes()) == 0) {
            throw new IllegalArgumentException("合同不存在");
        }
        mapper.updateRoomStatus(oldRoomId, roomStatus);
        log.info("Terminated contract id={}, status={}, roomStatus={}", id, status, roomStatus);
    }

    @Transactional
    public void transfer(Long id, ContractDtos.TransferRequest request) {
        if (mapper.transfer(id, request) == 0) {
            throw new IllegalArgumentException("只允许转让生效中的合同");
        }
        log.info("Transferred contract id={}, newTenantId={}", id, request.newTenantId());
    }

    @Transactional
    public void changeRoom(Long id, ContractDtos.ChangeRoomRequest request) {
        Long oldRoomId = mapper.findActiveRoomId(id);
        if (oldRoomId == null) {
            throw new IllegalArgumentException("只允许调整生效中的合同");
        }
        if (mapper.countActiveByRoom(request.newRoomId()) > 0) {
            throw new IllegalArgumentException("目标房间已有生效合同");
        }
        if (mapper.changeRoom(id, request) == 0) {
            throw new IllegalArgumentException("合同不存在");
        }
        mapper.updateRoomStatus(oldRoomId, "VACANT");
        mapper.updateRoomStatus(request.newRoomId(), "RENTED");
        log.info("Changed contract room id={}, oldRoomId={}, newRoomId={}", id, oldRoomId, request.newRoomId());
    }

    @Transactional
    public void renew(Long id, ContractDtos.RenewRequest request) {
        if (!request.endDate().isAfter(request.nextDueDate())) {
            throw new IllegalArgumentException("续租到期日必须晚于下次应收日");
        }
        if (mapper.renew(id, request) == 0) {
            throw new IllegalArgumentException("只允许续租生效中的合同");
        }
        log.info("Renewed contract id={}, endDate={}, nextDueDate={}", id, request.endDate(), request.nextDueDate());
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.countPayments(id) > 0) {
            throw new IllegalArgumentException("合同已有收租流水，请先撤销收租记录");
        }
        Long activeRoomId = mapper.findActiveRoomId(id);
        Long roomId = activeRoomId != null ? activeRoomId : mapper.findRoomId(id);
        if (roomId == null) {
            throw new IllegalArgumentException("合同不存在");
        }
        if (mapper.delete(id) == 0) {
            throw new IllegalArgumentException("合同不存在");
        }
        if (activeRoomId != null) {
            mapper.updateRoomStatus(activeRoomId, "VACANT");
        }
        log.info("Deleted contract id={}, releasedRoomId={}", id, activeRoomId);
    }
}
