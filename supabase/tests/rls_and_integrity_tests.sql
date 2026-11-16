-- =====================================================================
-- Database security & integrity tests. Every check raises an exception on failure
-- (psql runs with ON_ERROR_STOP), so a clean run means everything passed.
-- =====================================================================
\set QUIET on
set client_min_messages = notice;

create schema tests;
grant usage on schema tests to anon, authenticated;

create function tests.ok(label text) returns void language plpgsql as $$
begin raise notice 'PASS  %', label; end $$;

-- statement must be rejected with SQLSTATE `code` (42501 = permission denied / RLS violation)
create function tests.expect_error(stmt text, code text, label text) returns void language plpgsql as $$
begin
  begin
    execute stmt;
  exception when others then
    if sqlstate = code then perform tests.ok(label); return; end if;
    raise exception 'FAIL  % — expected SQLSTATE %, got % (%)', label, code, sqlstate, sqlerrm;
  end;
  raise exception 'FAIL  % — statement unexpectedly succeeded: %', label, stmt;
end $$;

create function tests.expect_count(query text, expected bigint, label text) returns void language plpgsql as $$
declare n bigint;
begin
  execute format('select count(*) from (%s) q', query) into n;
  if n is distinct from expected then
    raise exception 'FAIL  % — expected % rows, got %', label, expected, n;
  end if;
  perform tests.ok(label);
end $$;

create function tests.expect_true(cond boolean, label text) returns void language plpgsql as $$
begin
  if cond is not true then raise exception 'FAIL  %', label; end if;
  perform tests.ok(label);
end $$;

create function tests.as_user(uid uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, false);
end $$;
grant execute on all functions in schema tests to anon, authenticated;

-- ---------------------------------------------------------------------
-- Fixtures (created as the database owner, like Supabase Auth would)
-- ---------------------------------------------------------------------
-- A tries to self-assign "admin" via sign-up metadata: must be ignored.
insert into auth.users (id, phone, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', '994501112233', '{"first_name":"Aysel","last_name":"Məmmədova","role":"admin"}'),
  ('00000000-0000-0000-0000-00000000000b', '994502223344', '{"first_name":"Bəhruz","last_name":"Əliyev"}'),
  ('00000000-0000-0000-0000-00000000000c', '994503334455', '{"first_name":"Kamran","last_name":"Kuryer"}');
insert into auth.users (id, email, raw_user_meta_data) values
  ('00000000-0000-0000-0000-0000000000ad', 'owner@market.test', '{"first_name":"Sahib"}');

-- 1. Sign-up trigger -----------------------------------------------------
select tests.expect_count($$select 1 from public.profiles$$, 4, 'signup creates a profile for every auth user');
select tests.expect_count($$select 1 from public.user_roles where role = 'customer'$$, 4, 'every new user starts as customer (metadata role ignored)');
select tests.expect_count($$select 1 from public.carts$$, 4, 'signup creates a cart');
select tests.expect_count($$select 1 from public.bonus_cards$$, 4, 'signup creates a bonus card');
select tests.expect_true((select phone from public.profiles where id = '00000000-0000-0000-0000-00000000000a') = '+994501112233',
                         'phone normalised to E.164 with +');
select tests.expect_true(
  (select bool_and(card_number ~ '^7700[0-9]{12}$'
       and public.luhn_check_digit(left(card_number, 15)) = right(card_number, 1)::int) from public.bonus_cards),
  'bonus card numbers are 16 digits with a valid Luhn check digit');
select tests.expect_count($$select distinct qr_token from public.bonus_cards$$, 4, 'bonus QR tokens are unique');

-- 2. Seed ---------------------------------------------------------------
select tests.expect_count($$select 1 from public.categories$$, 12, 'seed: 12 initial categories');
select tests.expect_count($$select 1 from public.app_settings$$, 1, 'seed: single app_settings row');
select tests.expect_error($$insert into public.app_settings (id) values (2)$$, '23514', 'app_settings is a singleton');

-- 3. First admin bootstrap ----------------------------------------------
select public.bootstrap_first_admin('owner@market.test');
select tests.expect_true((select role from public.user_roles where user_id = '00000000-0000-0000-0000-0000000000ad') = 'admin',
                         'bootstrap_first_admin grants admin');
select tests.expect_error($$select public.bootstrap_first_admin('owner@market.test')$$, 'P0001',
                          'bootstrap refuses when an admin already exists');
-- Simulates the server-side courier creation (Stage 10 function).
update public.user_roles set role = 'courier' where user_id = '00000000-0000-0000-0000-00000000000c';
insert into public.couriers (id) values ('00000000-0000-0000-0000-00000000000c');
select tests.expect_count($$select 1 from public.bonus_cards$$, 2, 'promoted staff accounts lose customer-only data (cart, bonus card)');
select tests.expect_count($$select 1 from public.customers$$, 2, 'staff accounts are no longer customers');
select tests.expect_error($$update public.user_roles set role = 'customer' where user_id = '00000000-0000-0000-0000-0000000000ad'$$,
                          '42501', 'the last admin can never be removed');

-- Catalogue + an order created the way the server will (as owner).
insert into public.products (id, category_id, name, barcode, sku, unit, price, sale_price)
values ('11111111-1111-1111-1111-111111111111', 2, 'Süd 1L', '4760000000011', 'SUD-001', 'liter', 2.40, 1.99),
       ('22222222-2222-2222-2222-222222222222', 1, 'Gizli məhsul', '4760000000028', 'HID-001', 'piece', 5.00, null);
update public.products set is_active = false where id = '22222222-2222-2222-2222-222222222222';
insert into public.product_costs values ('11111111-1111-1111-1111-111111111111', 1.50);
update public.inventory set quantity = 10 where product_id = '11111111-1111-1111-1111-111111111111';

insert into public.orders (id, customer_id, delivery_method, customer_name, contact_phone, address_snapshot,
                           subtotal, discount_total, bonus_used, delivery_fee, total, payment_method, idempotency_key, courier_id)
values ('aaaaaaaa-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000000a', 'delivery', 'Aysel Məmmədova',
        '+994501112233', '{"address_line":"Mərkəz küç. 1"}', 4.80, 0.82, 0, 1.00, 4.98, 'cash_on_delivery',
        gen_random_uuid(), '00000000-0000-0000-0000-00000000000c');
insert into public.order_items (order_id, product_id, product_name, unit, quantity, unit_price, final_unit_price, line_total)
values ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'Süd 1L', 'liter', 2, 2.40, 1.99, 3.98);
insert into public.notifications (user_id, type, title) values
  (null, 'promotion', 'Hamıya endirim'),
  ('00000000-0000-0000-0000-00000000000a', 'order_status', 'Sifarişiniz qəbul edildi');
insert into storage.objects (bucket_id, name) values ('product-images', 'sud.jpg');

select id as a_cart from public.carts where customer_id = '00000000-0000-0000-0000-00000000000a' \gset

-- 4. Integrity -----------------------------------------------------------
select tests.expect_true((select order_number from public.orders) ~ '^MK-[0-9]{6}-[0-9]{6}$', 'unique order number format MK-YYMMDD-NNNNNN');
select tests.expect_count($$select 1 from public.order_status_history$$, 1, 'order status history recorded on insert');
select tests.expect_count($$select 1 from public.inventory$$, 2, 'every product automatically gets a stock row');
select tests.expect_error($$update public.inventory set quantity = -1 where product_id = '11111111-1111-1111-1111-111111111111'$$,
                          '23514', 'stock can never be negative');
select tests.expect_error($$update public.bonus_cards set balance = -5$$, '23514', 'bonus balance can never be negative');
select tests.expect_error($$update public.orders set total = 999$$, '23514', 'order total must equal subtotal - discount - bonus + fee');
select tests.expect_error($$update public.products set sale_price = 3.00 where id = '11111111-1111-1111-1111-111111111111'$$,
                          '23514', 'sale price must be below regular price');
select tests.expect_error(
  $$insert into public.orders (customer_id, delivery_method, customer_name, contact_phone, subtotal, total, payment_method, idempotency_key)
    select customer_id, 'pickup', customer_name, contact_phone, 1, 1, 'pay_at_pickup', idempotency_key from public.orders$$,
  '23505', 'retrying an order with the same idempotency key cannot create a duplicate');
select tests.expect_error(
  $$update public.orders set delivery_method = 'pickup', delivery_fee = 0, total = 3.98$$,
  '23514', 'pickup orders cannot have a courier');
insert into public.bonus_transactions (card_id, order_id, type, amount, balance_after, idempotency_key)
select id, 'aaaaaaaa-0000-0000-0000-000000000001', 'earn', 0.49, 0.49, 'earn:aaaaaaaa-0000-0000-0000-000000000001'
from public.bonus_cards where customer_id = '00000000-0000-0000-0000-00000000000a';
select tests.expect_error(
  $$insert into public.bonus_transactions (card_id, order_id, type, amount, balance_after, idempotency_key)
    select id, 'aaaaaaaa-0000-0000-0000-000000000001', 'earn', 0.49, 0.98, 'earn-again' from public.bonus_cards
    where customer_id = '00000000-0000-0000-0000-00000000000a'$$,
  '23505', 'bonus cannot be earned twice for the same order');
select tests.expect_error(
  $$insert into public.payment_transactions (order_id, method, amount, status) values
    ('aaaaaaaa-0000-0000-0000-000000000001', 'cash_on_delivery', 4.98, 'paid')$$,
  '23514', 'a payment cannot be marked paid without confirmation');
select tests.expect_true(exists (select 1 from public.audit_logs where table_name = 'products' and action = 'INSERT'),
                         'audit log records catalogue changes');
select tests.expect_true(exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'orders'),
                         'orders are published to Realtime');

-- 5. Anonymous visitor ------------------------------------------------------
set role anon;
select tests.expect_count($$select 1 from public.categories$$, 12, 'anon: can browse categories');
select tests.expect_count($$select 1 from public.products$$, 1, 'anon: sees only active products');
select tests.expect_count($$select 1 from public.app_settings$$, 1, 'anon: can read store settings');
select tests.expect_count($$select 1 from storage.objects$$, 1, 'anon: can read public product images');
select tests.expect_error($$select * from public.profiles$$, '42501', 'anon: cannot read profiles');
select tests.expect_error($$select * from public.orders$$, '42501', 'anon: cannot read orders');
select tests.expect_error($$select * from public.product_costs$$, '42501', 'anon: cannot read purchase prices');
select tests.expect_error($$insert into public.categories (name, slug) values ('x', 'x')$$, '42501', 'anon: cannot create categories');
select tests.expect_error($$select public.bootstrap_first_admin('owner@market.test')$$, '42501', 'anon: cannot call admin bootstrap');
select tests.expect_error($$select public.next_bonus_card_number()$$, '42501', 'anon: cannot call internal functions');
reset role;

-- 6. Customer A --------------------------------------------------------------
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000a');
select tests.expect_count($$select 1 from public.profiles$$, 1, 'customer: sees only own profile');
select tests.expect_count($$select 1 from public.user_roles$$, 1, 'customer: sees only own role');
select tests.expect_count($$select 1 from public.orders$$, 1, 'customer: sees own order');
select tests.expect_count($$select 1 from public.order_items$$, 1, 'customer: sees own order items');
select tests.expect_count($$select 1 from public.bonus_cards$$, 1, 'customer: sees only own bonus card');
select tests.expect_count($$select 1 from public.bonus_transactions$$, 1, 'customer: sees own bonus history');
select tests.expect_count($$select 1 from public.notifications$$, 2, 'customer: sees own + broadcast notifications');
select tests.expect_count($$select 1 from public.audit_logs$$, 0, 'customer: cannot read the audit log');
select tests.expect_count($$select 1 from public.products$$, 1, 'customer: inactive products hidden');
update public.profiles set first_name = 'Aysel2' where id = auth.uid();
select tests.expect_true((select first_name from public.profiles) = 'Aysel2', 'customer: can edit own name');
select tests.expect_error($$update public.profiles set phone = '+994509999999'$$, '42501', 'customer: cannot change own phone directly');
select tests.expect_error($$update public.profiles set is_active = true$$, '42501', 'customer: cannot change account status');
select tests.expect_error($$update public.user_roles set role = 'admin'$$, '42501', 'customer: cannot change own role');
select tests.expect_error($$insert into public.user_roles (user_id, role) values (auth.uid(), 'admin')$$, '42501', 'customer: cannot insert a role');
select tests.expect_error($$update public.bonus_cards set balance = 1000$$, '42501', 'customer: cannot change bonus balance');
select tests.expect_error($$insert into public.bonus_transactions (card_id, type, amount, balance_after, idempotency_key)
                            select id, 'campaign', 100, 100, 'hack' from public.bonus_cards$$, '42501', 'customer: cannot create bonus transactions');
select tests.expect_error($$update public.orders set total = 0, subtotal = 0, discount_total = 0, delivery_fee = 0$$, '42501', 'customer: cannot modify order prices');
select tests.expect_error($$insert into public.orders (customer_id, delivery_method, customer_name, contact_phone, subtotal, total, payment_method, idempotency_key)
                            values (auth.uid(), 'pickup', 'x', '+994501112233', 0, 0, 'pay_at_pickup', gen_random_uuid())$$,
                          '42501', 'customer: cannot insert orders directly (server function only)');
select tests.expect_error($$update public.orders set payment_status = 'paid'$$, '42501', 'customer: cannot mark an order paid');
select tests.expect_error($$update public.inventory set quantity = 0$$, '42501', 'customer: cannot change stock');
update public.products set price = 0.01;   -- filtered by RLS: affects 0 rows
select tests.expect_true((select price from public.products where id = '11111111-1111-1111-1111-111111111111') = 2.40,
                         'customer: price update attempt has no effect');
select tests.expect_count($$select 1 from public.product_costs$$, 0, 'customer: cannot read purchase prices');
select tests.expect_error($$insert into storage.objects (bucket_id, name) values ('product-images', 'x.jpg')$$, '42501', 'customer: cannot upload product images');
insert into public.cart_items (cart_id, product_id, quantity)
select id, '11111111-1111-1111-1111-111111111111', 2 from public.carts;
select tests.expect_count($$select 1 from public.cart_items$$, 1, 'customer: can add to own cart');
insert into public.favorites (customer_id, product_id) values (auth.uid(), '11111111-1111-1111-1111-111111111111');
insert into public.delivery_addresses (customer_id, address_line, settlement) values (auth.uid(), 'Mərkəz küç. 1', 'Kənd');
select tests.expect_error($$insert into public.delivery_addresses (customer_id, address_line) values ('00000000-0000-0000-0000-00000000000b', 'Başqasının ünvanı')$$,
                          '42501', 'customer: cannot create an address for someone else');
select tests.expect_error($$insert into public.favorites (customer_id, product_id) values ('00000000-0000-0000-0000-00000000000b', '11111111-1111-1111-1111-111111111111')$$,
                          '42501', 'customer: cannot write another customer''s favorites');
update public.notifications set read_at = now() where user_id = auth.uid();
select tests.expect_count($$select 1 from public.notifications where read_at is not null$$, 1, 'customer: can mark own notification read');
reset role;

-- 7. Customer B cannot see A's data ------------------------------------------
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000b');
select tests.expect_count($$select 1 from public.orders$$, 0, 'other customer: cannot see A''s order');
select tests.expect_count($$select 1 from public.order_items$$, 0, 'other customer: cannot see A''s order items');
select tests.expect_count($$select 1 from public.cart_items$$, 0, 'other customer: cannot see A''s cart');
select tests.expect_count($$select 1 from public.delivery_addresses$$, 0, 'other customer: cannot see A''s addresses');
select tests.expect_count($$select 1 from public.favorites$$, 0, 'other customer: cannot see A''s favorites');
select tests.expect_count($$select 1 from public.bonus_transactions$$, 0, 'other customer: cannot see A''s bonus history');
select tests.expect_count($$select 1 from public.profiles where id = '00000000-0000-0000-0000-00000000000a'$$, 0, 'other customer: cannot see A''s profile');
select tests.expect_count($$select 1 from public.notifications$$, 1, 'other customer: sees broadcast only, not A''s personal notification');
select tests.expect_error(
  format($f$insert into public.cart_items (cart_id, product_id, quantity) values (%L, '11111111-1111-1111-1111-111111111111', 1)$f$, :'a_cart'),
  '42501', 'other customer: cannot add items to A''s cart');
update public.profiles set first_name = 'hacked' where id = '00000000-0000-0000-0000-00000000000a';
reset role;
select tests.expect_true((select first_name from public.profiles where id = '00000000-0000-0000-0000-00000000000a') = 'Aysel2',
                         'other customer: update of A''s profile had no effect');

-- 8. Courier ---------------------------------------------------------------
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000c');
select tests.expect_count($$select 1 from public.orders$$, 1, 'courier: sees the order assigned to them');
select tests.expect_count($$select 1 from public.order_items$$, 1, 'courier: sees items of assigned order');
select tests.expect_count($$select 1 from public.profiles$$, 1, 'courier: cannot browse customer profiles');
select tests.expect_count($$select 1 from public.bonus_cards$$, 0, 'courier: cannot see bonus cards');
select tests.expect_count($$select 1 from public.notifications$$, 0, 'courier: does not receive customer broadcasts');
select tests.expect_error($$update public.orders set status = 'delivered'$$, '42501', 'courier: status changes only via server function');
update public.couriers set is_available = false where id = auth.uid();
select tests.expect_true((select not is_available from public.couriers), 'courier: can toggle own availability');
reset role;
update public.orders set courier_id = null;
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000c');
select tests.expect_count($$select 1 from public.orders$$, 0, 'courier: unassigned orders are invisible');
reset role;

-- 9. Admin ---------------------------------------------------------------
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-0000000000ad');
select tests.expect_count($$select 1 from public.profiles$$, 4, 'admin: sees all profiles');
select tests.expect_count($$select 1 from public.orders$$, 1, 'admin: sees all orders');
select tests.expect_count($$select 1 from public.products$$, 2, 'admin: sees inactive products too');
select tests.expect_count($$select 1 from public.product_costs$$, 1, 'admin: can read purchase prices');
select tests.expect_true((select count(*) > 0 from public.audit_logs), 'admin: can read the audit log');
insert into public.categories (name, slug, sort_order) values ('Dondurma', 'dondurma', 130);
insert into public.products (category_id, name, price, unit) values (4, 'Şokolad', 3.50, 'piece');
update public.products set price = 2.60 where id = '11111111-1111-1111-1111-111111111111';
update public.inventory set low_stock_threshold = 3 where product_id = '11111111-1111-1111-1111-111111111111';
update public.app_settings set bonus_earn_rate = 0.05, primary_color = '#C62828';
select tests.expect_true((select bonus_earn_rate from public.app_settings) = 0.05, 'admin: can change bonus rate');
insert into storage.objects (bucket_id, name) values ('banners', 'kampaniya.jpg');
select tests.expect_error($$update public.inventory set quantity = 1000$$, '42501', 'admin: stock quantity changes only via audited stock function');
select tests.expect_error($$update public.user_roles set role = 'admin' where user_id = '00000000-0000-0000-0000-00000000000b'$$,
                          '42501', 'admin: role changes only via server function (no direct table writes)');
select tests.expect_error($$update public.app_settings set primary_color = 'red'$$, '23514', 'admin: invalid brand color rejected');
reset role;
select tests.expect_true(
  exists (select 1 from public.audit_logs where table_name = 'products' and action = 'UPDATE'
          and actor_id = '00000000-0000-0000-0000-0000000000ad' and actor_role = 'admin'),
  'audit log records who changed a price');

-- 10. Deactivated admin loses access immediately --------------------------
update public.profiles set is_active = false where id = '00000000-0000-0000-0000-0000000000ad';
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-0000000000ad');
select tests.expect_count($$select 1 from public.orders$$, 0, 'deactivated admin: loses admin access');
reset role;
update public.profiles set is_active = true where id = '00000000-0000-0000-0000-0000000000ad';

select 'ALL CHECKS PASSED' as result;
