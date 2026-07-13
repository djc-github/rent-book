truncate table rent_payments, reminders, contracts, tenants, rooms, properties restart identity cascade;

insert into properties(name, address, district, landlord_name, landlord_phone, manager, notes)
values
('人民路 88 号阳光花园 3 栋', '人民路 88 号阳光花园 3 栋', '城东', '王先生', '13800000001', '李经理', '房态收租示例'),
('滨江大道 12 号滨江公寓 A 座', '滨江大道 12 号滨江公寓 A 座', '江北', '陈女士', '13800000002', '李经理', '房态收租示例');

insert into rooms(property_id, room_no, floor, area, rent_amount, deposit_amount, status, pay_cycle_months, next_due_date, last_paid_date, orientation, tags)
select p.id, seed.room_no, seed.floor, seed.area, seed.rent_amount, seed.deposit_amount, seed.status,
       seed.pay_cycle_months, seed.next_due_date, seed.last_paid_date, seed.orientation, seed.tags
from properties p
join (values
  ('人民路 88 号阳光花园 3 栋', '301-A', '3F', 18.5, 1800, 1800, 'RENTED', 1, current_date - 5, current_date - 35, '南', '已出租'),
  ('人民路 88 号阳光花园 3 栋', '301-B', '3F', 14.0, 1500, 1500, 'VACANT', 1, null, null, '北', '近地铁'),
  ('人民路 88 号阳光花园 3 栋', '302-A', '3F', 16.0, 1650, 1650, 'RENTED', 1, current_date + 3, current_date - 27, '南', '近期应收'),
  ('滨江大道 12 号滨江公寓 A 座', '1201', '12F', 42.0, 4200, 4200, 'VACANT', 1, null, null, '江景', '整租')
) as seed(property_address, room_no, floor, area, rent_amount, deposit_amount, status, pay_cycle_months, next_due_date, last_paid_date, orientation, tags)
on p.address = seed.property_address;

insert into rent_payments(room_id, period_start, period_end, paid_date, amount, method, notes)
select r.id, current_date - 35, current_date - 6, current_date - 35, r.rent_amount, '微信', '示例收租记录'
from rooms r
join properties p on p.id = r.property_id
where p.address = '人民路 88 号阳光花园 3 栋' and r.room_no = '301-A';
