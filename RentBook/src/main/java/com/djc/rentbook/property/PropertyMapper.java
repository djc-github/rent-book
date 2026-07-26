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
                   coalesce(r.lease_start_date, current_rental.lease_start_date) as lease_start_date,
                   coalesce(r.lease_end_date, current_rental.lease_end_date) as lease_end_date,
                   coalesce(r.next_due_date, current_rental.next_collection_date) as next_due_date,
                   coalesce(r.next_period_start_date, current_rental.next_period_start_date) as next_period_start_date,
                   ri.id as image_id, ri.url as image_url, coalesce(ri.thumbnail_url, ri.url) as image_thumbnail_url,
                   payment.latest_covered_date
            from rooms r
            left join contracts c on c.room_id = r.id and c.status = 'ACTIVE'
            left join tenants t on t.id = c.tenant_id
            left join room_rentals current_rental on current_rental.id = r.current_rental_id
            left join lateral (
                select id, url, thumbnail_url
                from room_images
                where room_id = r.id and deleted = false
                order by sort_order, id
                limit 1
            ) ri on true
            left join lateral (
                select max(pay.period_end) as latest_covered_date
                from rent_payments pay
                left join contracts payment_contract on payment_contract.id = pay.contract_id
                where pay.deleted = false
                  and coalesce(pay.room_id, payment_contract.room_id) = r.id
                  and (r.current_rental_id is null or pay.rental_id = r.current_rental_id)
            ) payment on true
            where r.property_id = #{propertyId} and r.deleted = false
            order by r.room_no
            """)
    List<Map<String, Object>> listRooms(@Param("propertyId") Long propertyId);

    @Select("""
            select r.id, r.property_id, r.room_no, r.floor, r.area, r.rent_amount, r.deposit_amount, r.status,
                   r.pay_cycle_months,
                   coalesce(r.next_due_date, current_rental.next_collection_date) as next_due_date,
                   r.last_paid_date,
                   coalesce(r.lease_start_date, current_rental.lease_start_date) as lease_start_date,
                   coalesce(r.lease_end_date, current_rental.lease_end_date) as lease_end_date,
                   coalesce(r.next_period_start_date, current_rental.next_period_start_date) as next_period_start_date,
                   coalesce(r.collection_day, current_rental.collection_day) as collection_day,
                   r.current_rental_id, r.orientation, r.tags,
                   coalesce(nullif(p.address, ''), p.name) as property_name, p.address as property_address,
                   ri.id as image_id, ri.url as image_url, coalesce(ri.thumbnail_url, ri.url) as image_thumbnail_url,
                   payment.latest_covered_date
            from rooms r
            join properties p on p.id = r.property_id
            left join room_rentals current_rental on current_rental.id = r.current_rental_id
            left join lateral (
                select id, url, thumbnail_url
                from room_images
                where room_id = r.id and deleted = false
                order by sort_order, id
                limit 1
            ) ri on true
            left join lateral (
                select max(pay.period_end) as latest_covered_date
                from rent_payments pay
                left join contracts payment_contract on payment_contract.id = pay.contract_id
                where pay.deleted = false
                  and coalesce(pay.room_id, payment_contract.room_id) = r.id
                  and (r.current_rental_id is null or pay.rental_id = r.current_rental_id)
            ) payment on true
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
                next_period_start_date = case when #{status} = 'RENTED' then next_period_start_date else null end,
                collection_day = case when #{status} = 'RENTED' then collection_day else null end,
                current_rental_id = case when #{status} = 'RENTED' then current_rental_id else null end,
                last_paid_date = case when #{status} = 'RENTED' then last_paid_date else null end,
                lease_start_date = case when #{status} = 'RENTED' then lease_start_date else null end,
                lease_end_date = case when #{status} = 'RENTED' then lease_end_date else null end,
                updated_at = now()
            where id = #{roomId} and deleted = false
            """)
    int updateRoomStatus(@Param("roomId") Long roomId, @Param("status") String status);

    @Insert("""
            insert into room_rentals(
                room_id, status, lease_start_date, lease_end_date,
                rent_amount, deposit_amount, pay_cycle_months,
                next_collection_date, collection_day, next_period_start_date, notes
            )
            values(
                #{roomId}, 'ACTIVE', #{leaseStartDate}, #{leaseEndDate},
                #{rentAmount}, #{depositAmount}, #{payCycleMonths},
                #{nextCollectionDate}, #{collectionDay}, #{nextPeriodStartDate}, #{notes}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createRoomRental(RoomRentalRecord rental);

    @Update("""
            update rooms
            set status = 'RENTED',
                rent_amount = coalesce(#{request.rentAmount,jdbcType=DECIMAL}, rent_amount),
                deposit_amount = coalesce(#{request.depositAmount,jdbcType=DECIMAL}, deposit_amount),
                pay_cycle_months = coalesce(#{request.payCycleMonths,jdbcType=INTEGER}, pay_cycle_months),
                lease_start_date = #{request.leaseStartDate},
                lease_end_date = #{request.leaseEndDate},
                next_due_date = #{request.nextDueDate},
                next_period_start_date = #{nextPeriodStartDate},
                collection_day = #{collectionDay},
                current_rental_id = #{rentalId},
                last_paid_date = null,
                notes = concat_ws(E'\n', notes, #{request.notes,jdbcType=VARCHAR}),
                updated_at = now()
            where id = #{roomId} and deleted = false
            """)
    int startNewRoomRent(@Param("roomId") Long roomId,
                         @Param("rentalId") Long rentalId,
                         @Param("nextPeriodStartDate") java.time.LocalDate nextPeriodStartDate,
                         @Param("collectionDay") Integer collectionDay,
                         @Param("request") PropertyDtos.RoomRentRequest request);

    @Update("""
            update rooms
            set rent_amount = #{request.rentAmount},
                deposit_amount = #{request.depositAmount},
                pay_cycle_months = #{request.payCycleMonths},
                lease_start_date = #{request.leaseStartDate},
                lease_end_date = #{request.leaseEndDate},
                next_due_date = #{request.nextDueDate},
                collection_day = #{collectionDay},
                notes = concat_ws(E'\n', notes, #{request.notes,jdbcType=VARCHAR}),
                updated_at = now()
            where id = #{roomId}
              and deleted = false
              and status = 'RENTED'
              and current_rental_id = #{rentalId}
            """)
    int updateCurrentRoomRent(@Param("roomId") Long roomId,
                              @Param("rentalId") Long rentalId,
                              @Param("collectionDay") Integer collectionDay,
                              @Param("request") PropertyDtos.RoomRentRequest request);

    @Update("""
            update room_rentals
            set lease_start_date = #{request.leaseStartDate},
                lease_end_date = #{request.leaseEndDate},
                rent_amount = #{request.rentAmount},
                deposit_amount = #{request.depositAmount},
                pay_cycle_months = #{request.payCycleMonths},
                next_collection_date = #{request.nextDueDate},
                collection_day = #{collectionDay},
                notes = concat_ws(E'\n', notes, #{request.notes,jdbcType=VARCHAR}),
                updated_at = now()
            where id = #{rentalId}
              and room_id = #{roomId}
              and status = 'ACTIVE'
            """)
    int updateCurrentRental(@Param("roomId") Long roomId,
                            @Param("rentalId") Long rentalId,
                            @Param("collectionDay") Integer collectionDay,
                            @Param("request") PropertyDtos.RoomRentRequest request);

    @Update("""
            update rooms
            set next_due_date = #{nextDueDate},
                collection_day = extract(day from cast(#{nextDueDate} as date))::integer,
                updated_at = now()
            where id = #{roomId}
              and deleted = false
              and next_due_date = #{expectedNextDueDate}
            """)
    int adjustRoomNextDueDate(@Param("roomId") Long roomId,
                              @Param("expectedNextDueDate") java.time.LocalDate expectedNextDueDate,
                              @Param("nextDueDate") java.time.LocalDate nextDueDate);

    @Update("""
            update room_rentals
            set next_collection_date = #{nextDueDate},
                collection_day = extract(day from cast(#{nextDueDate} as date))::integer,
                updated_at = now()
            where id = #{rentalId}
              and room_id = #{roomId}
              and status = 'ACTIVE'
            """)
    int adjustRentalCollectionDate(@Param("roomId") Long roomId,
                                   @Param("rentalId") Long rentalId,
                                   @Param("nextDueDate") java.time.LocalDate nextDueDate);

    @Select("select * from rooms where id = #{roomId} and deleted = false")
    RoomRecord findRoomRecord(@Param("roomId") Long roomId);

    @Select("select * from rooms where id = #{roomId} and deleted = false for update")
    RoomRecord findRoomRecordForUpdate(@Param("roomId") Long roomId);

    @Select("select id from rooms where property_id = #{propertyId} and deleted = false")
    List<Long> listRoomIdsByProperty(@Param("propertyId") Long propertyId);

    @Update("""
            update rooms
            set next_due_date = #{nextCollectionDate},
                next_period_start_date = #{nextPeriodStartDate},
                last_paid_date = #{paidDate},
                updated_at = now()
            where id = #{roomId}
              and deleted = false
              and current_rental_id = #{rentalId}
            """)
    int moveRoomSchedule(@Param("roomId") Long roomId,
                         @Param("rentalId") Long rentalId,
                         @Param("nextCollectionDate") java.time.LocalDate nextCollectionDate,
                         @Param("nextPeriodStartDate") java.time.LocalDate nextPeriodStartDate,
                         @Param("paidDate") java.time.LocalDate paidDate);

    @Update("""
            update room_rentals
            set next_collection_date = #{nextCollectionDate},
                next_period_start_date = #{nextPeriodStartDate},
                updated_at = now()
            where id = #{rentalId}
              and room_id = #{roomId}
              and status = 'ACTIVE'
            """)
    int moveRentalSchedule(@Param("roomId") Long roomId,
                           @Param("rentalId") Long rentalId,
                           @Param("nextCollectionDate") java.time.LocalDate nextCollectionDate,
                           @Param("nextPeriodStartDate") java.time.LocalDate nextPeriodStartDate);

    @Select("""
            select payment.id, payment.period_start, payment.period_end, payment.amount
            from rent_payments payment
            where payment.deleted = false
              and payment.rental_id = #{rentalId}
              and payment.period_end > #{moveOutDate}
            order by payment.period_start, payment.id
            """)
    List<Map<String, Object>> listRefundablePayments(@Param("rentalId") Long rentalId,
                                                     @Param("moveOutDate") java.time.LocalDate moveOutDate);

    @Insert("""
            insert into rent_settlements(
                rental_id, room_id, settlement_date, move_out_date, reason,
                rent_refund_amount, deposit_amount, deposit_deduction_amount,
                deposit_refund_amount, total_refund_amount, notes
            )
            values(
                #{rentalId}, #{roomId}, #{settlementDate}, #{moveOutDate}, #{reason},
                #{rentRefundAmount}, #{depositAmount}, #{depositDeductionAmount},
                #{depositRefundAmount}, #{totalRefundAmount}, #{notes}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createSettlement(RentSettlementRecord settlement);

    @Update("""
            update room_rentals
            set status = 'ENDED',
                actual_end_date = #{moveOutDate},
                next_collection_date = null,
                next_period_start_date = null,
                ended_at = now(),
                updated_at = now()
            where id = #{rentalId}
              and room_id = #{roomId}
              and status = 'ACTIVE'
            """)
    int endRental(@Param("roomId") Long roomId,
                  @Param("rentalId") Long rentalId,
                  @Param("moveOutDate") java.time.LocalDate moveOutDate);

    @Update("""
            update rooms
            set status = 'VACANT',
                current_rental_id = null,
                next_due_date = null,
                next_period_start_date = null,
                collection_day = null,
                last_paid_date = null,
                lease_start_date = null,
                lease_end_date = null,
                updated_at = now()
            where id = #{roomId}
              and deleted = false
              and current_rental_id = #{rentalId}
            """)
    int settleRoomToVacant(@Param("roomId") Long roomId, @Param("rentalId") Long rentalId);

    @Update("update properties set deleted = true, updated_at = now() where id = #{id} and deleted = false")
    int deleteProperty(@Param("id") Long id);

    @Update("update rooms set deleted = true, updated_at = now() where property_id = #{propertyId} and deleted = false")
    int deleteRoomsByProperty(@Param("propertyId") Long propertyId);

    @Update("update rooms set deleted = true, updated_at = now() where id = #{roomId} and deleted = false")
    int deleteRoom(@Param("roomId") Long roomId);

    @Update("""
            update room_rentals
            set status = 'ENDED',
                actual_end_date = lease_end_date,
                next_collection_date = null,
                next_period_start_date = null,
                ended_at = now(),
                updated_at = now()
            where status = 'ACTIVE'
              and lease_end_date < #{today}
            """)
    int expireEndedRentals(@Param("today") java.time.LocalDate today);

    @Update("""
            update rooms
            set status = 'VACANT',
                current_rental_id = null,
                next_due_date = null,
                next_period_start_date = null,
                collection_day = null,
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
