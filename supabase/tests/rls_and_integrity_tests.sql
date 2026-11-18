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

-- Simulates the verified JWT of a signed-in user. aal2 = signed in with password + MFA code.
create function tests.as_user(uid uuid, aal text default 'aal1') returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated', 'aal', aal)::text, false);
end $$;
create function tests.as_claims(claims jsonb) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', claims::text, false);
end $$;
grant execute on all functions in schema tests to anon, authenticated;

-- ---------------------------------------------------------------------
-- Fixtures (created as the database owner, like Supabase Auth would)
-- ---------------------------------------------------------------------
-- A tries to self-assign "admin" via sign-up metadata: must be ignored.
insert into auth.users (id, phone, encrypted_password, raw_user_meta_data) values
  ('00000000-0000-0000-0000-00000000000a', '994501112233', '$2a$10$abcdefghijklmnopqrstuuJ2Fz7m4cE7h0Qw3m4cE7h0Qw3m4cE7h',
   '{"first_name":"Aysel","last_name":"Məmmədova","role":"admin","phone":"+994509999999"}'),
  ('00000000-0000-0000-0000-00000000000b', '994502223344', null, '{"first_name":"Bəhruz","last_name":"Əliyev"}'),
  ('00000000-0000-0000-0000-00000000000c', '994503334455', null, '{"first_name":"Kamran","last_name":"Kuryer"}');
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

-- 9. Admin (signed in with password + MFA) -------------------------------
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-0000000000ad', 'aal2');
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
select tests.as_user('00000000-0000-0000-0000-0000000000ad', 'aal2');
select tests.expect_count($$select 1 from public.orders$$, 0, 'deactivated admin: loses admin access');
reset role;
update public.profiles set is_active = true where id = '00000000-0000-0000-0000-0000000000ad';

-- =====================================================================
-- SECURITY REVIEW CHECKS (Stage 2 hardening)
-- =====================================================================

-- 11. Passwords are never stored in application tables -----------------
select tests.expect_count($$select 1 from information_schema.columns
                            where table_schema = 'public'
                              and column_name ~* '(pass|pwd|secret|hash|salt|pin_code|otp)'$$,
                          0, 'security: no password / secret / hash column in any public table');
select tests.expect_count($$select 1 from information_schema.columns
                            where table_schema = 'public' and table_name = 'profiles'
                              and column_name not in ('id','first_name','last_name','phone','email','avatar_url','is_active','created_at','updated_at')$$,
                          0, 'security: profiles contains only non-secret identity fields');
select tests.expect_true((select encrypted_password like '$2a$%' from auth.users where id = '00000000-0000-0000-0000-00000000000a'),
                         'security: Supabase Auth keeps only a bcrypt hash (auth.users)');
select tests.expect_count($$select 1 from public.audit_logs where new_data::text ~* 'encrypted_password|\$2a\$' or old_data::text ~* 'encrypted_password|\$2a\$'$$,
                          0, 'security: password hashes never copied into the audit log');
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000a');
select tests.expect_error($$select encrypted_password from auth.users$$, '42501', 'security: API users cannot read auth.users / password hashes');
reset role;
set role anon;
select tests.expect_error($$select * from auth.users$$, '42501', 'security: anon cannot read auth.users');
reset role;

-- 12. RLS everywhere, no bypass paths ----------------------------------------
select tests.expect_count($$select 1 from pg_class where relnamespace = 'public'::regnamespace and relkind in ('r','p') and not relrowsecurity$$,
                          0, 'security: RLS enabled on every public table');
select tests.expect_count($$select 1 from pg_class c where c.relnamespace = 'public'::regnamespace and c.relkind = 'r'
                            and not exists (select 1 from pg_policies p where p.schemaname = 'public' and p.tablename = c.relname)$$,
                          0, 'security: every public table has explicit policies');
select tests.expect_count($$select 1 from pg_class where relnamespace = 'public'::regnamespace and relkind in ('v','m')$$,
                          0, 'security: no views / materialized views that could bypass RLS');
select tests.expect_count($$select 1 from information_schema.role_table_grants
                            where grantee = 'anon' and table_schema = 'public'
                              and (privilege_type <> 'SELECT' or table_name not in
                                   ('app_settings','banners','categories','delivery_zones','inventory','product_images','products','promotions'))$$,
                          0, 'security: anon has read-only access to catalogue tables only');
select tests.expect_count($$select 1 from information_schema.role_table_grants
                            where grantee = 'authenticated' and table_schema = 'public' and privilege_type in ('INSERT','UPDATE','DELETE')
                              and table_name in ('orders','order_items','order_status_history','bonus_cards','bonus_transactions',
                                                 'user_roles','payment_transactions','audit_logs','carts')$$,
                          0, 'security: no client write privilege on orders / bonus / roles / payments / audit');
select tests.expect_count($$select 1 from information_schema.column_privileges
                            where grantee = 'authenticated' and table_schema = 'public' and privilege_type = 'UPDATE'
                              and ((table_name = 'inventory' and column_name = 'quantity')
                                   or (table_name = 'profiles' and column_name in ('phone','email','is_active','id')))$$,
                          0, 'security: stock quantity and profile identity/status columns not client-writable');
select tests.expect_count($$select 1 from pg_proc where pronamespace = 'public'::regnamespace and prosecdef and proconfig is null$$,
                          0, 'security: every SECURITY DEFINER function pins search_path');
select tests.expect_count($$select 1 from pg_proc p where p.pronamespace = 'public'::regnamespace
                              and (has_function_privilege('anon', p.oid, 'execute') or has_function_privilege('authenticated', p.oid, 'execute'))
                              and p.proname not in ('current_app_role','is_admin','is_courier','is_customer','is_active_user',
                                                    'az_fold','effective_price','catalog_products','home_feed','product_sales_last_30_days')
                              and not (p.proname in ('get_my_account','admin_make_courier','admin_set_user_active','set_default_address')
                                       and not has_function_privilege('anon', p.oid, 'execute'))$$,
                          0, 'security: only the reviewed API functions are callable (signed-in-only ones closed to anon)');
select tests.expect_count($$select 1 from pg_proc where pronamespace = 'public'::regnamespace and prosecdef
                              and proname in ('catalog_products','home_feed','effective_price','set_default_address')$$,
                          0, 'security: catalogue / home / address functions run with the caller''s rights (RLS applies)');
select tests.expect_true(not has_function_privilege('service_role', 'public.bootstrap_first_admin(text)', 'execute'),
                         'security: even the service_role key cannot call the admin bootstrap');

-- 13. Admin access requires a verified admin role AND MFA -------------------
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-0000000000ad', 'aal1');   -- correct password, no MFA
select tests.expect_true(public.current_app_role() = 'admin' and not public.is_admin(),
                         'admin without MFA: role is known (app asks for the code) but no admin rights');
select tests.expect_count($$select 1 from public.profiles$$, 1, 'admin without MFA: cannot list customers');
select tests.expect_count($$select 1 from public.audit_logs$$, 0, 'admin without MFA: cannot read the audit log');
select tests.expect_count($$select 1 from public.product_costs$$, 0, 'admin without MFA: cannot read purchase prices');
select tests.expect_error($$insert into public.categories (name, slug) values ('Hack', 'hack')$$, '42501', 'admin without MFA: cannot change the catalogue');
select tests.expect_error($$insert into storage.objects (bucket_id, name) values ('branding', 'logo.png')$$, '42501', 'admin without MFA: cannot upload files');
update public.app_settings set store_name = 'hacked';
reset role;
select tests.expect_true((select store_name from public.app_settings) = 'Market', 'admin without MFA: settings update has no effect');

-- Forged / unexpected JWT claims never grant admin: the role is read from user_roles, not from the token.
set role authenticated;
select tests.as_claims('{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated","aal":"aal2",
                         "user_role":"admin","app_metadata":{"role":"admin"},"user_metadata":{"role":"admin"}}');
select tests.expect_true(public.current_app_role() = 'customer' and not public.is_admin(),
                         'customer with forged admin claims + MFA is still a customer');
select tests.expect_count($$select 1 from public.profiles$$, 1, 'forged claims: still sees only own profile');
select tests.as_claims('{"sub":"99999999-9999-9999-9999-999999999999","role":"authenticated","aal":"aal2"}');
select tests.expect_true(public.current_app_role() is null and not public.is_admin(), 'unknown user id gets no role');
select tests.expect_count($$select 1 from public.profiles$$, 0, 'unknown user id sees no personal data');
select tests.as_claims('{}');
select tests.expect_count($$select 1 from public.user_roles$$, 0, 'request without user id sees no roles');
reset role;

-- User-editable auth metadata changed after sign-up does not change the role.
update auth.users set raw_user_meta_data = '{"role":"admin"}' where id = '00000000-0000-0000-0000-00000000000b';
select tests.expect_true((select role from public.user_roles where user_id = '00000000-0000-0000-0000-00000000000b') = 'customer',
                         'changing auth user metadata cannot change the role');

-- Courier cannot reach admin data.
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000c', 'aal2');
select tests.expect_true(not public.is_admin(), 'courier with MFA is not an admin');
select tests.expect_count($$select 1 from public.audit_logs$$, 0, 'courier: cannot read the audit log');
select tests.expect_count($$select 1 from public.product_costs$$, 0, 'courier: cannot read purchase prices');
select tests.expect_count($$select 1 from public.user_roles$$, 1, 'courier: sees only own role');
select tests.expect_error($$insert into public.products (name, price) values ('x', 1)$$, '42501', 'courier: cannot create products');
reset role;

-- 14. Identity: sign-up cannot claim someone else's phone number --------------
select tests.expect_true((select phone from public.profiles where id = '00000000-0000-0000-0000-00000000000a') = '+994501112233',
                         'phone comes from verified auth phone, not from metadata');
insert into auth.users (id, email, raw_user_meta_data)
values ('00000000-0000-0000-0000-0000000000e1', 'squatter@test.az', '{"phone":"+994507778899"}');
select tests.expect_true((select phone is null from public.profiles where id = '00000000-0000-0000-0000-0000000000e1'),
                         'email sign-up cannot set an unverified phone number');
insert into auth.users (id, phone) values ('00000000-0000-0000-0000-0000000000e2', '994507778899');
select tests.expect_true((select phone from public.profiles where id = '00000000-0000-0000-0000-0000000000e2') = '+994507778899',
                         'the real owner of the phone can still register');

-- 15. Blocked accounts cannot write ---------------------------------------------
update public.profiles set is_active = false where id = '00000000-0000-0000-0000-00000000000a';
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000a');
select tests.expect_count($$select 1 from public.orders$$, 1, 'blocked customer: can still see own order history');
select tests.expect_error(format($f$insert into public.cart_items (cart_id, product_id, quantity) values (%L, '11111111-1111-1111-1111-111111111111', 1)$f$, :'a_cart'),
                          '42501', 'blocked customer: cannot add to cart');
select tests.expect_error($$insert into public.delivery_addresses (customer_id, address_line) values (auth.uid(), 'Yeni ünvan')$$,
                          '42501', 'blocked customer: cannot add addresses');
select tests.expect_error($$insert into public.device_tokens (token, user_id) values ('fcm-token-1', auth.uid())$$,
                          '42501', 'blocked customer: cannot register for push');
update public.profiles set first_name = 'blocked-edit' where id = auth.uid();
reset role;
select tests.expect_true((select first_name from public.profiles where id = '00000000-0000-0000-0000-00000000000a') = 'Aysel2',
                         'blocked customer: profile edit has no effect');
update public.profiles set is_active = true where id = '00000000-0000-0000-0000-00000000000a';

-- =====================================================================
-- STAGE 3: authentication functions
-- =====================================================================

-- 16. Registration with address ------------------------------------------------
insert into auth.users (id, phone, encrypted_password, raw_user_meta_data) values
  ('00000000-0000-0000-0000-0000000000f1', '994551234567', '$2a$10$hash',
   '{"first_name":" Leyla ","last_name":"Həsənova","address_line":"Nizami küç. 12, mən. 5","settlement":"Qəbələ"}');
select tests.expect_true((select first_name from public.profiles where id = '00000000-0000-0000-0000-0000000000f1') = 'Leyla',
                         'registration: names are trimmed');
select tests.expect_true(
  (select d.address_line = 'Nizami küç. 12, mən. 5' and d.settlement = 'Qəbələ' and d.is_default and d.contact_phone = '+994551234567'
          and c.default_address_id = d.id
   from public.delivery_addresses d join public.customers c on c.id = d.customer_id
   where d.customer_id = '00000000-0000-0000-0000-0000000000f1'),
  'registration: address saved atomically as default delivery address');
insert into auth.users (id, phone, raw_user_meta_data) values
  ('00000000-0000-0000-0000-0000000000f2', '994551234568', '{"first_name":"X","address_line":"a"}');
select tests.expect_count($$select 1 from public.delivery_addresses where customer_id = '00000000-0000-0000-0000-0000000000f2'$$,
                          0, 'registration: too-short address is ignored, account still created');

-- 17. get_my_account ----------------------------------------------------------
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-0000000000f1');
select tests.expect_true((select public.get_my_account() ->> 'role') = 'customer'
                         and (select (public.get_my_account() ->> 'is_active')::boolean),
                         'get_my_account: returns own role and status');
select tests.as_user('00000000-0000-0000-0000-00000000000c');
select tests.expect_true((select public.get_my_account() ->> 'role') = 'courier', 'get_my_account: courier role');
select tests.as_user('00000000-0000-0000-0000-0000000000ad');
select tests.expect_true((select public.get_my_account() ->> 'role') = 'admin', 'get_my_account: admin role visible before MFA (for routing)');
select tests.as_claims('{}');
select tests.expect_true(public.get_my_account() is null, 'get_my_account: nothing without a signed-in user');
reset role;
set role anon;
select tests.expect_error($$select public.get_my_account()$$, '42501', 'get_my_account: not callable by anon');
reset role;

-- 18. Courier creation (admin only, with MFA) ----------------------------------
insert into auth.users (id, phone, raw_user_meta_data) values
  ('00000000-0000-0000-0000-0000000000c2', '994707654321', '{"first_name":"Orxan","last_name":"Kuryer"}');
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-0000000000f1');
select tests.expect_error($$select public.admin_make_courier('00000000-0000-0000-0000-0000000000c2')$$, '42501',
                          'courier creation: customer cannot create couriers');
select tests.as_user('00000000-0000-0000-0000-00000000000c', 'aal2');
select tests.expect_error($$select public.admin_make_courier('00000000-0000-0000-0000-0000000000c2')$$, '42501',
                          'courier creation: courier cannot create couriers');
select tests.as_user('00000000-0000-0000-0000-0000000000ad', 'aal1');
select tests.expect_error($$select public.admin_make_courier('00000000-0000-0000-0000-0000000000c2')$$, '42501',
                          'courier creation: admin without MFA cannot create couriers');
select tests.as_user('00000000-0000-0000-0000-0000000000ad', 'aal2');
select public.admin_make_courier('00000000-0000-0000-0000-0000000000c2', 'Moped 10-AB-123');
reset role;
select tests.expect_true(
  (select r.role = 'courier' and c.created_by = '00000000-0000-0000-0000-0000000000ad' and c.vehicle_info = 'Moped 10-AB-123'
   from public.user_roles r join public.couriers c on c.id = r.user_id where r.user_id = '00000000-0000-0000-0000-0000000000c2'),
  'courier creation: admin with MFA creates courier, creator recorded');
select tests.expect_count($$select 1 from public.bonus_cards b where b.customer_id = '00000000-0000-0000-0000-0000000000c2'$$,
                          0, 'courier creation: courier has no customer cart / bonus card');
select tests.expect_true(exists (select 1 from public.audit_logs where table_name = 'user_roles' and record_id = '00000000-0000-0000-0000-0000000000c2'
                                 and actor_id = '00000000-0000-0000-0000-0000000000ad'),
                         'courier creation: audited with the admin as actor');
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-0000000000ad', 'aal2');
select tests.expect_error($$select public.admin_make_courier('00000000-0000-0000-0000-0000000000c2')$$, '22023',
                          'courier creation: cannot re-promote an existing courier');
select tests.expect_error($$select public.admin_make_courier('00000000-0000-0000-0000-00000000000a')$$, '22023',
                          'courier creation: a customer with orders cannot become a courier');
select tests.expect_error($$select public.admin_make_courier('00000000-0000-0000-0000-0000000000ad')$$, '22023',
                          'courier creation: an admin cannot be turned into a courier');
reset role;

-- 19. Block / unblock ------------------------------------------------------------
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-0000000000f1');
select tests.expect_error($$select public.admin_set_user_active('00000000-0000-0000-0000-0000000000c2', false)$$, '42501',
                          'blocking: customers cannot block accounts');
select tests.as_user('00000000-0000-0000-0000-0000000000ad', 'aal2');
select tests.expect_error($$select public.admin_set_user_active('00000000-0000-0000-0000-0000000000ad', false)$$, '22023',
                          'blocking: admin cannot block themselves');
select public.admin_set_user_active('00000000-0000-0000-0000-0000000000c2', false);
select tests.as_user('00000000-0000-0000-0000-0000000000c2', 'aal1');
select tests.expect_true((select (public.get_my_account() ->> 'is_active')::boolean) = false and not public.is_courier(),
                         'blocking: blocked courier sees blocked status and loses courier rights');
select tests.as_user('00000000-0000-0000-0000-0000000000ad', 'aal2');
select public.admin_set_user_active('00000000-0000-0000-0000-0000000000c2', true);
reset role;
select tests.expect_count($$select 1 from public.audit_logs where action in ('BLOCK_USER','UNBLOCK_USER')$$, 2, 'blocking: audited');

-- =====================================================================
-- STAGE 4: server-side pricing, catalogue and home feed
-- =====================================================================
insert into public.products (id, category_id, name, description, barcode, sku, unit, price, is_featured) values
  ('33333333-0000-0000-0000-000000000001', 1, 'Şəkər 1kg', 'Toz şəkər', '4760000000035', 'SEK-001', 'kg', 2.00, false),
  ('33333333-0000-0000-0000-000000000002', 1, 'Çay Azərçay 100q', 'Qara çay', '4760000000042', 'CAY-001', 'pack', 5.00, true),
  ('33333333-0000-0000-0000-000000000003', 3, 'Su 1.5L', 'Mineral su', '4760000000059', 'SU-001', 'liter', 0.80, false),
  ('33333333-0000-0000-0000-000000000004', 3, 'Limonad', 'Qazlı içki', '4760000000066', 'LIM-001', 'piece', 1.20, false);
update public.inventory set quantity = 50 where product_id in ('33333333-0000-0000-0000-000000000001','33333333-0000-0000-0000-000000000002','33333333-0000-0000-0000-000000000003');
-- product 4 stays at stock 0
-- 20 % off the whole "Ərzaq" category, a better fixed price on tea, an expired and a future promotion on water.
insert into public.promotions (title, category_id, discount_type, discount_value, starts_at, ends_at) values
  ('Ərzaq həftəsi', 1, 'percent', 20, now() - interval '1 day', now() + interval '6 days');
insert into public.promotions (title, product_id, discount_type, discount_value, starts_at, ends_at) values
  ('Çay endirimi', '33333333-0000-0000-0000-000000000002', 'fixed_price', 3.49, now() - interval '1 hour', now() + interval '1 day'),
  ('Köhnə kampaniya', '33333333-0000-0000-0000-000000000003', 'percent', 50, now() - interval '10 days', now() - interval '1 day'),
  ('Gələcək kampaniya', '33333333-0000-0000-0000-000000000003', 'percent', 50, now() + interval '1 day', now() + interval '2 days');
-- Lemonade: product-level sale whose window has not started yet.
update public.products set sale_price = 0.99, sale_starts_at = now() + interval '1 day' where id = '33333333-0000-0000-0000-000000000004';
insert into public.banners (title, image_url, link_type, link_value, sort_order) values
  ('Ərzaq həftəsi -20%', 'https://example.test/b1.jpg', 'category', '1', 1),
  ('Köhnə banner', 'https://example.test/b0.jpg', 'none', null, 0);
update public.banners set ends_at = now() - interval '1 hour' where title = 'Köhnə banner';

-- 20. Pricing ----------------------------------------------------------------
select tests.expect_true((select public.effective_price(p) from public.products p where id = '33333333-0000-0000-0000-000000000001') = 1.60,
                         'pricing: category percent promotion applies (2.00 → 1.60)');
select tests.expect_true((select public.effective_price(p) from public.products p where id = '33333333-0000-0000-0000-000000000002') = 3.49,
                         'pricing: the lowest of category promo (4.00) and product fixed price (3.49) wins');
select tests.expect_true((select public.effective_price(p) from public.products p where id = '33333333-0000-0000-0000-000000000003') = 0.80,
                         'pricing: expired and future promotions are ignored');
select tests.expect_true((select public.effective_price(p) from public.products p where id = '33333333-0000-0000-0000-000000000004') = 1.20,
                         'pricing: product sale price before its window opens is ignored');
select tests.expect_true((select public.effective_price(p) from public.products p where id = '11111111-1111-1111-1111-111111111111') = 1.99,
                         'pricing: open-ended product sale price applies');
update public.promotions set is_active = false where title = 'Ərzaq həftəsi';
select tests.expect_true((select public.effective_price(p) from public.products p where id = '33333333-0000-0000-0000-000000000001') = 2.00,
                         'pricing: deactivating a promotion restores the regular price immediately');
update public.promotions set is_active = true where title = 'Ərzaq həftəsi';

-- 21. Catalogue (as an anonymous visitor: no user id in the request) --------------
select tests.as_claims('{}');
set role anon;
select tests.expect_count($$select 1 from public.catalog_products()$$, 6, 'catalogue: all active products, inactive hidden');
select tests.expect_true(not exists (select 1 from public.catalog_products(p_limit => 100) where name = 'Gizli məhsul'),
                         'catalogue: inactive product never listed');
select tests.expect_true((select discount_percent from public.catalog_products(p_search => 'şəkər')) = 20,
                         'catalogue: discount percent computed on the server');
select tests.expect_count($$select 1 from public.catalog_products(p_search => 'seker')$$, 1, 'search: "seker" finds "Şəkər" (Azerbaijani letters folded)');
select tests.expect_count($$select 1 from public.catalog_products(p_search => 'CAY')$$, 1, 'search: case-insensitive, "CAY" finds "Çay"');
select tests.expect_count($$select 1 from public.catalog_products(p_search => '4760000000059')$$, 1, 'search: exact barcode match');
select tests.expect_count($$select 1 from public.catalog_products(p_search => 'qazli')$$, 1, 'search: matches the description too');
select tests.expect_count($$select 1 from public.catalog_products(p_category_id => 3)$$, 2, 'catalogue: filter by category');
select tests.expect_true((select bool_and(final_price < price) from public.catalog_products(p_only_discounted => true)),
                         'catalogue: discounted filter returns only discounted products');
select tests.expect_true((select array_agg(name order by ord) from (select name, row_number() over () ord from public.catalog_products(p_sort => 'price_asc')) x)
                         = (select array_agg(name order by final_price, name) from public.catalog_products()),
                         'catalogue: price sort uses the final (discounted) price');
select tests.expect_true((select not in_stock from public.catalog_products(p_search => 'limonad')), 'catalogue: out-of-stock flag');
select tests.expect_true((select name from public.catalog_products(p_sort => 'popular') limit 1) = 'Süd 1L',
                         'catalogue: popular sort uses units sold (aggregate only)');
select tests.expect_count($$select 1 from public.catalog_products(p_limit => 100000)$$, 6, 'catalogue: limit is capped server-side');
select tests.expect_true((select string_agg(a, ',' order by n) from (
                            select unnest(proargnames) a, generate_subscripts(proargnames, 1) n
                            from pg_proc where proname = 'product_sales_last_30_days') x) = 'product_id,units',
                         'popularity helper returns only product_id and units (no customer or order data)');

-- 22. Home feed ------------------------------------------------------------------------
select tests.expect_true((select jsonb_array_length(public.home_feed() -> 'banners')) = 1, 'home: only currently active banners');
select tests.expect_true((select jsonb_array_length(public.home_feed() -> 'categories')) = 13, 'home: active top-level categories');
select tests.expect_true((select public.home_feed() -> 'discounted' -> 0 ->> 'name') = 'Çay Azərçay 100q', 'home: biggest discount first');
select tests.expect_true((select jsonb_array_length(public.home_feed() -> 'best_sellers')) = 1, 'home: best sellers only include products that sold');
select tests.expect_true((select public.home_feed() -> 'for_you' -> 0 ->> 'name') = 'Çay Azərçay 100q', 'home: anonymous "for you" falls back to featured products');
select tests.expect_true((select public.home_feed() -> 'bonus') = 'null'::jsonb, 'home: no bonus data for anonymous visitors');
reset role;

set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000a');
select tests.expect_true((select (public.home_feed() -> 'bonus' ->> 'card_number') ~ '^7700'), 'home: signed-in customer sees own bonus card');
select tests.expect_true((select public.home_feed() -> 'for_you' -> 0 ->> 'category_id') = '2',
                         'home: "for you" is based on the customer''s own purchases (milk → dairy)');
select tests.as_user('00000000-0000-0000-0000-00000000000b');
select tests.expect_true((select public.home_feed() -> 'bonus' ->> 'card_number')
                         = (select card_number from public.bonus_cards where customer_id = auth.uid()),
                         'home: each customer sees only their own bonus card');
reset role;

-- 23. Default address --------------------------------------------------------------------
insert into public.delivery_addresses (id, customer_id, label, address_line, settlement)
values ('44444444-0000-0000-0000-000000000001', '00000000-0000-0000-0000-00000000000a', 'İş', 'Bakı küç. 5', 'Qəbələ');
set role authenticated;
select tests.as_user('00000000-0000-0000-0000-00000000000a');
select public.set_default_address('44444444-0000-0000-0000-000000000001');
select tests.expect_count($$select 1 from public.delivery_addresses where is_default$$, 1, 'address: exactly one default address');
select tests.expect_true((select default_address_id from public.customers) = '44444444-0000-0000-0000-000000000001',
                         'address: customer default updated');
select tests.as_user('00000000-0000-0000-0000-00000000000b');
select tests.expect_error($$select public.set_default_address('44444444-0000-0000-0000-000000000001')$$, 'P0002',
                          'address: cannot select another customer''s address');
reset role;
set role anon;
select tests.expect_error($$select public.set_default_address('44444444-0000-0000-0000-000000000001')$$, '42501',
                          'address: anon cannot call set_default_address');
reset role;

select 'ALL CHECKS PASSED' as result;
