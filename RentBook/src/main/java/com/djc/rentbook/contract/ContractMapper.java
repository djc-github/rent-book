package com.djc.rentbook.contract;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ContractMapper {
    @Select("""
            select c.*, t.name as tenant_name, t.phone as tenant_phone, coalesce(nullif(p.address, ''), p.name) as property_name, r.room_no
            from contracts c
            join tenants t on t.id = c.tenant_id
            join rooms r on r.id = c.room_id
            join properties p on p.id = r.property_id
            where c.deleted = false
              and (#{status,jdbcType=VARCHAR} is null or c.status = #{status,jdbcType=VARCHAR})
            order by c.next_due_date asc, c.end_date asc
            """)
    List<Map<String, Object>> list(@Param("status") String status);

    @Select("select count(*) from contracts where room_id = #{roomId} and status = 'ACTIVE' and deleted = false")
    int countActiveByRoom(@Param("roomId") Long roomId);

    @Select("select room_id from contracts where id = #{id} and status = 'ACTIVE' and deleted = false")
    Long findActiveRoomId(@Param("id") Long id);

    @Select("select room_id from contracts where id = #{id} and deleted = false")
    Long findRoomId(@Param("id") Long id);

    @Select("select count(*) from rent_payments where contract_id = #{id} and deleted = false")
    int countPayments(@Param("id") Long id);

    @Insert("""
            insert into contracts(room_id, tenant_id, contract_no, start_date, end_date, rent_amount, deposit_amount, pay_cycle_months, next_due_date, notes)
            values(#{roomId}, #{tenantId}, #{contractNo}, #{startDate}, #{endDate}, #{rentAmount}, #{depositAmount}, #{payCycleMonths}, #{nextDueDate}, #{notes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(ContractRecord contract);

    @Update("update rooms set status = 'RENTED', rent_amount = #{rentAmount}, deposit_amount = #{depositAmount}, updated_at = now() where id = #{roomId}")
    void markRoomRented(ContractRecord contract);

    @Update("update contracts set status = #{status}, updated_at = now() where id = #{id} and deleted = false")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("""
            update contracts
            set status = #{status},
                end_date = #{endDate},
                notes = concat_ws(E'\n', notes, #{notes,jdbcType=VARCHAR}),
                updated_at = now()
            where id = #{id} and status = 'ACTIVE' and deleted = false
            """)
    int terminate(@Param("id") Long id, @Param("status") String status, @Param("endDate") java.time.LocalDate endDate, @Param("notes") String notes);

    @Update("""
            update contracts
            set tenant_id = #{request.newTenantId},
                start_date = coalesce(#{request.startDate,jdbcType=DATE}, start_date),
                end_date = coalesce(#{request.endDate,jdbcType=DATE}, end_date),
                rent_amount = coalesce(#{request.rentAmount,jdbcType=DECIMAL}, rent_amount),
                deposit_amount = coalesce(#{request.depositAmount,jdbcType=DECIMAL}, deposit_amount),
                next_due_date = coalesce(#{request.nextDueDate,jdbcType=DATE}, next_due_date),
                notes = concat_ws(E'\n', notes, #{request.notes,jdbcType=VARCHAR}),
                updated_at = now()
            where id = #{id} and status = 'ACTIVE' and deleted = false
            """)
    int transfer(@Param("id") Long id, @Param("request") ContractDtos.TransferRequest request);

    @Update("""
            update contracts
            set room_id = #{request.newRoomId},
                rent_amount = coalesce(#{request.rentAmount,jdbcType=DECIMAL}, rent_amount),
                deposit_amount = coalesce(#{request.depositAmount,jdbcType=DECIMAL}, deposit_amount),
                next_due_date = coalesce(#{request.nextDueDate,jdbcType=DATE}, next_due_date),
                notes = concat_ws(E'\n', notes, #{request.notes,jdbcType=VARCHAR}),
                updated_at = now()
            where id = #{id} and status = 'ACTIVE' and deleted = false
            """)
    int changeRoom(@Param("id") Long id, @Param("request") ContractDtos.ChangeRoomRequest request);

    @Update("""
            update contracts
            set end_date = #{request.endDate},
                next_due_date = #{request.nextDueDate},
                pay_cycle_months = #{request.payCycleMonths},
                rent_amount = coalesce(#{request.rentAmount,jdbcType=DECIMAL}, rent_amount),
                deposit_amount = coalesce(#{request.depositAmount,jdbcType=DECIMAL}, deposit_amount),
                notes = concat_ws(E'\n', notes, #{request.notes,jdbcType=VARCHAR}),
                updated_at = now()
            where id = #{id} and status = 'ACTIVE' and deleted = false
            """)
    int renew(@Param("id") Long id, @Param("request") ContractDtos.RenewRequest request);

    @Update("update rooms set status = #{status}, updated_at = now() where id = #{roomId}")
    int updateRoomStatus(@Param("roomId") Long roomId, @Param("status") String status);

    @Update("update contracts set deleted = true, status = 'CANCELLED', updated_at = now() where id = #{id} and deleted = false")
    int delete(@Param("id") Long id);
}
