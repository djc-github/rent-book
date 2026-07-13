package com.djc.rentbook.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class TenantService {
    private static final Logger log = LoggerFactory.getLogger(TenantService.class);
    private final TenantMapper mapper;

    public TenantService(TenantMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> list(String keyword) {
        return mapper.list(keyword == null || keyword.isBlank() ? null : keyword);
    }

    @Transactional
    public Long create(TenantDtos.TenantRequest request) {
        TenantRecord tenant = toRecord(null, request);
        mapper.create(tenant);
        log.info("Created tenant id={}, name={}, phone={}", tenant.getId(), tenant.getName(), tenant.getPhone());
        return tenant.getId();
    }

    @Transactional
    public void update(Long id, TenantDtos.TenantRequest request) {
        TenantRecord tenant = toRecord(id, request);
        if (mapper.update(tenant) == 0) {
            throw new IllegalArgumentException("租客不存在");
        }
        log.info("Updated tenant id={}, name={}, phone={}", id, tenant.getName(), tenant.getPhone());
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.countActiveContracts(id) > 0) {
            throw new IllegalArgumentException("租客还有生效合同，请先退租、转租或撤销合同");
        }
        if (mapper.delete(id) == 0) {
            throw new IllegalArgumentException("租客不存在");
        }
        log.info("Deleted tenant id={}", id);
    }

    private TenantRecord toRecord(Long id, TenantDtos.TenantRequest request) {
        TenantRecord tenant = new TenantRecord();
        tenant.setId(id);
        tenant.setName(request.name());
        tenant.setPhone(request.phone());
        tenant.setIdCard(request.idCard());
        tenant.setEmergencyContact(request.emergencyContact());
        tenant.setEmergencyPhone(request.emergencyPhone());
        tenant.setSource(request.source());
        tenant.setNotes(request.notes());
        return tenant;
    }
}
