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
