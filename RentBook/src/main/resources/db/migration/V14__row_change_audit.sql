create table if not exists data_change_logs (
    id bigserial primary key,
    trace_id varchar(64),
    idempotency_key varchar(128),
    table_name varchar(80) not null,
    record_id varchar(100),
    operation varchar(12) not null,
    before_data jsonb,
    after_data jsonb,
    created_at timestamptz not null default now(),
    constraint ck_data_change_operation check (operation in ('INSERT', 'UPDATE', 'DELETE'))
);

create index if not exists idx_data_change_logs_trace
    on data_change_logs(trace_id, id);

create index if not exists idx_data_change_logs_record
    on data_change_logs(table_name, record_id, created_at desc);

comment on table data_change_logs is '业务表行级变更审计，保存修改前后完整数据';
comment on column data_change_logs.trace_id is '关联operation_logs.trace_id';
comment on column data_change_logs.before_data is '修改或删除前的数据';
comment on column data_change_logs.after_data is '新增或修改后的数据';

create or replace function audit_business_row_change()
returns trigger
language plpgsql
as $$
declare
    old_json jsonb;
    new_json jsonb;
    effective_id text;
begin
    old_json := case when tg_op in ('UPDATE', 'DELETE') then to_jsonb(old) else null end;
    new_json := case when tg_op in ('INSERT', 'UPDATE') then to_jsonb(new) else null end;
    effective_id := coalesce(new_json ->> 'id', old_json ->> 'id');

    insert into data_change_logs(
        trace_id, idempotency_key, table_name, record_id,
        operation, before_data, after_data
    )
    values(
        nullif(current_setting('rentbook.trace_id', true), ''),
        nullif(current_setting('rentbook.idempotency_key', true), ''),
        tg_table_name,
        effective_id,
        tg_op,
        old_json,
        new_json
    );

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

do $$
declare
    table_to_audit text;
begin
    foreach table_to_audit in array array[
        'properties', 'rooms', 'tenants', 'contracts',
        'rent_payments', 'reminders', 'room_images'
    ]
    loop
        execute format('drop trigger if exists trg_audit_%I on %I', table_to_audit, table_to_audit);
        execute format(
            'create trigger trg_audit_%I after insert or update or delete on %I '
            'for each row execute function audit_business_row_change()',
            table_to_audit,
            table_to_audit
        );
    end loop;
end;
$$;
