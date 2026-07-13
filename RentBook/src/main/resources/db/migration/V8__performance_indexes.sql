create index if not exists idx_rent_payments_recent_cursor
    on rent_payments(paid_date desc, created_at desc, id desc)
    where deleted = false;

create index if not exists idx_rent_payments_room_active
    on rent_payments(room_id, paid_date desc, id desc)
    where deleted = false and room_id is not null;

create index if not exists idx_rooms_property_active
    on rooms(property_id, room_no)
    where deleted = false;

create index if not exists idx_rooms_due_active
    on rooms(status, next_due_date)
    where deleted = false;

create index if not exists idx_properties_active_created
    on properties(created_at desc, id desc)
    where deleted = false;
