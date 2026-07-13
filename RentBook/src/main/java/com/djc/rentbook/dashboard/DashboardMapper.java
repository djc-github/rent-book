package com.djc.rentbook.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DashboardMapper {
    @Select("""
            select
              (select count(*) from properties where deleted = false) as property_count,
              (select count(*) from rooms where deleted = false) as room_count,
              (select count(*) from rooms where deleted = false and status = 'VACANT') as vacant_count,
              (select count(*) from rooms where deleted = false and status = 'RENTED') as rented_count,
              (select coalesce(sum(amount), 0) from rent_payments where deleted = false and paid_date >= date_trunc('month', current_date)) as month_income,
              (select count(*) from rooms where deleted = false and status = 'RENTED' and next_due_date <= current_date + 7) as due_soon_count,
              (select count(*) from rooms where deleted = false and status = 'RENTED' and next_due_date < current_date) as overdue_count,
              0 as expiring_count
            """)
    Map<String, Object> summary();

    @Select("""
            select r.id as room_id, r.next_due_date, r.rent_amount, r.pay_cycle_months, r.last_paid_date, r.lease_end_date,
                   coalesce(nullif(p.address, ''), p.name) as property_name, r.room_no,
                   case when r.next_due_date < current_date then 'OVERDUE' else 'DUE_SOON' end as urgency
            from rooms r
            join properties p on p.id = r.property_id
            where r.deleted = false and r.status = 'RENTED' and r.next_due_date <= current_date + 7
            order by r.next_due_date asc
            limit 20
            """)
    List<Map<String, Object>> dueRent();

    @Select("""
            select c.id as contract_id, c.end_date, t.name as tenant_name, t.phone, coalesce(nullif(p.address, ''), p.name) as property_name, r.room_no
            from contracts c
            join tenants t on t.id = c.tenant_id
            join rooms r on r.id = c.room_id
            join properties p on p.id = r.property_id
            where c.deleted = false and c.status = 'ACTIVE' and c.end_date <= current_date + 30
            order by c.end_date asc
            limit 20
            """)
    List<Map<String, Object>> expiringContracts();

    @Select("""
            select coalesce(nullif(p.address, ''), p.name) as property_name, r.id as room_id, r.room_no, r.rent_amount, r.deposit_amount, r.tags
            from rooms r
            join properties p on p.id = r.property_id
            where r.deleted = false and r.status = 'VACANT'
            order by coalesce(nullif(p.address, ''), p.name), r.room_no
            limit 30
            """)
    List<Map<String, Object>> vacantRooms();
}
