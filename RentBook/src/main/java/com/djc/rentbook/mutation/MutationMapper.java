package com.djc.rentbook.mutation;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MutationMapper {
    @Select("""
            select set_config('rentbook.trace_id', coalesce(#{traceId}, ''), true) as trace_context,
                   set_config('rentbook.idempotency_key', coalesce(#{idempotencyKey}, ''), true) as idempotency_context
            """)
    Map<String, Object> setAuditContext(@Param("traceId") String traceId,
                                        @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            insert into api_idempotency_records(
                idempotency_key, request_hash, http_method, request_path,
                status, expires_at
            )
            values(
                #{idempotencyKey}, #{requestHash}, #{httpMethod}, #{requestPath},
                'PROCESSING', #{expiresAt}
            )
            on conflict (idempotency_key) do nothing
            """)
    int tryCreateIdempotency(@Param("idempotencyKey") String idempotencyKey,
                             @Param("requestHash") String requestHash,
                             @Param("httpMethod") String httpMethod,
                             @Param("requestPath") String requestPath,
                             @Param("expiresAt") OffsetDateTime expiresAt);

    @Select("""
            select idempotency_key, request_hash, status, response_payload::text as response_payload, expires_at
            from api_idempotency_records
            where idempotency_key = #{idempotencyKey}
            for update
            """)
    IdempotencyRecord findIdempotencyForUpdate(@Param("idempotencyKey") String idempotencyKey);

    @Update("""
            update api_idempotency_records
            set request_hash = #{requestHash},
                http_method = #{httpMethod},
                request_path = #{requestPath},
                status = 'PROCESSING',
                response_payload = null,
                expires_at = #{expiresAt},
                updated_at = now()
            where idempotency_key = #{idempotencyKey}
            """)
    int restartIdempotency(@Param("idempotencyKey") String idempotencyKey,
                           @Param("requestHash") String requestHash,
                           @Param("httpMethod") String httpMethod,
                           @Param("requestPath") String requestPath,
                           @Param("expiresAt") OffsetDateTime expiresAt);

    @Update("""
            update api_idempotency_records
            set status = 'SUCCEEDED',
                response_payload = cast(#{responsePayload} as jsonb),
                expires_at = #{expiresAt},
                updated_at = now()
            where idempotency_key = #{idempotencyKey}
              and status = 'PROCESSING'
            """)
    int completeIdempotency(@Param("idempotencyKey") String idempotencyKey,
                            @Param("responsePayload") String responsePayload,
                            @Param("expiresAt") OffsetDateTime expiresAt);

    @Insert("""
            insert into operation_logs(
                trace_id, idempotency_key, module, action, http_method, request_path,
                request_payload, response_payload, status, error_message,
                duration_ms, client_ip, user_agent
            )
            values(
                #{traceId}, #{idempotencyKey}, #{module}, #{action}, #{httpMethod}, #{requestPath},
                cast(#{requestPayload,jdbcType=VARCHAR} as jsonb),
                cast(#{responsePayload,jdbcType=VARCHAR} as jsonb),
                #{status}, #{errorMessage}, #{durationMs}, #{clientIp}, #{userAgent}
            )
            """)
    void insertOperationLog(@Param("traceId") String traceId,
                            @Param("idempotencyKey") String idempotencyKey,
                            @Param("module") String module,
                            @Param("action") String action,
                            @Param("httpMethod") String httpMethod,
                            @Param("requestPath") String requestPath,
                            @Param("requestPayload") String requestPayload,
                            @Param("responsePayload") String responsePayload,
                            @Param("status") String status,
                            @Param("errorMessage") String errorMessage,
                            @Param("durationMs") long durationMs,
                            @Param("clientIp") String clientIp,
                            @Param("userAgent") String userAgent);

    @Select("""
            <script>
            select id, trace_id, idempotency_key, module, action, http_method, request_path,
                   request_payload::text as request_payload,
                   response_payload::text as response_payload,
                   status, error_message,
                   duration_ms, client_ip, user_agent, created_at
            from operation_logs
            where (#{module,jdbcType=VARCHAR} is null or module = #{module})
              and (#{status,jdbcType=VARCHAR} is null or status = #{status})
              <if test="cursorId != null">
                and id &lt; #{cursorId}
              </if>
            order by id desc
            limit #{limit}
            </script>
            """)
    List<Map<String, Object>> listOperationLogs(@Param("module") String module,
                                                @Param("status") String status,
                                                @Param("cursorId") Long cursorId,
                                                @Param("limit") int limit);

    @Select("""
            select id, trace_id, idempotency_key, table_name, record_id, operation,
                   before_data::text as before_data,
                   after_data::text as after_data,
                   created_at
            from data_change_logs
            where trace_id = #{traceId}
            order by id
            """)
    List<Map<String, Object>> listDataChanges(@Param("traceId") String traceId);

    @Delete("""
            delete from api_idempotency_records
            where expires_at < now() - interval '1 day'
            """)
    int deleteExpiredIdempotencyRecords();

    @Delete("""
            delete from data_change_logs
            where created_at < #{cutoff}
            """)
    int deleteExpiredDataChangeLogs(@Param("cutoff") OffsetDateTime cutoff);

    @Delete("""
            delete from operation_logs
            where created_at < #{cutoff}
            """)
    int deleteExpiredOperationLogs(@Param("cutoff") OffsetDateTime cutoff);
}
