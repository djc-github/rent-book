package com.djc.rentbook.payment;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.time.LocalDate;
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
            select pay.*,
                   coalesce(nullif(p.address, ''), p.name) as property_name,
                   r.room_no
            from rent_payments pay
            left join contracts c on c.id = pay.contract_id
            join rooms r on r.id = coalesce(pay.room_id, c.room_id)
            join properties p on p.id = r.property_id
            where pay.paid_date >= coalesce(#{from,jdbcType=DATE}, date '1900-01-01')
              and pay.paid_date &lt;= coalesce(#{to,jdbcType=DATE}, date '2999-12-31')
              and pay.deleted = false
              <if test="cursorPaidDate != null and cursorCreatedAt != null and cursorId != null">
              and (pay.paid_date, pay.created_at, pay.id) &lt; (#{cursorPaidDate}, #{cursorCreatedAt}, #{cursorId})
              </if>
            order by pay.paid_date desc, pay.created_at desc, pay.id desc
            limit #{limit}
            </script>
            """)
    List<Map<String, Object>> listPage(@Param("from") LocalDate from,
                                       @Param("to") LocalDate to,
                                       @Param("cursorPaidDate") LocalDate cursorPaidDate,
                                       @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
                                       @Param("cursorId") Long cursorId,
                                       @Param("limit") int limit);

    @Select("select * from rent_payments where id = #{id} and deleted = false")
    PaymentRecord find(@Param("id") Long id);

    @Select("select * from rent_payments where id = #{id} and deleted = false for update")
    PaymentRecord findForUpdate(@Param("id") Long id);

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
              and pay.period_start <= #{periodEnd}
              and pay.period_end >= #{periodStart}
              and (#{excludePaymentId,jdbcType=BIGINT} is null or pay.id <> #{excludePaymentId})
            order by pay.created_at desc, pay.id desc
            limit 1
            """)
    Long findOverlappingPaymentId(@Param("roomId") Long roomId,
                                  @Param("periodStart") LocalDate periodStart,
                                  @Param("periodEnd") LocalDate periodEnd,
                                  @Param("excludePaymentId") Long excludePaymentId);

    @Select("""
            select max(pay.period_end)
            from rent_payments pay
            left join contracts c on c.id = pay.contract_id
            where pay.deleted = false
              and coalesce(pay.room_id, c.room_id) = #{roomId}
            """)
    LocalDate findLatestCoveredDate(@Param("roomId") Long roomId);

    @Insert("""
            insert into rent_payments(contract_id, period_start, period_end, paid_date, amount, method, receipt_no, notes)
            values(#{contractId}, #{periodStart}, #{periodEnd}, #{paidDate}, #{amount}, #{method}, #{receiptNo}, #{notes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void create(PaymentRecord payment);

    @Insert("""
            insert into rent_payments(room_id, period_start, period_end, paid_date, amount, method, receipt_no, notes)
            values(#{roomId}, #{periodStart}, #{periodEnd}, #{paidDate}, #{amount}, #{method}, #{receiptNo}, #{notes})
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

    @Update("update rent_payments set deleted = true where id = #{id} and deleted = false")
    int delete(@Param("id") Long id);
}
