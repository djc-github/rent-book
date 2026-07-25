create index if not exists idx_rent_payments_period_start_active
    on rent_payments(period_start)
    where deleted = false and room_id is not null;
