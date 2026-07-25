create table if not exists operation_logs (
    id bigserial primary key,
    trace_id varchar(64),
    idempotency_key varchar(128),
    module varchar(80) not null,
    action varchar(120) not null,
    http_method varchar(12) not null,
    request_path varchar(500) not null,
    request_payload jsonb,
    response_payload jsonb,
    status varchar(20) not null,
    error_message varchar(1000),
    duration_ms bigint not null default 0,
    client_ip varchar(80),
    user_agent varchar(500),
    created_at timestamptz not null default now(),
    constraint ck_operation_logs_status check (status in ('SUCCESS', 'FAILED', 'REPLAYED'))
);

create index if not exists idx_operation_logs_created
    on operation_logs(created_at desc, id desc);

create index if not exists idx_operation_logs_module_status
    on operation_logs(module, status, created_at desc);

comment on table operation_logs is '用户写操作审计日志';
comment on column operation_logs.trace_id is '请求链路ID';
comment on column operation_logs.idempotency_key is '幂等键摘要';
comment on column operation_logs.module is '业务模块';
comment on column operation_logs.action is '操作名称';
comment on column operation_logs.request_payload is '脱敏后的请求参数';
comment on column operation_logs.response_payload is '接口响应摘要';
comment on column operation_logs.status is '执行结果：SUCCESS成功，FAILED失败，REPLAYED幂等重放';

create table if not exists api_idempotency_records (
    idempotency_key varchar(128) primary key,
    request_hash varchar(64) not null,
    http_method varchar(12) not null,
    request_path varchar(500) not null,
    status varchar(20) not null,
    response_payload jsonb,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_api_idempotency_status check (status in ('PROCESSING', 'SUCCEEDED'))
);

create index if not exists idx_api_idempotency_expires
    on api_idempotency_records(expires_at);

comment on table api_idempotency_records is '写接口幂等记录，防止重复提交造成重复数据';
comment on column api_idempotency_records.idempotency_key is '客户端幂等键或短时请求指纹的SHA-256摘要';
comment on column api_idempotency_records.request_hash is '请求方法、地址和参数的SHA-256摘要';
comment on column api_idempotency_records.response_payload is '成功响应，用于安全重放';
