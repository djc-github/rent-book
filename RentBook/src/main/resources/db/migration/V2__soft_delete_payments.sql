alter table rent_payments add column if not exists deleted boolean not null default false;
