alter table rooms add column if not exists lease_start_date date;
alter table rooms add column if not exists lease_end_date date;

create index if not exists idx_rooms_rent_expire
    on rooms(status, lease_end_date)
    where deleted = false and status = 'RENTED';

comment on column rooms.lease_start_date is '房间当前租期开始日期，简化收租模式下使用';
comment on column rooms.lease_end_date is '房间当前租期结束日期，简化收租模式下使用';
