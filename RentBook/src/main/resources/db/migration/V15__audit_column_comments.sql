comment on column operation_logs.id is '操作日志ID';
comment on column operation_logs.http_method is 'HTTP请求方法';
comment on column operation_logs.request_path is 'HTTP请求路径';
comment on column operation_logs.error_message is '失败原因';
comment on column operation_logs.duration_ms is '操作耗时，单位毫秒';
comment on column operation_logs.client_ip is '客户端IP';
comment on column operation_logs.user_agent is '客户端浏览器标识';
comment on column operation_logs.created_at is '日志创建时间';

comment on column api_idempotency_records.http_method is 'HTTP请求方法';
comment on column api_idempotency_records.request_path is 'HTTP请求路径';
comment on column api_idempotency_records.status is '处理状态：PROCESSING处理中，SUCCEEDED已成功';
comment on column api_idempotency_records.expires_at is '幂等记录过期时间';
comment on column api_idempotency_records.created_at is '创建时间';
comment on column api_idempotency_records.updated_at is '更新时间';

comment on column data_change_logs.id is '行变更日志ID';
comment on column data_change_logs.idempotency_key is '关联写接口的幂等键摘要';
comment on column data_change_logs.table_name is '发生变更的业务表名';
comment on column data_change_logs.record_id is '发生变更的业务数据主键';
comment on column data_change_logs.operation is '数据库操作类型：INSERT、UPDATE或DELETE';
comment on column data_change_logs.created_at is '行变更时间';
