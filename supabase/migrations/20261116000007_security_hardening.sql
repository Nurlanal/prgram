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
