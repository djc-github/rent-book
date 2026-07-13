package com.djc.rentbook.dashboard;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DashboardService {
    private final DashboardMapper mapper;

    public DashboardService(DashboardMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> overview() {
        return Map.of(
                "summary", mapper.summary(),
                "dueRent", mapper.dueRent(),
                "expiringContracts", mapper.expiringContracts(),
                "vacantRooms", mapper.vacantRooms()
        );
    }
}
