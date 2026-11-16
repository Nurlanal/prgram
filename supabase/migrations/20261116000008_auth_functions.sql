-- =====================================================================
-- 0008 — Stage 3: authentication helpers
--   * Registration stores the delivery address atomically with the account.
--   * get_my_account(): one call that tells the app who is signed in and with which role.
--   * Courier accounts are created only by an admin (with MFA) — see the Edge Function
--     supabase/functions/admin-create-courier, which calls admin_make_courier().
--   * admin_set_user_active(): block / unblock accounts.
-- =====================================================================

-- ---------- Sign-up: profile + role + cart + bonus card + optional first address ----------
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
  meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  -- raw_user_meta_data is user-controlled: never trust it for identity (phone / role).
  v_phone text := nullif(new.phone, '');
  v_address text := left(btrim(coalesce(meta ->> 'address_line', '')), 300);
  v_settlement text := left(btrim(coalesce(meta ->> 'settlement', '')), 120);
  v_address_id uuid;
begin
  if v_phone is not null and left(v_phone, 1) <> '+' then
    v_phone := '+' || v_phone;
  end if;

  insert into public.profiles (id, first_name, last_name, phone, email)
  values (new.id,
          left(btrim(coalesce(meta ->> 'first_name', '')), 80),
          left(btrim(coalesce(meta ->> 'last_name', '')), 80),
          v_phone, new.email);

  insert into public.user_roles (user_id, role) values (new.id, 'customer');
  insert into public.customers (id) values (new.id);
  insert into public.carts (customer_id) values (new.id);
  insert into public.bonus_cards (customer_id, card_number)
  values (new.id, public.next_bonus_card_number());

  -- The address the customer typed in the registration form (it is their own data).
  if char_length(v_address) >= 3 then
    insert into public.delivery_addresses (customer_id, label, address_line, settlement, contact_phone, is_default)
    values (new.id, 'Ev', v_address, v_settlement, v_phone, true)
    returning id into v_address_id;
    update public.customers set default_address_id = v_address_id where id = new.id;
  end if;
  return new;
end;
$$;
revoke all on function public.handle_new_user() from public, anon, authenticated;

-- ---------- Who am I? (role always read from the database) ----------
create or replace function public.get_my_account()
returns jsonb
language sql stable security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'user_id',    p.id,
    'role',       r.role,
    'is_active',  p.is_active,
    'first_name', p.first_name,
    'last_name',  p.last_name,
    'phone',      p.phone,
    'email',      p.email
  )
  from public.profiles p
  join public.user_roles r on r.user_id = p.id
  where p.id = auth.uid()
$$;
revoke all on function public.get_my_account() from public, anon;
grant execute on function public.get_my_account() to authenticated;

-- ---------- Courier creation (second half; the auth user is created by the Edge Function) ----------
create or replace function public.admin_make_courier(p_user_id uuid, p_vehicle_info text default null)
returns void
language plpgsql security definer
set search_path = ''
as $$
declare
  v_role public.app_role;
begin
  if not public.is_admin() then
    raise exception 'permission denied: admin with MFA required' using errcode = '42501';
  end if;

  select role into v_role from public.user_roles where user_id = p_user_id for update;
  if v_role is null then
    raise exception 'user not found' using errcode = 'P0002';
  end if;
  if v_role <> 'customer' then
    raise exception 'user already has role %', v_role using errcode = '22023';
  end if;
  if exists (select 1 from public.orders where customer_id = p_user_id) then
    raise exception 'a customer with orders cannot be turned into a courier' using errcode = '22023';
  end if;

  update public.user_roles set role = 'courier', granted_by = auth.uid() where user_id = p_user_id;
  insert into public.couriers (id, vehicle_info, created_by)
  values (p_user_id, nullif(btrim(coalesce(p_vehicle_info, '')), ''), auth.uid());
end;
$$;
revoke all on function public.admin_make_courier(uuid, text) from public, anon;
grant execute on function public.admin_make_courier(uuid, text) to authenticated;

-- ---------- Block / unblock an account ----------
create or replace function public.admin_set_user_active(p_user_id uuid, p_active boolean)
returns void
language plpgsql security definer
set search_path = ''
as $$
begin
  if not public.is_admin() then
    raise exception 'permission denied: admin with MFA required' using errcode = '42501';
  end if;
  if p_user_id = auth.uid() then
    raise exception 'an admin cannot block their own account' using errcode = '22023';
  end if;
  update public.profiles set is_active = p_active where id = p_user_id;
  if not found then
    raise exception 'user not found' using errcode = 'P0002';
  end if;
  insert into public.audit_logs (actor_id, actor_role, action, table_name, record_id, new_data)
  values (auth.uid(), 'admin', case when p_active then 'UNBLOCK_USER' else 'BLOCK_USER' end,
          'profiles', p_user_id::text, jsonb_build_object('is_active', p_active));
end;
$$;
revoke all on function public.admin_set_user_active(uuid, boolean) from public, anon;
grant execute on function public.admin_set_user_active(uuid, boolean) to authenticated;
