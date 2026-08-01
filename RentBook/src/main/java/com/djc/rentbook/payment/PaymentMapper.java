package com.djc.rentbook.payment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PaymentMapper {
    @Select("""
            select pay.*,
                   coalesce(nullif(p.address, ''), p.name) as property_name,
                   r.room_no
            from rent_payments pay
            left join contracts c on c.id = pay.contract_id
            join rooms r on r.id = coalesce(pay.room_id, c.room_id)
            join properties p on p.id = r.property_id
            where pay.paid_date >= coalesce(#{from,jdbcType=DATE}, date '1900-01-01')
              and pay.paid_date <= coalesce(#{to,jdbcType=DATE}, date '2999-12-31')
              and pay.deleted = false
            order by pay.paid_date desc, pay.created_at desc
            """)
    List<Map<String, Object>> list(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("""
            <script>
            with filtered as (
                select pay.*,
                       coalesce(nullif(p.address, ''), p.name) as property_name,
                       r.room_no,
                       p.id as property_id
                from rent_payments pay
                left join contracts c on c.id = pay.contract_id
                join rooms r on r.id = coalesce(pay.room_id, c.room_id)
                join properties p on p.id = r.property_id
                where pay.paid_date >= coalesce(#{from,jdbcType=DATE}, date '1900-01-01')
                  and pay.paid_date &lt;= coalesce(#{to,jdbcType=DATE}, date '2999-12-31')
                  and pay.deleted = false
                  <if test="propertyId != null">
                  and p.id = #{propertyId}
                  </if>
                  <if test="minAmount != null">
                  and pay.amount >= #{minAmount}
                  </if>
                  <if test="maxAmount != null">
                  and pay.amount &lt;= #{maxAmount}
                  </if>
            ), payment_rows as (
                select filtered.*,
                       count(*) over (partition by paid_date) as daily_count,
                       sum(amount) over (partition by paid_date) as daily_amount
                from filtered
            )
            select *
            from payment_rows
            where 1 = 1
              <if test="cursorPaidDate != null and cursorCreatedAt != null and cursorId != null">
              and (paid_date, created_at, id) &lt; (#{cursorPaidDate}, #{cursorCreatedAt}, #{cursorId})
              </if>
            order by paid_date desc, created_at desc, id desc
            limit #{limit}
            </script>
            """)
    List<Map<String, Object>> listPage(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("propertyId") Long propertyId,
                                       @Param("minAmount") BigDecimal minAmount,
                                       @Param("maxAmount") BigDecimal maxAmount,
                                       @Param("cursorPaidDate") LocalDate cursorPaidDate,
                                       @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                       @Param("cursorId") Long cursorId,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            select count(*) as total_count,
                   coalesce(sum(pay.amount), 0) as total_amount
            from rent_payments pay
            left join contracts c on c.id = pay.contract_id
            join rooms r on r.id = coalesce(pay.room_id, c.room_id)
            join properties p on p.id = r.property_id
            where pay.paid_date >= coalesce(#{from,jdbcType=DATE}, date '1900-01-01')
              and pay.paid_date &lt;= coalesce(#{to,jdbcType=DATE}, date '2999-12-31')
              and pay.deleted = false
              <if test="propertyId != null">
              and p.id = #{propertyId}
              </if>
              <if test="minAmount != null">
              and pay.amount >= #{minAmount}
              </if>
              <if test="maxAmount != null">
              and pay.amount &lt;= #{maxAmount}
              </if>
            </script>
            """)
    Map<String, Object> summarize(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("propertyId") Long propertyId,
                                  @Param("minAmount") BigDecimal minAmount,
                                  @Param("maxAmount") BigDecimal maxAmount);

    @Select("""
            <script>
            select coalesce(sum(r.rent_amount * greatest(r.pay_cycle_months, 1)), 0)
            from rooms r
            where r.deleted = false
              and r.status = 'RENTED'
              and r.next_due_date is not null
              and r.next_due_date >= coalesce(#{from,jdbcType=DATE}, date '1900-01-01')
              and r.next_due_date &lt;= coalesce(#{to,jdbcType=DATE}, date '2999-12-31')
              <if test="propertyId != null">
              and r.property_id = #{propertyId}
              </if>
            </script>
            """)
    BigDecimal summarizeOutstanding(@Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    @Param("propertyId") Long propertyId);

    @Select("select * from rent_payments where id = #{id} and deleted = false")
    PaymentRecord find(@Param("id") Long id);

    @Select("select * from rent_payments where id = #{id} and deleted = false for update")
    PaymentRecord findForUpdate(@Param("id") Long id);

    @Select("""
            select exists(
                select 1
                from rent_payments
                where rental_id = #{rentalId}
                  and deleted = false
                  and id <> #{paymentId}
                  and period_end > #{periodEnd}
            )
            """)
    boolean hasLaterRentalPayment(@Param("rentalId") Long rentalId,
                                  @Param("paymentId") Long paymentId,
                                  @Param("periodEnd") LocalDate periodEnd);

    @Select("""
            select c.room_id
            from contracts c
            join rooms r on r.id = c.room_id
            where c.id = #{contractId}
              and c.deleted = false
            for update of c, r
            """)
    Long findContractRoomIdForUpdate(@Param("contractId") Long contractId);

    @Select("select id from rooms where id = #{roomId} and deleted = false for update")
    Long lockRoom(@Param("roomId") Long roomId);

    @Select("""
            select pay.id
            from rent_payments pay
            left join contracts c on c.id = pay.contract_id
            where pay.deleted = false
              and coalesce(pay.room_id, c.room_id) = #{roomId}
              and (#{rentalId,jdbcType=BIGINT} is null or pay.rental_id = #{rentalId})
              and pay.period_start <= #{periodEnd}
              and pay.period_end >= #{periodStart}
              and (#{excludePaymentId,jdbcType=BIGINT} is null or pay.id <> #{excludePaymentId})
            order by pay.created_at desc, pay.id desc
            limit 1
            """)
    Long findOverlappingPaymentId(@Param("roomId") Long roomId,
                                  @Param("rentalId") Long rentalId,
                                  @Param("periodStart") LocalDate periodStart,
                                  @Param("periodEnd") LocalDate periodEnd,
                                  @Param("excludePaymentId") Long excludePaymentId);

    @Select("""
            select max(pay.period_end)
            from rent_payments pay
            left join contracts c on c.id = pay.contract_id
            where pay.deleted = false
              and coalesce(pay.room_id, c.room_id) = #{roomId}
              and (#{rentalId,jdbcType=BIGINT} is null or pay.rental_id = #{rentalId})
            """)
    LocalDate findLatestCoveredDate(@Param("roomId") Long roomId,
                                    @Param("rentalId") Long rentalId);

    @Insert("""
            insert into rent_payments(contract_id, period_start, period_end, paid_date, amount, method, receipt_no, notes)
            values(#{contractId}, #{periodStart}, #{periodEnd}, #{paidDate}, #{amount}, #{method}, #{receiptNo}, #{notes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(PaymentRecord payment);

    @Insert("""
            insert into rent_payments(
                room_id, rental_id, due_date, cycle_months,
                period_start, period_end, paid_date, amount, method, receipt_no, notes
            )
            values(
                #{roomId}, #{rentalId}, #{dueDate}, #{cycleMonths},
                #{periodStart}, #{periodEnd}, #{paidDate}, #{amount}, #{method}, #{receiptNo}, #{notes}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createRoomPayment(PaymentRecord payment);

    @Update("""
            update contracts
            set next_due_date = greatest(next_due_date, cast(#{periodEnd} as date) + interval '1 day')::date,
                updated_at = now()
            where id = #{contractId}
            """)
    void moveNextDueDate(PaymentRecord payment);

    @Update("""
            update contracts
            set next_due_date = case
                    when next_due_date > (cast(#{periodEnd} as date) + interval '1 day')::date
                        then greatest(
                            next_due_date,
                            coalesce(
                                (select (max(period_end) + interval '1 day')::date
                                   from rent_payments
                                  where contract_id = #{contractId}
                                    and deleted = false),
                                cast(#{periodStart} as date)
                            )
                        )
                    else greatest(
                        cast(#{periodStart} as date),
                        coalesce(
                            (select (max(period_end) + interval '1 day')::date
                               from rent_payments
                              where contract_id = #{contractId}
                                and deleted = false),
                            cast(#{periodStart} as date)
                        )
                    )
                end,
                updated_at = now()
            where id = #{contractId} and #{contractId,jdbcType=BIGINT} is not null
            """)
    void rollbackNextDueDate(PaymentRecord payment);

    @Update("""
            update rooms
            set next_due_date = case
                    when next_due_date > (cast(#{periodEnd} as date) + interval '1 day')::date
                        then greatest(
                            next_due_date,
                            coalesce(
                                (select (max(pay.period_end) + interval '1 day')::date
                                   from rent_payments pay
                                   left join contracts c on c.id = pay.contract_id
                                  where coalesce(pay.room_id, c.room_id) = #{roomId}
                                    and pay.deleted = false
                                    and pay.id <> #{id}),
                                cast(#{periodStart} as date)
                            )
                        )
                    else greatest(
                        cast(#{periodStart} as date),
                        coalesce(
                            (select (max(pay.period_end) + interval '1 day')::date
                               from rent_payments pay
                               left join contracts c on c.id = pay.contract_id
                              where coalesce(pay.room_id, c.room_id) = #{roomId}
                                and pay.deleted = false
                                and pay.id <> #{id}),
                            cast(#{periodStart} as date)
                        )
                    )
                end,
                last_paid_date = (
                    select max(pay.paid_date)
                    from rent_payments pay
                    left join contracts c on c.id = pay.contract_id
                    where coalesce(pay.room_id, c.room_id) = #{roomId}
                      and pay.deleted = false
                      and pay.id <> #{id}
                ),
                updated_at = now()
            where id = #{roomId} and #{roomId,jdbcType=BIGINT} is not null
            """)
    void rollbackRoomNextDueDate(PaymentRecord payment);

    @Update("""
            update rooms
            set next_due_date = case
                    when current_rental_id = #{rentalId}
                     and next_period_start_date = (cast(#{periodEnd} as date) + interval '1 day')::date
                        then coalesce(#{dueDate,jdbcType=DATE}, #{periodStart})
                    else next_due_date
                end,
                next_period_start_date = case
                    when current_rental_id = #{rentalId}
                     and next_period_start_date = (cast(#{periodEnd} as date) + interval '1 day')::date
                        then #{periodStart}
                    else next_period_start_date
                end,
                last_paid_date = (
                    select max(pay.paid_date)
                    from rent_payments pay
                    where pay.rental_id = #{rentalId}
                      and pay.deleted = false
                      and pay.id <> #{id}
                ),
                updated_at = now()
            where id = #{roomId}
              and #{roomId,jdbcType=BIGINT} is not null
              and current_rental_id = #{rentalId}
            """)
    int rollbackCurrentRentalSchedule(PaymentRecord payment);

    @Update("""
            update room_rentals rental
            set next_collection_date = room.next_due_date,
                next_period_start_date = room.next_period_start_date,
                updated_at = now()
            from rooms room
            where rental.id = #{rentalId}
              and rental.room_id = #{roomId}
              and rental.status = 'ACTIVE'
              and room.id = rental.room_id
              and room.current_rental_id = rental.id
            """)
    int syncRentalScheduleFromRoom(PaymentRecord payment);

    @Update("update rent_payments set deleted = true where id = #{id} and deleted = false")
    int delete(@Param("id") Long id);
}
