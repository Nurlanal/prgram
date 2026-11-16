-- =====================================================================
-- 0003 — Server-side functions & triggers
-- All SECURITY DEFINER functions pin search_path = '' and fully qualify names.
-- =====================================================================

-- ---------- Role helpers (used by RLS policies) ----------
-- SECURITY DEFINER so policies can read user_roles without recursive RLS checks.
create or replace function public.current_app_role()
returns public.app_role
language sql stable security definer
set search_path = ''
as $$
  select r.role
  from public.user_roles r
  join public.profiles p on p.id = r.user_id
  where r.user_id = auth.uid() and p.is_active
$$;

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = ''
as $$ select coalesce(public.current_app_role() = 'admin', false) $$;

create or replace function public.is_courier()
returns boolean language sql stable security definer set search_path = ''
as $$ select coalesce(public.current_app_role() = 'courier', false) $$;

create or replace function public.is_customer()
returns boolean language sql stable security definer set search_path = ''
as $$ select coalesce(public.current_app_role() = 'customer', false) $$;

-- ---------- Bonus card numbers: 16 digits with Luhn check digit ----------
create or replace function public.luhn_check_digit(p_digits text)
returns integer
language plpgsql immutable
set search_path = ''
as $$
declare
  total integer := 0;
  d integer;
  i integer;
  double_it boolean := true;   -- the right-most payload digit is doubled
begin
  for i in reverse char_length(p_digits)..1 loop
    d := substr(p_digits, i, 1)::integer;
    if double_it then
      d := d * 2;
      if d > 9 then d := d - 9; end if;
    end if;
    total := total + d;
    double_it := not double_it;
  end loop;
  return (10 - (total % 10)) % 10;
end;
$$;

create or replace function public.next_bonus_card_number()
returns text
language plpgsql volatile security definer
set search_path = ''
as $$
declare
  payload text := '7700' || lpad(nextval('public.bonus_card_seq')::text, 11, '0');
begin
  return payload || public.luhn_check_digit(payload)::text;
end;
$$;

-- ---------- New sign-up → profile + customer + role + cart + bonus card ----------
-- The role is ALWAYS 'customer' here; any "role" sent in sign-up metadata is ignored.
-- Admin / courier roles are granted only by server-side functions.
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_phone text := nullif(coalesce(new.phone, meta ->> 'phone'), '');
begin
  if v_phone is not null and left(v_phone, 1) <> '+' then
    v_phone := '+' || v_phone;
  end if;

  insert into public.profiles (id, first_name, last_name, phone, email)
  values (
    new.id,
    left(coalesce(meta ->> 'first_name', ''), 80),
    left(coalesce(meta ->> 'last_name', ''), 80),
    v_phone,
    new.email
  );

  insert into public.user_roles (user_id, role) values (new.id, 'customer');
  insert into public.customers (id) values (new.id);
  insert into public.carts (customer_id) values (new.id);
  insert into public.bonus_cards (customer_id, card_number)
  values (new.id, public.next_bonus_card_number());

  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- Order number: MK-YYMMDD-NNNNNN (unique, sequential) ----------
create or replace function public.assign_order_number()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  new.order_number := 'MK-' || to_char(now() at time zone 'Asia/Baku', 'YYMMDD') || '-'
                      || lpad(nextval('public.order_number_seq')::text, 6, '0');
  return new;
end;
$$;

create trigger assign_order_number
  before insert on public.orders
  for each row execute function public.assign_order_number();

-- Order history: record every status transition.
create or replace function public.log_order_status()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' or new.status is distinct from old.status then
    insert into public.order_status_history (order_id, status, changed_by)
    values (new.id, new.status, auth.uid());
  end if;
  return new;
end;
$$;

create trigger log_order_status
  after insert or update of status on public.orders
  for each row execute function public.log_order_status();

-- ---------- Every product gets a stock row ----------
create or replace function public.create_inventory_row()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  insert into public.inventory (product_id) values (new.id) on conflict do nothing;
  return new;
end;
$$;

create trigger create_inventory_row
  after insert on public.products
  for each row execute function public.create_inventory_row();

-- ---------- Role guard (defence in depth on top of RLS + grants) ----------
create or replace function public.guard_user_roles()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  -- Requests coming through the API as a signed-in user must be an admin.
  if auth.uid() is not null and not public.is_admin() then
    raise exception 'permission denied: only an admin can change roles' using errcode = '42501';
  end if;

  -- Never leave the store without an active admin.
  if (tg_op = 'DELETE' and old.role = 'admin')
     or (tg_op = 'UPDATE' and old.role = 'admin' and new.role <> 'admin') then
    if (select count(*) from public.user_roles where role = 'admin') <= 1 then
      raise exception 'cannot remove the last admin' using errcode = '42501';
    end if;
  end if;

  if tg_op = 'DELETE' then return old; end if;
  return new;
end;
$$;

create trigger guard_user_roles
  before update or delete on public.user_roles
  for each row execute function public.guard_user_roles();

-- Staff accounts (admin / courier) are not shoppers: when a fresh account is promoted, drop its
-- customer-only data (cart, bonus card, favorites, addresses). Accounts that already placed orders keep it.
create or replace function public.cleanup_customer_data_for_staff()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
  if new.role <> 'customer' and old.role = 'customer'
     and not exists (select 1 from public.orders where customer_id = new.user_id) then
    delete from public.customers where id = new.user_id;     -- cascades to cart, bonus card, favorites, addresses
  end if;
  return new;
end;
$$;

create trigger cleanup_customer_data_for_staff
  after update of role on public.user_roles
  for each row execute function public.cleanup_customer_data_for_staff();

-- ---------- Audit log ----------
create or replace function public.audit_row_change()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  v_old jsonb := case when tg_op in ('UPDATE', 'DELETE') then to_jsonb(old) end;
  v_new jsonb := case when tg_op in ('INSERT', 'UPDATE') then to_jsonb(new) end;
  v_id  text  := coalesce(v_new ->> 'id', v_old ->> 'id', v_new ->> 'product_id', v_old ->> 'product_id',
                          v_new ->> 'user_id', v_old ->> 'user_id');
begin
  if tg_op = 'UPDATE' and v_old = v_new then
    return new;
  end if;
  insert into public.audit_logs (actor_id, actor_role, action, table_name, record_id, old_data, new_data)
  values (auth.uid(), public.current_app_role()::text, tg_op, tg_table_name, v_id, v_old, v_new);
  return coalesce(new, old);
end;
$$;

do $$
declare t text;
begin
  foreach t in array array[
    'user_roles','couriers','categories','products','product_costs','inventory','delivery_zones',
    'orders','bonus_transactions','banners','promotions','payment_transactions','app_settings'
  ] loop
    execute format('create trigger audit_row_change after insert or update or delete on public.%I
                    for each row execute function public.audit_row_change()', t);
  end loop;
end $$;

-- ---------- First admin bootstrap (server-side only) ----------
-- Usage (Supabase SQL Editor, which runs as the database owner):
--   select public.bootstrap_first_admin('owner@example.com');
-- The user must first be created in Authentication → Users. Works only while no admin exists.
create or replace function public.bootstrap_first_admin(p_email text)
returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
  v_user_id uuid;
begin
  if exists (select 1 from public.user_roles where role = 'admin') then
    raise exception 'an admin already exists; use the admin panel to manage roles';
  end if;

  select id into v_user_id from auth.users where lower(email) = lower(trim(p_email));
  if v_user_id is null then
    raise exception 'no auth user with email %', p_email;
  end if;

  update public.user_roles set role = 'admin', granted_by = null where user_id = v_user_id;

  insert into public.audit_logs (actor_id, actor_role, action, table_name, record_id, new_data)
  values (null, 'bootstrap', 'BOOTSTRAP_FIRST_ADMIN', 'user_roles', v_user_id::text,
          jsonb_build_object('email', p_email));
  return v_user_id;
end;
$$;

-- Not callable through the API by anyone (anon / authenticated / service_role).
revoke all on function public.bootstrap_first_admin(text) from public;
do $$ begin
  if exists (select 1 from pg_roles where rolname = 'anon') then
    execute 'revoke all on function public.bootstrap_first_admin(text) from anon, authenticated, service_role';
  end if;
end $$;

-- Internal helpers are not part of the public API either.
revoke all on function public.handle_new_user() from public;
revoke all on function public.next_bonus_card_number() from public;
revoke all on function public.assign_order_number() from public;
revoke all on function public.audit_row_change() from public;
revoke all on function public.guard_user_roles() from public;
revoke all on function public.log_order_status() from public;
revoke all on function public.create_inventory_row() from public;
revoke all on function public.cleanup_customer_data_for_staff() from public;
