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
