create table if not exists room_rentals (
    id bigserial primary key,
    room_id bigint not null references rooms(id),
    status varchar(16) not null default 'ACTIVE',
    lease_start_date date not null,
    lease_end_date date not null,
    actual_end_date date,
    rent_amount numeric(12, 2) not null,
    deposit_amount numeric(12, 2) not null default 0,
    pay_cycle_months integer not null default 1,
    next_collection_date date,
    collection_day integer,
    next_period_start_date date,
    notes varchar(1000),
    ended_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_room_rental_status check (status in ('ACTIVE', 'ENDED')),
    constraint ck_room_rental_cycle check (pay_cycle_months > 0),
    constraint ck_room_rental_collection_day check (collection_day is null or collection_day between 1 and 31),
    constraint ck_room_rental_dates check (lease_end_date >= lease_start_date)
);

create unique index if not exists uk_room_rentals_active_room
    on room_rentals(room_id)
    where status = 'ACTIVE';

create index if not exists idx_room_rentals_room_created
    on room_rentals(room_id, created_at desc);

alter table rooms add column if not exists current_rental_id bigint;
alter table rooms add column if not exists next_period_start_date date;
alter table rooms add column if not exists collection_day integer;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_rooms_current_rental'
    ) then
        alter table rooms
            add constraint fk_rooms_current_rental
            foreign key (current_rental_id) references room_rentals(id);
    end if;
end;
$$;

alter table rent_payments add column if not exists rental_id bigint;
alter table rent_payments add column if not exists due_date date;
alter table rent_payments add column if not exists cycle_months integer;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'fk_rent_payments_rental'
    ) then
        alter table rent_payments
            add constraint fk_rent_payments_rental
            foreign key (rental_id) references room_rentals(id);
    end if;
end;
$$;

create index if not exists idx_rent_payments_rental_period_active
    on rent_payments(rental_id, period_start, period_end)
    where deleted = false and rental_id is not null;

create index if not exists idx_rent_payments_due_date_active
    on rent_payments(due_date)
    where deleted = false and room_id is not null;

insert into room_rentals(
    room_id, status, lease_start_date, lease_end_date,
    rent_amount, deposit_amount, pay_cycle_months,
    next_collection_date, collection_day, next_period_start_date, notes
)
select
    room.id,
    'ACTIVE',
    coalesce(room.lease_start_date, room.next_due_date, current_date),
    greatest(
        coalesce(room.lease_end_date, current_date + interval '1 year')::date,
        coalesce(room.lease_start_date, room.next_due_date, current_date)
    ),
    room.rent_amount,
    coalesce(room.deposit_amount, 0),
    greatest(coalesce(room.pay_cycle_months, 1), 1),
    room.next_due_date,
    extract(day from room.next_due_date)::integer,
    coalesce(
        (
            select (max(payment.period_end) + interval '1 day')::date
            from rent_payments payment
            left join contracts payment_contract on payment_contract.id = payment.contract_id
            where payment.deleted = false
              and coalesce(payment.room_id, payment_contract.room_id) = room.id
        ),
        room.lease_start_date,
        room.next_due_date,
        current_date
    ),
    'V16迁移：由现有在租房间安全建立'
from rooms room
where room.deleted = false
  and room.status = 'RENTED'
  and not exists (
      select 1
      from room_rentals existing
      where existing.room_id = room.id
        and existing.status = 'ACTIVE'
  );

update rooms room
set current_rental_id = rental.id,
    next_period_start_date = rental.next_period_start_date,
    collection_day = rental.collection_day
from room_rentals rental
where rental.room_id = room.id
  and rental.status = 'ACTIVE'
  and room.status = 'RENTED'
  and room.current_rental_id is null;

update rent_payments payment
set rental_id = rental.id,
    due_date = coalesce(payment.due_date, payment.period_start),
    cycle_months = coalesce(
        payment.cycle_months,
        greatest(
            1,
            (
                extract(year from age(payment.period_end + 1, payment.period_start)) * 12
                + extract(month from age(payment.period_end + 1, payment.period_start))
            )::integer
        )
    )
from room_rentals rental
where rental.status = 'ACTIVE'
  and rental.room_id = coalesce(
      payment.room_id,
      (select contract_record.room_id from contracts contract_record where contract_record.id = payment.contract_id)
  )
  and payment.rental_id is null;

create table if not exists rent_settlements (
    id bigserial primary key,
    rental_id bigint not null references room_rentals(id),
    room_id bigint not null references rooms(id),
    settlement_date date not null,
    move_out_date date not null,
    reason varchar(32) not null,
    rent_refund_amount numeric(12, 2) not null default 0,
    deposit_amount numeric(12, 2) not null default 0,
    deposit_deduction_amount numeric(12, 2) not null default 0,
    deposit_refund_amount numeric(12, 2) not null default 0,
    total_refund_amount numeric(12, 2) not null default 0,
    notes varchar(1000),
    created_at timestamptz not null default now(),
    constraint uk_rent_settlement_rental unique (rental_id),
    constraint ck_rent_settlement_reason check (reason in ('EARLY_TERMINATION', 'NORMAL_END', 'OTHER')),
    constraint ck_rent_settlement_amounts check (
        rent_refund_amount >= 0
        and deposit_amount >= 0
        and deposit_deduction_amount >= 0
        and deposit_refund_amount >= 0
        and total_refund_amount >= 0
        and deposit_deduction_amount <= deposit_amount
    )
);

create index if not exists idx_rent_settlements_room_date
    on rent_settlements(room_id, settlement_date desc);

create index if not exists idx_rent_settlements_date
    on rent_settlements(settlement_date);

create or replace function prevent_overlapping_rent_payment()
returns trigger
language plpgsql
as $$
declare
    effective_room_id bigint;
    rental_room_id bigint;
begin
    if new.deleted then
        return new;
    end if;

    effective_room_id := new.room_id;
    if effective_room_id is null and new.contract_id is not null then
        select room_id into effective_room_id
        from contracts
        where id = new.contract_id;
    end if;

    if new.rental_id is not null then
        select room_id into rental_room_id
        from room_rentals
        where id = new.rental_id;

        if rental_room_id is null or effective_room_id is distinct from rental_room_id then
            raise exception using
                errcode = '23503',
                message = '收租记录与出租轮次对应的房间不一致';
        end if;

        if exists (
            select 1
            from rent_payments payment
            where payment.deleted = false
              and payment.id <> coalesce(new.id, -1)
              and payment.rental_id = new.rental_id
              and payment.period_start <= new.period_end
              and payment.period_end >= new.period_start
        ) then
            raise exception using
                errcode = '23505',
                constraint = 'uk_rent_payment_rental_period_no_overlap',
                message = '本轮出租对应租期已经登记过收租';
        end if;
    elsif effective_room_id is not null and exists (
        select 1
        from rent_payments payment
        left join contracts payment_contract on payment_contract.id = payment.contract_id
        where payment.deleted = false
          and payment.id <> coalesce(new.id, -1)
          and coalesce(payment.room_id, payment_contract.room_id) = effective_room_id
          and payment.period_start <= new.period_end
          and payment.period_end >= new.period_start
    ) then
        raise exception using
            errcode = '23505',
            constraint = 'uk_rent_payment_room_period_no_overlap',
            message = '该房间对应租期已经登记过收租';
    end if;

    return new;
end;
$$;

drop trigger if exists trg_prevent_overlapping_rent_payment on rent_payments;

create trigger trg_prevent_overlapping_rent_payment
before insert or update of room_id, contract_id, rental_id, period_start, period_end, deleted
on rent_payments
for each row
execute function prevent_overlapping_rent_payment();

drop trigger if exists trg_audit_room_rentals on room_rentals;
create trigger trg_audit_room_rentals
after insert or update or delete on room_rentals
for each row execute function audit_business_row_change();

drop trigger if exists trg_audit_rent_settlements on rent_settlements;
create trigger trg_audit_rent_settlements
after insert or update or delete on rent_settlements
for each row execute function audit_business_row_change();

comment on table room_rentals is '匿名出租轮次；不保存租客信息，用于隔离同一房间不同出租阶段';
comment on column room_rentals.next_collection_date is '下次计划收租日，用于提醒和应收统计';
comment on column room_rentals.next_period_start_date is '下一笔租金覆盖期开始日';
comment on column room_rentals.collection_day is '计划收租日的月内日号，用于跨月稳定推进';
comment on column rooms.current_rental_id is '当前生效的匿名出租轮次ID';
comment on column rooms.next_period_start_date is '下一笔租金覆盖期开始日，与计划收租日分离';
comment on column rooms.collection_day is '当前出租轮次计划收租日的月内日号';
comment on column rent_payments.rental_id is '所属匿名出租轮次ID';
comment on column rent_payments.due_date is '本笔租金原计划收租日';
comment on column rent_payments.cycle_months is '本笔收租覆盖的月数';
comment on table rent_settlements is '退租结算记录，保留租金退款和押金扣退明细';
comment on column rent_settlements.rent_refund_amount is '退还的剩余租金';
comment on column rent_settlements.deposit_deduction_amount is '从押金中扣除的金额';
comment on column rent_settlements.deposit_refund_amount is '实际退还押金';
comment on column rent_settlements.total_refund_amount is '实际退款合计：剩余租金退款加押金退款';
