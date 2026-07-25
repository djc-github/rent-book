create index if not exists idx_rent_payments_room_period_active
    on rent_payments(room_id, period_start, period_end)
    where deleted = false and room_id is not null;

create or replace function prevent_overlapping_rent_payment()
returns trigger
language plpgsql
as $$
declare
    effective_room_id bigint;
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

    if effective_room_id is not null and exists (
        select 1
        from rent_payments pay
        left join contracts contract_record on contract_record.id = pay.contract_id
        where pay.deleted = false
          and pay.id <> coalesce(new.id, -1)
          and coalesce(pay.room_id, contract_record.room_id) = effective_room_id
          and pay.period_start <= new.period_end
          and pay.period_end >= new.period_start
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
before insert or update of room_id, contract_id, period_start, period_end, deleted
on rent_payments
for each row
execute function prevent_overlapping_rent_payment();

comment on function prevent_overlapping_rent_payment() is
    '阻止同一房间产生重叠的有效收租记录；允许先清理部署前已有的重复记录';

