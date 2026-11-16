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
