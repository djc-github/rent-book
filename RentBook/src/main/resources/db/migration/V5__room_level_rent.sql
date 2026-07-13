alter table rooms add column if not exists pay_cycle_months int not null default 1;
alter table rooms add column if not exists next_due_date date;
alter table rooms add column if not exists last_paid_date date;

alter table rent_payments alter column contract_id drop not null;
alter table rent_payments add column if not exists room_id bigint references rooms(id);

comment on column rooms.pay_cycle_months is '房间付款周期，单位月';
comment on column rooms.next_due_date is '房间下次应收租日期';
comment on column rooms.last_paid_date is '房间最近收租日期';
comment on column rent_payments.room_id is '房间ID，简化收租模式下使用';
