package com.djc.rentbook.property;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface PropertyMapper {
    @Select("""
            select p.id, p.name, p.address, p.district, p.landlord_name, p.landlord_phone, p.manager,
                   count(r.id) as room_count,
                   count(*) filter (where r.status = 'VACANT') as vacant_count,
                   count(*) filter (where r.status = 'RENTED') as rented_count,
                   count(*) filter (where r.status = 'RESERVED') as reserved_count
            from properties p
            left join rooms r on r.property_id = p.id and r.deleted = false
            where p.deleted = false
              and (#{keyword,jdbcType=VARCHAR} is null
                   or p.name ilike concat('%', #{keyword,jdbcType=VARCHAR}, '%')
                   or p.address ilike concat('%', #{keyword,jdbcType=VARCHAR}, '%'))
            group by p.id
            order by p.created_at desc
            """)
    List<Map<String, Object>> listProperties(@Param("keyword") String keyword);

    @Select("select * from properties where id = #{id} and deleted = false")
    Map<String, Object> findProperty(@Param("id") Long id);

    @Select("""
            select count(*)
            from contracts c
            join rooms r on r.id = c.room_id
            where r.property_id = #{propertyId}
              and r.deleted = false
              and c.deleted = false
              and c.status = 'ACTIVE'
            """)
    int countActiveContractsByProperty(@Param("propertyId") Long propertyId);

    @Select("select count(*) from rooms where property_id = #{propertyId} and deleted = false and status = 'RENTED'")
    int countRentedRoomsByProperty(@Param("propertyId") Long propertyId);

    @Select("select count(*) from contracts where room_id = #{roomId} and deleted = false and status = 'ACTIVE'")
    int countActiveContractsByRoom(@Param("roomId") Long roomId);

    @Select("select count(*) from rooms where id = #{roomId} and deleted = false and status = 'RENTED'")
    int countRentedRoom(@Param("roomId") Long roomId);

    @Select("""
            select r.*, t.name as tenant_name, c.end_date as contract_end_date,
                   ri.id as image_id, ri.url as image_url, coalesce(ri.thumbnail_url, ri.url) as image_thumbnail_url
            from rooms r
            left join contracts c on c.room_id = r.id and c.status = 'ACTIVE'
            left join tenants t on t.id = c.tenant_id
            left join lateral (
                select id, url, thumbnail_url
                from room_images
                where room_id = r.id and deleted = false
                order by sort_order, id
                limit 1
            ) ri on true
            where r.property_id = #{propertyId} and r.deleted = false
            order by r.room_no
            """)
    List<Map<String, Object>> listRooms(@Param("propertyId") Long propertyId);

    @Select("""
            select r.id, r.property_id, r.room_no, r.floor, r.area, r.rent_amount, r.deposit_amount, r.status,
                   r.pay_cycle_months, r.next_due_date, r.last_paid_date, r.lease_start_date, r.lease_end_date,
                   r.orientation, r.tags,
                   coalesce(nullif(p.address, ''), p.name) as property_name, p.address as property_address,
                   ri.id as image_id, ri.url as image_url, coalesce(ri.thumbnail_url, ri.url) as image_thumbnail_url
            from rooms r
            join properties p on p.id = r.property_id
            left join lateral (
                select id, url, thumbnail_url
                from room_images
                where room_id = r.id and deleted = false
                order by sort_order, id
                limit 1
            ) ri on true
            where r.deleted = false
              and p.deleted = false
              and (#{status,jdbcType=VARCHAR} is null or r.status = #{status,jdbcType=VARCHAR})
            order by coalesce(nullif(p.address, ''), p.name), r.room_no
            """)
    List<Map<String, Object>> listAllRooms(@Param("status") String status);

    @Insert("""
            insert into properties(name, address, district, landlord_name, landlord_phone, manager, notes)
            values(#{name}, #{address}, #{district}, #{landlordName}, #{landlordPhone}, #{manager}, #{notes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createProperty(PropertyRecord property);

    @Update("""
            update properties
            set name = #{name},
                address = #{address},
                district = #{district},
                landlord_name = #{landlordName},
                landlord_phone = #{landlordPhone},
                manager = #{manager},
                notes = #{notes},
                updated_at = now()
            where id = #{id} and deleted = false
            """)
    int updateProperty(PropertyRecord property);

    @Insert("""
            insert into rooms(property_id, room_no, floor, area, rent_amount, deposit_amount, status, pay_cycle_months, next_due_date, orientation, tags, notes)
            values(#{propertyId}, #{roomNo}, #{floor}, #{area}, #{rentAmount}, #{depositAmount}, #{status}, #{payCycleMonths}, #{nextDueDate}, #{orientation}, #{tags}, #{notes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createRoom(RoomRecord room);

    @Update("""
            update rooms
            set property_id = #{propertyId},
                room_no = #{roomNo},
                floor = #{floor},
                area = #{area},
                rent_amount = #{rentAmount},
                deposit_amount = #{depositAmount},
                orientation = #{orientation},
                tags = #{tags},
                notes = #{notes},
                updated_at = now()
            where id = #{id} and deleted = false
            """)
    int updateRoom(RoomRecord room);

    @Update("""
            update rooms
            set status = #{status},
                next_due_date = case when #{status} = 'RENTED' then next_due_date else null end,
                last_paid_date = case when #{status} = 'RENTED' then last_paid_date else null end,
                lease_start_date = case when #{status} = 'RENTED' then lease_start_date else null end,
                lease_end_date = case when #{status} = 'RENTED' then lease_end_date else null end,
                updated_at = now()
            where id = #{roomId} and deleted = false
            """)
    int updateRoomStatus(@Param("roomId") Long roomId, @Param("status") String status);

    @Update("""
            update rooms
            set status = 'RENTED',
                rent_amount = coalesce(#{request.rentAmount,jdbcType=DECIMAL}, rent_amount),
                deposit_amount = coalesce(#{request.depositAmount,jdbcType=DECIMAL}, deposit_amount),
                pay_cycle_months = coalesce(#{request.payCycleMonths,jdbcType=INTEGER}, pay_cycle_months),
                lease_start_date = #{request.leaseStartDate},
                lease_end_date = #{request.leaseEndDate},
                next_due_date = #{request.nextDueDate},
                last_paid_date = case when status = 'RENTED' then last_paid_date else null end,
                notes = concat_ws(E'\n', notes, #{request.notes,jdbcType=VARCHAR}),
                updated_at = now()
            where id = #{roomId} and deleted = false
            """)
    int startRoomRent(@Param("roomId") Long roomId, @Param("request") PropertyDtos.RoomRentRequest request);

    @Select("select * from rooms where id = #{roomId} and deleted = false")
    RoomRecord findRoomRecord(@Param("roomId") Long roomId);

    @Select("select * from rooms where id = #{roomId} and deleted = false for update")
    RoomRecord findRoomRecordForUpdate(@Param("roomId") Long roomId);

    @Select("select id from rooms where property_id = #{propertyId} and deleted = false")
    List<Long> listRoomIdsByProperty(@Param("propertyId") Long propertyId);

    @Update("""
            update rooms
            set next_due_date = #{nextDueDate},
                last_paid_date = #{paidDate},
                updated_at = now()
            where id = #{roomId} and deleted = false
            """)
    int moveRoomDueDate(@Param("roomId") Long roomId, @Param("nextDueDate") java.time.LocalDate nextDueDate, @Param("paidDate") java.time.LocalDate paidDate);

    @Update("update properties set deleted = true, updated_at = now() where id = #{id} and deleted = false")
    int deleteProperty(@Param("id") Long id);

    @Update("update rooms set deleted = true, updated_at = now() where property_id = #{propertyId} and deleted = false")
    int deleteRoomsByProperty(@Param("propertyId") Long propertyId);

    @Update("update rooms set deleted = true, updated_at = now() where id = #{roomId} and deleted = false")
    int deleteRoom(@Param("roomId") Long roomId);

    @Update("""
            update rooms
            set status = 'VACANT',
                next_due_date = null,
                last_paid_date = null,
                lease_start_date = null,
                lease_end_date = null,
                updated_at = now()
            where deleted = false
              and status = 'RENTED'
              and lease_end_date is not null
              and lease_end_date < #{today}
            """)
    int expireEndedRoomLeases(@Param("today") java.time.LocalDate today);
}
