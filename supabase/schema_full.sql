-- ============================================================================
-- Supermarket platform — full database schema (generated, do not edit by hand)
-- Source: supabase/migrations/*.sql — regenerate with supabase/build_schema.sh
-- ============================================================================

-- >>> 20261116000001_extensions_and_types.sql
-- =====================================================================
-- 0001 — Types and generic helpers
-- Supermarket platform (single store). Target: Supabase PostgreSQL 15+.
-- =====================================================================

-- ---------- Enums ----------
create type public.app_role as enum ('customer', 'admin', 'courier');

create type public.unit_type as enum ('piece', 'kg', 'gram', 'liter', 'ml', 'pack', 'box');

-- Order lifecycle. Delivery: placed → preparing → courier_assigned → on_the_way → delivered.
-- Pickup:   placed → preparing → ready_for_pickup → picked_up.  Any open order → cancelled.
create type public.order_status as enum (
  'placed', 'preparing', 'ready_for_pickup', 'courier_assigned',
  'on_the_way', 'delivered', 'picked_up', 'cancelled'
);

create type public.delivery_method as enum ('delivery', 'pickup');

-- 'online_card' is reserved for a future bank integration (no card data is ever stored here).
create type public.payment_method as enum ('cash_on_delivery', 'card_on_delivery', 'pay_at_pickup', 'online_card');

create type public.payment_status as enum ('pending', 'paid', 'failed', 'refunded', 'cancelled');

create type public.bonus_tx_type as enum (
  'earn', 'spend', 'earn_reversal', 'spend_refund', 'expire', 'campaign', 'admin_adjust'
);

create type public.notification_type as enum (
  'promotion', 'campaign', 'new_product', 'order_status', 'bonus', 'system'
);

create type public.discount_type as enum ('percent', 'fixed_price');

create type public.banner_link_type as enum ('none', 'product', 'category', 'promotion', 'url');

-- ---------- Generic trigger: keep updated_at current ----------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- >>> 20261116000002_tables.sql
-- =====================================================================
-- 0002 — Tables and relations
-- Money: numeric(12,2) AZN. Quantities: numeric(12,3) (supports kg / litre).
-- =====================================================================

-- ---------- Users ----------
create table public.profiles (
  id          uuid primary key references auth.users (id) on delete cascade,
  first_name  text not null default '' check (char_length(first_name) <= 80),
  last_name   text not null default '' check (char_length(last_name) <= 80),
  phone       text unique check (phone is null or phone ~ '^\+?[0-9]{9,15}$'),
  email       text,
  avatar_url  text,
  is_active   boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

-- Exactly one role per user. Written ONLY by server-side functions (see 0004 RLS).
create table public.user_roles (
  user_id     uuid primary key references public.profiles (id) on delete cascade,
  role        public.app_role not null default 'customer',
  granted_by  uuid references public.profiles (id) on delete set null,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create table public.customers (
  id                  uuid primary key references public.profiles (id) on delete cascade,
  default_address_id  uuid,
  -- Per-category push preferences (Stage 12/13). Missing key = enabled.
  notification_prefs  jsonb not null default '{}'::jsonb,
  created_at          timestamptz not null default now()
);

create table public.couriers (
  id            uuid primary key references public.profiles (id) on delete cascade,
  vehicle_info  text,
  is_available  boolean not null default true,
  is_active     boolean not null default true,
  created_by    uuid references public.profiles (id) on delete set null,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

-- ---------- Catalogue ----------
create table public.categories (
  id          bigint generated always as identity primary key,
  parent_id   bigint references public.categories (id) on delete set null,
  name        text not null check (char_length(name) between 1 and 80),
  slug        text not null unique check (slug ~ '^[a-z0-9-]+$'),
  icon_url    text,
  image_url   text,
  sort_order  integer not null default 0,
  is_active   boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create table public.products (
  id              uuid primary key default gen_random_uuid(),
  category_id     bigint references public.categories (id) on delete set null,
  name            text not null check (char_length(name) between 1 and 200),
  description     text not null default '',
  barcode         text unique check (barcode is null or barcode ~ '^[0-9A-Za-z-]{4,32}$'),
  sku             text unique,                                  -- "Məhsul kodu"
  unit            public.unit_type not null default 'piece',
  price           numeric(12,2) not null check (price > 0),     -- regular selling price
  -- Direct sale price set on the product (promotions may also apply, see promotions).
  sale_price      numeric(12,2) check (sale_price is null or (sale_price > 0)),
  sale_starts_at  timestamptz,
  sale_ends_at    timestamptz,
  main_image_url  text,
  is_active       boolean not null default true,
  is_featured     boolean not null default false,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  constraint sale_below_price check (sale_price is null or sale_price < price),
  constraint sale_window check (sale_ends_at is null or sale_starts_at is null or sale_ends_at > sale_starts_at)
);
create index products_category_idx on public.products (category_id) where is_active;
create index products_name_search_idx on public.products using gin (to_tsvector('simple', name || ' ' || description));

-- Purchase price is admin-only, so it lives in its own table (RLS is row-level).
create table public.product_costs (
  product_id      uuid primary key references public.products (id) on delete cascade,
  purchase_price  numeric(12,2) not null check (purchase_price >= 0),
  updated_at      timestamptz not null default now()
);

create table public.product_images (
  id          uuid primary key default gen_random_uuid(),
  product_id  uuid not null references public.products (id) on delete cascade,
  image_url   text not null,
  sort_order  integer not null default 0,
  created_at  timestamptz not null default now()
);
create index product_images_product_idx on public.product_images (product_id, sort_order);

-- Single store → one stock row per product. Stock can never go negative.
create table public.inventory (
  product_id           uuid primary key references public.products (id) on delete cascade,
  quantity             numeric(12,3) not null default 0 check (quantity >= 0),
  low_stock_threshold  numeric(12,3) not null default 5 check (low_stock_threshold >= 0),
  updated_at           timestamptz not null default now()
);

create table public.favorites (
  customer_id  uuid not null references public.customers (id) on delete cascade,
  product_id   uuid not null references public.products (id) on delete cascade,
  created_at   timestamptz not null default now(),
  primary key (customer_id, product_id)
);

-- ---------- Cart (prices are NOT stored; the server prices everything) ----------
create table public.carts (
  id           uuid primary key default gen_random_uuid(),
  customer_id  uuid not null unique references public.customers (id) on delete cascade,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

create table public.cart_items (
  cart_id     uuid not null references public.carts (id) on delete cascade,
  product_id  uuid not null references public.products (id) on delete cascade,
  quantity    numeric(12,3) not null check (quantity > 0 and quantity <= 1000),
  added_at    timestamptz not null default now(),
  primary key (cart_id, product_id)
);

-- ---------- Delivery ----------
create table public.delivery_zones (
  id                       bigint generated always as identity primary key,
  name                     text not null,                        -- e.g. village / town name
  description              text not null default '',
  -- Polygon as JSON array of [lat, lng] pairs (no PostGIS dependency).
  polygon                  jsonb not null check (jsonb_typeof(polygon) = 'array' and jsonb_array_length(polygon) >= 3),
  delivery_fee             numeric(12,2) not null default 0 check (delivery_fee >= 0),
  free_delivery_threshold  numeric(12,2) check (free_delivery_threshold is null or free_delivery_threshold >= 0),
  min_order_amount         numeric(12,2) not null default 0 check (min_order_amount >= 0),
  opens_at                 time not null default '09:00',
  closes_at                time not null default '21:00',
  working_days             smallint[] not null default '{1,2,3,4,5,6,7}',   -- ISO: 1=Mon … 7=Sun
  is_active                boolean not null default true,
  created_at               timestamptz not null default now(),
  updated_at               timestamptz not null default now()
);

create table public.delivery_addresses (
  id             uuid primary key default gen_random_uuid(),
  customer_id    uuid not null references public.customers (id) on delete cascade,
  label          text not null default 'Ev',
  address_line   text not null check (char_length(address_line) between 3 and 300),
  settlement     text not null default '',                     -- kənd / şəhər
  latitude       double precision check (latitude between -90 and 90),
  longitude      double precision check (longitude between -180 and 180),
  contact_phone  text check (contact_phone is null or contact_phone ~ '^\+?[0-9]{9,15}$'),
  notes          text not null default '',
  is_default     boolean not null default false,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
create index delivery_addresses_customer_idx on public.delivery_addresses (customer_id);

alter table public.customers
  add constraint customers_default_address_fk
  foreign key (default_address_id) references public.delivery_addresses (id) on delete set null;

-- ---------- Orders ----------
create sequence public.order_number_seq start 1001;

create table public.orders (
  id                   uuid primary key default gen_random_uuid(),
  order_number         text not null unique,
  customer_id          uuid not null references public.customers (id) on delete restrict,
  status               public.order_status not null default 'placed',
  delivery_method      public.delivery_method not null,
  delivery_address_id  uuid references public.delivery_addresses (id) on delete set null,
  delivery_zone_id     bigint references public.delivery_zones (id) on delete set null,
  -- Snapshots so the order stays correct even if profile/address change later.
  customer_name        text not null,
  contact_phone        text not null,
  address_snapshot     jsonb,
  scheduled_for        timestamptz,
  courier_id           uuid references public.couriers (id) on delete set null,
  subtotal             numeric(12,2) not null check (subtotal >= 0),
  discount_total       numeric(12,2) not null default 0 check (discount_total >= 0),
  bonus_used           numeric(12,2) not null default 0 check (bonus_used >= 0),
  delivery_fee         numeric(12,2) not null default 0 check (delivery_fee >= 0),
  total                numeric(12,2) not null check (total >= 0),
  bonus_earned         numeric(12,2) not null default 0 check (bonus_earned >= 0),
  payment_method       public.payment_method not null,
  payment_status       public.payment_status not null default 'pending',
  customer_note        text not null default '',
  cancel_reason        text,
  idempotency_key      uuid not null,
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now(),
  delivered_at         timestamptz,
  cancelled_at         timestamptz,
  constraint total_consistent check (total = subtotal - discount_total - bonus_used + delivery_fee),
  constraint pickup_has_no_courier check (delivery_method = 'delivery' or (courier_id is null and delivery_fee = 0)),
  constraint delivery_has_address check (delivery_method = 'pickup' or address_snapshot is not null),
  unique (customer_id, idempotency_key)          -- a retried "place order" never creates a duplicate
);
create index orders_customer_idx on public.orders (customer_id, created_at desc);
create index orders_courier_idx on public.orders (courier_id, status);
create index orders_status_idx on public.orders (status, created_at desc);

create table public.order_items (
  id             uuid primary key default gen_random_uuid(),
  order_id       uuid not null references public.orders (id) on delete cascade,
  product_id     uuid references public.products (id) on delete set null,
  product_name   text not null,
  unit           public.unit_type not null,
  quantity       numeric(12,3) not null check (quantity > 0),
  unit_price     numeric(12,2) not null check (unit_price >= 0),   -- regular price at order time
  final_unit_price numeric(12,2) not null check (final_unit_price >= 0 and final_unit_price <= unit_price),
  line_total     numeric(12,2) not null check (line_total >= 0)
);
create index order_items_order_idx on public.order_items (order_id);

create table public.order_status_history (
  id          bigint generated always as identity primary key,
  order_id    uuid not null references public.orders (id) on delete cascade,
  status      public.order_status not null,
  changed_by  uuid references public.profiles (id) on delete set null,
  note        text,
  created_at  timestamptz not null default now()
);
create index order_status_history_order_idx on public.order_status_history (order_id, created_at);

-- ---------- Bonus card (balance lives ONLY on the server) ----------
create sequence public.bonus_card_seq start 1;

create table public.bonus_cards (
  id               uuid primary key default gen_random_uuid(),
  customer_id      uuid not null unique references public.customers (id) on delete cascade,
  card_number      text not null unique check (card_number ~ '^[0-9]{16}$'),
  -- Opaque random token encoded in the QR code (not guessable from the card number).
  qr_token         text not null unique default replace(gen_random_uuid()::text, '-', ''),
  balance          numeric(12,2) not null default 0 check (balance >= 0),
  lifetime_earned  numeric(12,2) not null default 0 check (lifetime_earned >= 0),
  is_active        boolean not null default true,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

create table public.bonus_transactions (
  id               uuid primary key default gen_random_uuid(),
  card_id          uuid not null references public.bonus_cards (id) on delete cascade,
  order_id         uuid references public.orders (id) on delete set null,
  type             public.bonus_tx_type not null,
  amount           numeric(12,2) not null check (amount <> 0),       -- signed: + credit, - debit
  balance_after    numeric(12,2) not null check (balance_after >= 0),
  expires_at       timestamptz,
  description      text not null default '',
  idempotency_key  text not null unique,                           -- prevents double execution
  created_by       uuid references public.profiles (id) on delete set null,
  created_at       timestamptz not null default now()
);
create index bonus_transactions_card_idx on public.bonus_transactions (card_id, created_at desc);
-- An order can earn / spend / be reversed at most once per type.
create unique index bonus_tx_one_per_order_type on public.bonus_transactions (order_id, type) where order_id is not null;

-- ---------- Marketing ----------
create table public.banners (
  id          uuid primary key default gen_random_uuid(),
  title       text not null,
  subtitle    text not null default '',
  image_url   text not null,
  link_type   public.banner_link_type not null default 'none',
  link_value  text,
  sort_order  integer not null default 0,
  starts_at   timestamptz,
  ends_at     timestamptz,
  is_active   boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create table public.promotions (
  id              uuid primary key default gen_random_uuid(),
  title           text not null,
  description     text not null default '',
  product_id      uuid references public.products (id) on delete cascade,
  category_id     bigint references public.categories (id) on delete cascade,
  discount_type   public.discount_type not null,
  discount_value  numeric(12,2) not null check (discount_value > 0),
  starts_at       timestamptz not null,
  ends_at         timestamptz not null,
  banner_id       uuid references public.banners (id) on delete set null,
  is_active       boolean not null default true,
  notified_at     timestamptz,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now(),
  constraint promotion_window check (ends_at > starts_at),
  constraint promotion_target check (product_id is not null or category_id is not null),
  constraint percent_range check (discount_type <> 'percent' or discount_value < 100)
);
create index promotions_active_idx on public.promotions (starts_at, ends_at) where is_active;

-- ---------- Notifications ----------
create table public.notifications (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid references public.profiles (id) on delete cascade,  -- null = broadcast to all customers
  type        public.notification_type not null,
  title       text not null,
  body        text not null default '',
  data        jsonb not null default '{}'::jsonb,   -- deep link, e.g. {"order_id": "..."} / {"product_id": "..."}
  read_at     timestamptz,
  sent_at     timestamptz,
  created_by  uuid references public.profiles (id) on delete set null,
  created_at  timestamptz not null default now()
);
create index notifications_user_idx on public.notifications (user_id, created_at desc);

create table public.device_tokens (
  token       text primary key,
  user_id     uuid not null references public.profiles (id) on delete cascade,
  platform    text not null default 'android',
  updated_at  timestamptz not null default now()
);
create index device_tokens_user_idx on public.device_tokens (user_id);

-- ---------- Payments (no card data is ever stored) ----------
create table public.payment_transactions (
  id                  uuid primary key default gen_random_uuid(),
  order_id            uuid not null references public.orders (id) on delete cascade,
  method              public.payment_method not null,
  amount              numeric(12,2) not null check (amount >= 0),
  status              public.payment_status not null default 'pending',
  provider            text,               -- future online bank provider name
  provider_reference  text,               -- provider transaction id (never a card number)
  confirmed_by        uuid references public.profiles (id) on delete set null,
  confirmed_at        timestamptz,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  constraint paid_requires_confirmation check (status <> 'paid' or confirmed_at is not null)
);
create index payment_transactions_order_idx on public.payment_transactions (order_id);

-- ---------- Store settings (single row) ----------
create table public.app_settings (
  id                         smallint primary key default 1 check (id = 1),
  store_name                 text not null default 'Market',
  logo_url                   text,
  primary_color              text not null default '#0E7C5A' check (primary_color ~ '^#[0-9A-Fa-f]{6}$'),
  accent_color               text not null default '#FFB547' check (accent_color ~ '^#[0-9A-Fa-f]{6}$'),
  store_address              text not null default '',
  store_latitude             double precision,
  store_longitude            double precision,
  store_phone                text,
  support_phone              text,
  support_whatsapp           text,
  pickup_enabled             boolean not null default true,
  delivery_enabled           boolean not null default true,
  -- Bonus rules: e.g. 0.10 = 1 AZN bonus for every 10 AZN spent.
  bonus_earn_rate            numeric(6,4) not null default 0.10 check (bonus_earn_rate >= 0 and bonus_earn_rate <= 1),
  bonus_min_order_amount     numeric(12,2) not null default 0 check (bonus_min_order_amount >= 0),
  bonus_max_spend_percent    numeric(5,2) not null default 50 check (bonus_max_spend_percent between 0 and 100),
  bonus_max_balance          numeric(12,2) check (bonus_max_balance is null or bonus_max_balance > 0),
  bonus_expiry_days          integer check (bonus_expiry_days is null or bonus_expiry_days > 0),
  updated_at                 timestamptz not null default now()
);

-- ---------- Audit ----------
create table public.audit_logs (
  id          bigint generated always as identity primary key,
  actor_id    uuid,
  actor_role  text,
  action      text not null,          -- INSERT / UPDATE / DELETE / custom action name
  table_name  text,
  record_id   text,
  old_data    jsonb,
  new_data    jsonb,
  created_at  timestamptz not null default now()
);
create index audit_logs_created_idx on public.audit_logs (created_at desc);
create index audit_logs_table_record_idx on public.audit_logs (table_name, record_id);

-- ---------- updated_at triggers ----------
do $$
declare t text;
begin
  foreach t in array array[
    'profiles','user_roles','couriers','categories','products','product_costs','inventory','carts',
    'delivery_zones','delivery_addresses','orders','bonus_cards','banners','promotions',
    'payment_transactions','app_settings','device_tokens'
  ] loop
    execute format('create trigger set_updated_at before update on public.%I
                    for each row execute function public.set_updated_at()', t);
  end loop;
end $$;

-- >>> 20261116000003_functions_and_triggers.sql
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

-- >>> 20261116000004_rls_and_grants.sql
-- =====================================================================
-- 0004 — Row Level Security + explicit privileges
--
-- Model:
--   * Catalogue (products, categories, banners, promotions, zones, settings, stock) is public-read.
--   * Personal data (profile, addresses, cart, favorites, bonus, orders) is visible only to its owner,
--     to the assigned courier (orders only) and to admins.
--   * Money / stock / bonus / order / role tables have NO client write policies: they change only through
--     SECURITY DEFINER functions (added in later stages) that validate and compute everything server-side.
--   * Supabase grants ALL on public tables to anon/authenticated by default; we revoke and re-grant only
--     what each role needs, so a missing policy can never expose a write path.
-- =====================================================================

-- ---------- Enable RLS on every table ----------
do $$
declare t text;
begin
  for t in select tablename from pg_tables where schemaname = 'public' loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

-- ---------- Reset privileges ----------
revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke all on all functions in schema public from anon, authenticated;
alter default privileges in schema public revoke all on tables from anon, authenticated;
alter default privileges in schema public revoke all on sequences from anon, authenticated;
alter default privileges in schema public revoke all on functions from anon, authenticated;

grant usage on schema public to anon, authenticated;

-- Role helpers are callable (needed by policies evaluated as the caller).
grant execute on function public.current_app_role() to anon, authenticated;
grant execute on function public.is_admin() to anon, authenticated;
grant execute on function public.is_courier() to anon, authenticated;
grant execute on function public.is_customer() to anon, authenticated;

-- =====================================================================
-- Public catalogue
-- =====================================================================
grant select on public.categories, public.products, public.product_images, public.inventory,
                public.banners, public.promotions, public.delivery_zones, public.app_settings
  to anon, authenticated;

create policy categories_read on public.categories for select to anon, authenticated
  using (is_active or public.is_admin());
create policy products_read on public.products for select to anon, authenticated
  using (is_active or public.is_admin());
create policy product_images_read on public.product_images for select to anon, authenticated
  using (exists (select 1 from public.products p where p.id = product_id and (p.is_active or public.is_admin())));
create policy inventory_read on public.inventory for select to anon, authenticated
  using (exists (select 1 from public.products p where p.id = product_id and (p.is_active or public.is_admin())));
create policy banners_read on public.banners for select to anon, authenticated
  using ((is_active and (starts_at is null or starts_at <= now()) and (ends_at is null or ends_at > now()))
         or public.is_admin());
create policy promotions_read on public.promotions for select to anon, authenticated
  using ((is_active and starts_at <= now() and ends_at > now()) or public.is_admin());
create policy delivery_zones_read on public.delivery_zones for select to anon, authenticated
  using (is_active or public.is_admin());
create policy app_settings_read on public.app_settings for select to anon, authenticated
  using (true);

-- Admin-managed catalogue writes (prices are plain data; order totals are recomputed server-side).
grant insert, update, delete on public.categories, public.products, public.product_images,
                                public.banners, public.promotions, public.delivery_zones
  to authenticated;
grant update on public.app_settings to authenticated;
grant update (low_stock_threshold) on public.inventory to authenticated;   -- quantity: via stock functions only

do $$
declare t text;
begin
  foreach t in array array['categories','products','product_images','banners','promotions','delivery_zones'] loop
    execute format('create policy %I on public.%I for insert to authenticated with check (public.is_admin())', t || '_admin_insert', t);
    execute format('create policy %I on public.%I for update to authenticated using (public.is_admin()) with check (public.is_admin())', t || '_admin_update', t);
    execute format('create policy %I on public.%I for delete to authenticated using (public.is_admin())', t || '_admin_delete', t);
  end loop;
end $$;
create policy app_settings_admin_update on public.app_settings for update to authenticated
  using (public.is_admin()) with check (public.is_admin());
create policy inventory_admin_update on public.inventory for update to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- Purchase price: admins only.
grant select, insert, update, delete on public.product_costs to authenticated;
create policy product_costs_admin on public.product_costs for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- =====================================================================
-- Users
-- =====================================================================
grant select on public.profiles to authenticated;
-- Users may edit only their display fields (not phone / email / is_active / id).
grant update (first_name, last_name, avatar_url) on public.profiles to authenticated;

create policy profiles_read_own on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_admin());
create policy profiles_update_own on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- Roles: readable by the owner and admins; NO write grants at all (server functions only).
grant select on public.user_roles to authenticated;
create policy user_roles_read on public.user_roles for select to authenticated
  using (user_id = auth.uid() or public.is_admin());

grant select on public.customers to authenticated;
grant update (default_address_id, notification_prefs) on public.customers to authenticated;
create policy customers_read on public.customers for select to authenticated
  using (id = auth.uid() or public.is_admin());
create policy customers_update_own on public.customers for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

grant select on public.couriers to authenticated;
grant update (is_available) on public.couriers to authenticated;
create policy couriers_read on public.couriers for select to authenticated
  using (id = auth.uid() or public.is_admin());
create policy couriers_update_own on public.couriers for update to authenticated
  using (id = auth.uid() and public.is_courier()) with check (id = auth.uid());

-- =====================================================================
-- Customer-owned data
-- =====================================================================
grant select, insert, update, delete on public.delivery_addresses to authenticated;
create policy addresses_owner on public.delivery_addresses for all to authenticated
  using (customer_id = auth.uid()) with check (customer_id = auth.uid() and public.is_customer());
create policy addresses_admin_read on public.delivery_addresses for select to authenticated
  using (public.is_admin());

grant select, insert, delete on public.favorites to authenticated;
create policy favorites_owner on public.favorites for all to authenticated
  using (customer_id = auth.uid()) with check (customer_id = auth.uid() and public.is_customer());

grant select on public.carts to authenticated;
create policy carts_owner on public.carts for select to authenticated
  using (customer_id = auth.uid());

grant select, insert, update, delete on public.cart_items to authenticated;
create policy cart_items_owner on public.cart_items for all to authenticated
  using (exists (select 1 from public.carts c where c.id = cart_id and c.customer_id = auth.uid()))
  with check (exists (select 1 from public.carts c where c.id = cart_id and c.customer_id = auth.uid()));

grant select, insert, update, delete on public.device_tokens to authenticated;
create policy device_tokens_owner on public.device_tokens for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- =====================================================================
-- Orders (read-only from the client; created/changed by server functions)
-- =====================================================================
grant select on public.orders, public.order_items, public.order_status_history to authenticated;

create policy orders_read on public.orders for select to authenticated
  using (customer_id = auth.uid()
         or (courier_id = auth.uid() and public.is_courier())
         or public.is_admin());
create policy order_items_read on public.order_items for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id));      -- inherits orders RLS
create policy order_status_history_read on public.order_status_history for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id));

grant select on public.payment_transactions to authenticated;
create policy payments_read on public.payment_transactions for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id and (o.customer_id = auth.uid() or public.is_admin())));

-- =====================================================================
-- Bonus (read-only from the client)
-- =====================================================================
grant select on public.bonus_cards, public.bonus_transactions to authenticated;
create policy bonus_cards_read on public.bonus_cards for select to authenticated
  using (customer_id = auth.uid() or public.is_admin());
create policy bonus_tx_read on public.bonus_transactions for select to authenticated
  using (exists (select 1 from public.bonus_cards c where c.id = card_id and (c.customer_id = auth.uid() or public.is_admin())));

-- =====================================================================
-- Notifications
-- =====================================================================
grant select on public.notifications to authenticated;
grant update (read_at) on public.notifications to authenticated;
create policy notifications_read on public.notifications for select to authenticated
  using (user_id = auth.uid() or (user_id is null and public.is_customer()) or public.is_admin());
create policy notifications_mark_read on public.notifications for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- =====================================================================
-- Audit log: admins read; rows are written only by triggers / server functions.
-- =====================================================================
grant select on public.audit_logs to authenticated;
create policy audit_logs_admin_read on public.audit_logs for select to authenticated
  using (public.is_admin());

-- >>> 20261116000005_storage_and_realtime.sql
-- =====================================================================
-- 0005 — Storage buckets and Realtime
-- =====================================================================

-- Public-read image buckets; only admins can upload / replace / delete.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('product-images', 'product-images', true, 5242880, array['image/jpeg','image/png','image/webp']),
  ('banners',        'banners',        true, 5242880, array['image/jpeg','image/png','image/webp']),
  ('branding',       'branding',       true, 2097152, array['image/jpeg','image/png','image/webp','image/svg+xml'])
on conflict (id) do nothing;

create policy storage_public_read on storage.objects for select to anon, authenticated
  using (bucket_id in ('product-images', 'banners', 'branding'));

create policy storage_admin_insert on storage.objects for insert to authenticated
  with check (bucket_id in ('product-images', 'banners', 'branding') and public.is_admin());

create policy storage_admin_update on storage.objects for update to authenticated
  using (bucket_id in ('product-images', 'banners', 'branding') and public.is_admin())
  with check (bucket_id in ('product-images', 'banners', 'branding') and public.is_admin());

create policy storage_admin_delete on storage.objects for delete to authenticated
  using (bucket_id in ('product-images', 'banners', 'branding') and public.is_admin());

-- Realtime: live order tracking, courier assignment, in-app notifications, stock and settings.
-- postgres_changes events respect the RLS policies above, so users only receive their own rows.
alter publication supabase_realtime add table
  public.orders, public.order_status_history, public.notifications, public.inventory, public.app_settings;

-- >>> 20261116000006_seed.sql
-- =====================================================================
-- 0006 — Initial data (idempotent)
-- =====================================================================
insert into public.app_settings (id) values (1) on conflict (id) do nothing;

insert into public.categories (name, slug, sort_order) values
  ('Ərzaq',               'erzaq',              10),
  ('Süd məhsulları',      'sud-mehsullari',     20),
  ('İçkilər',             'ickiler',            30),
  ('Şirniyyat',           'sirniyyat',          40),
  ('Meyvə',               'meyve',              50),
  ('Tərəvəz',             'terevez',            60),
  ('Ət məhsulları',       'et-mehsullari',      70),
  ('Toyuq məhsulları',    'toyuq-mehsullari',   80),
  ('Təmizlik vasitələri', 'temizlik-vasiteleri', 90),
  ('Şəxsi qulluq',        'sexsi-qulluq',      100),
  ('Məişət məhsulları',   'meiset-mehsullari', 110),
  ('Uşaq məhsulları',     'usaq-mehsullari',   120)
on conflict (slug) do nothing;

-- >>> 20261116000007_security_hardening.sql
-- =====================================================================
-- 0007 — Security hardening (Stage 2 security review)
--
-- Passwords: NOT stored in any public table. Supabase Auth keeps only a bcrypt hash in
-- auth.users.encrypted_password; the auth schema is never exposed through the API.
-- This migration fixes the issues found in the review:
--   1. Sign-up trusted an unverified phone from user metadata (phone squatting).
--   2. Admin privileges required only a password → now require MFA (JWT aal2).
--   3. Blocked (is_active = false) users could still write their own rows.
-- =====================================================================

-- ---------- 1. Profile phone comes ONLY from the verified auth.users.phone ----------
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  -- raw_user_meta_data is user-controlled: never trust it for identity (phone / role).
  v_phone text := nullif(new.phone, '');
begin
  if v_phone is not null and left(v_phone, 1) <> '+' then
    v_phone := '+' || v_phone;
  end if;

  insert into public.profiles (id, first_name, last_name, phone, email)
  values (
    new.id,
    left(btrim(coalesce(meta ->> 'first_name', '')), 80),
    left(btrim(coalesce(meta ->> 'last_name', '')), 80),
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
revoke all on function public.handle_new_user() from public, anon, authenticated;

-- ---------- 2. Admin privileges require multi-factor authentication ----------
-- current_app_role() still reports 'admin' (so the app can route to the MFA step),
-- but every admin permission checks is_admin(), which demands an aal2 session.
create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = ''
as $$
  select coalesce(public.current_app_role() = 'admin' and (auth.jwt() ->> 'aal') = 'aal2', false)
$$;

-- Active account check (blocked users keep read access to their history but cannot write).
create or replace function public.is_active_user()
returns boolean language sql stable security definer set search_path = ''
as $$
  select exists (select 1 from public.profiles where id = auth.uid() and is_active)
$$;
revoke all on function public.is_active_user() from public;
grant execute on function public.is_active_user() to anon, authenticated;

-- ---------- 3. Owner write policies require an active account ----------
drop policy profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles for update to authenticated
  using (id = auth.uid() and public.is_active_user()) with check (id = auth.uid());

drop policy customers_update_own on public.customers;
create policy customers_update_own on public.customers for update to authenticated
  using (id = auth.uid() and public.is_customer()) with check (id = auth.uid());

drop policy cart_items_owner on public.cart_items;
create policy cart_items_owner_read on public.cart_items for select to authenticated
  using (exists (select 1 from public.carts c where c.id = cart_id and c.customer_id = auth.uid()));
create policy cart_items_owner_insert on public.cart_items for insert to authenticated
  with check (public.is_customer()
              and exists (select 1 from public.carts c where c.id = cart_id and c.customer_id = auth.uid()));
create policy cart_items_owner_update on public.cart_items for update to authenticated
  using (public.is_customer() and exists (select 1 from public.carts c where c.id = cart_id and c.customer_id = auth.uid()))
  with check (exists (select 1 from public.carts c where c.id = cart_id and c.customer_id = auth.uid()));
create policy cart_items_owner_delete on public.cart_items for delete to authenticated
  using (public.is_customer() and exists (select 1 from public.carts c where c.id = cart_id and c.customer_id = auth.uid()));

drop policy addresses_owner on public.delivery_addresses;
create policy addresses_owner_read on public.delivery_addresses for select to authenticated
  using (customer_id = auth.uid());
create policy addresses_owner_insert on public.delivery_addresses for insert to authenticated
  with check (customer_id = auth.uid() and public.is_customer());
create policy addresses_owner_update on public.delivery_addresses for update to authenticated
  using (customer_id = auth.uid() and public.is_customer()) with check (customer_id = auth.uid());
create policy addresses_owner_delete on public.delivery_addresses for delete to authenticated
  using (customer_id = auth.uid() and public.is_customer());

drop policy favorites_owner on public.favorites;
create policy favorites_owner_read on public.favorites for select to authenticated
  using (customer_id = auth.uid());
create policy favorites_owner_insert on public.favorites for insert to authenticated
  with check (customer_id = auth.uid() and public.is_customer());
create policy favorites_owner_delete on public.favorites for delete to authenticated
  using (customer_id = auth.uid() and public.is_customer());

drop policy device_tokens_owner on public.device_tokens;
create policy device_tokens_owner_read on public.device_tokens for select to authenticated
  using (user_id = auth.uid());
create policy device_tokens_owner_insert on public.device_tokens for insert to authenticated
  with check (user_id = auth.uid() and public.is_active_user());
create policy device_tokens_owner_update on public.device_tokens for update to authenticated
  using (user_id = auth.uid() and public.is_active_user()) with check (user_id = auth.uid());
create policy device_tokens_owner_delete on public.device_tokens for delete to authenticated
  using (user_id = auth.uid());     -- signing out must always be able to remove the token

drop policy notifications_mark_read on public.notifications;
create policy notifications_mark_read on public.notifications for update to authenticated
  using (user_id = auth.uid() and public.is_active_user()) with check (user_id = auth.uid());

-- ---------- 4. Minimal API surface: nothing callable unless explicitly granted ----------
-- PostgreSQL grants EXECUTE to PUBLIC by default; remove it for helpers and for future functions.
revoke all on function public.luhn_check_digit(text) from public, anon, authenticated;
revoke all on function public.set_updated_at() from public, anon, authenticated;
alter default privileges in schema public revoke execute on functions from public;
