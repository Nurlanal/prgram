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
