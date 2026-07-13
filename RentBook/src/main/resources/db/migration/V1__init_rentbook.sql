create table if not exists properties (
    id bigserial primary key,
    name varchar(120) not null,
    address varchar(300) not null,
    district varchar(80),
    landlord_name varchar(80),
    landlord_phone varchar(40),
    manager varchar(80),
    notes text,
    deleted boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists rooms (
    id bigserial primary key,
    property_id bigint not null references properties(id),
    room_no varchar(80) not null,
    floor varchar(40),
    area numeric(10, 2),
    rent_amount numeric(12, 2) not null default 0,
    deposit_amount numeric(12, 2) not null default 0,
    status varchar(30) not null default 'VACANT',
    orientation varchar(40),
    tags varchar(300),
    notes text,
    deleted boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_rooms_property_room unique(property_id, room_no),
    constraint ck_rooms_status check(status in ('VACANT', 'RESERVED', 'RENTED', 'MAINTENANCE', 'OFFLINE'))
);

create table if not exists tenants (
    id bigserial primary key,
    name varchar(80) not null,
    phone varchar(40) not null,
    id_card varchar(80),
    emergency_contact varchar(80),
    emergency_phone varchar(40),
    source varchar(80),
    notes text,
    deleted boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists contracts (
    id bigserial primary key,
    room_id bigint not null references rooms(id),
    tenant_id bigint not null references tenants(id),
    contract_no varchar(80),
    start_date date not null,
    end_date date not null,
    rent_amount numeric(12, 2) not null,
    deposit_amount numeric(12, 2) not null default 0,
    pay_cycle_months int not null default 1,
    next_due_date date not null,
    status varchar(30) not null default 'ACTIVE',
    notes text,
    deleted boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_contract_dates check(end_date > start_date),
    constraint ck_contract_status check(status in ('ACTIVE', 'ENDED', 'CANCELLED'))
);

create unique index if not exists uk_active_contract_room
    on contracts(room_id)
    where status = 'ACTIVE' and deleted = false;

create table if not exists rent_payments (
    id bigserial primary key,
    contract_id bigint not null references contracts(id),
    period_start date not null,
    period_end date not null,
    paid_date date not null,
    amount numeric(12, 2) not null,
    method varchar(40),
    receipt_no varchar(80),
    notes text,
    created_at timestamptz not null default now(),
    constraint ck_payment_period check(period_end >= period_start)
);

create table if not exists reminders (
    id bigserial primary key,
    biz_type varchar(40) not null,
    biz_id bigint not null,
    title varchar(160) not null,
    remind_date date not null,
    status varchar(30) not null default 'OPEN',
    notes text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_reminder_status check(status in ('OPEN', 'DONE', 'IGNORED'))
);

create index if not exists idx_rooms_status on rooms(status);
create index if not exists idx_contract_due on contracts(status, next_due_date);
create index if not exists idx_contract_end on contracts(status, end_date);
create index if not exists idx_payment_paid_date on rent_payments(paid_date);
create index if not exists idx_tenants_phone on tenants(phone);

insert into properties(name, address, district, landlord_name, landlord_phone, manager, notes)
values
('阳光花园 3 栋', '人民路 88 号阳光花园 3 栋', '城东', '王先生', '13800000001', '李经理', '示例房源，可删除'),
('滨江公寓 A 座', '滨江大道 12 号 A 座', '江北', '陈女士', '13800000002', '李经理', '靠近地铁')
on conflict do nothing;

insert into rooms(property_id, room_no, floor, area, rent_amount, deposit_amount, status, orientation, tags)
select p.id, room_no, floor, area, rent_amount, deposit_amount, status, orientation, tags
from properties p
join (values
  ('阳光花园 3 栋', '301-A', '3F', 18.5, 1800, 1800, 'RENTED', '南', '独卫,采光好'),
  ('阳光花园 3 栋', '301-B', '3F', 14.0, 1500, 1500, 'VACANT', '北', '近地铁'),
  ('阳光花园 3 栋', '302-A', '3F', 16.0, 1650, 1650, 'RESERVED', '南', '带阳台'),
  ('滨江公寓 A 座', '1201', '12F', 42.0, 4200, 4200, 'VACANT', '江景', '整租,可做饭')
) as seed(property_name, room_no, floor, area, rent_amount, deposit_amount, status, orientation, tags)
on p.name = seed.property_name
on conflict do nothing;
