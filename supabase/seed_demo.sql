-- =====================================================================
-- OPTIONAL DEMO DATA — NOT a migration. Run only on a test project to try the app
-- before real products are entered in the admin panel (Stage 9).
-- Every demo row is marked with sku 'DEMO-%' / banner title prefix '[DEMO]'.
-- Remove everything with the block at the end of this file.
-- =====================================================================
insert into public.products (category_id, name, description, sku, unit, price, sale_price, is_featured)
select c.id, v.name, v.description, v.sku, v.unit::public.unit_type, v.price, v.sale_price, v.featured
from (values
  ('erzaq',            'Düyü Basmati 1kg',       'Uzun dənəli düyü',        'DEMO-001', 'kg',    4.20, 3.69, true),
  ('erzaq',            'Un 2kg',                 'Əla növ buğda unu',       'DEMO-002', 'pack',  2.60, null, false),
  ('sud-mehsullari',   'Süd 3.2% 1L',            'Pasterizə edilmiş süd',   'DEMO-003', 'liter', 2.10, null, true),
  ('sud-mehsullari',   'Qatıq 500q',             'Təbii qatıq',             'DEMO-004', 'piece', 1.30, 1.09, false),
  ('ickiler',          'Mineral su 1.5L',        'Qazsız',                  'DEMO-005', 'liter', 0.70, null, false),
  ('ickiler',          'Alma şirəsi 1L',         '100% şirə',               'DEMO-006', 'liter', 2.40, 1.99, false),
  ('sirniyyat',        'Şokolad 90q',            'Südlü şokolad',           'DEMO-007', 'piece', 1.80, null, true),
  ('meyve',            'Alma',                   'Quba alması',             'DEMO-008', 'kg',    1.50, null, false),
  ('terevez',          'Pomidor',                'Yerli pomidor',           'DEMO-009', 'kg',    2.20, 1.79, false),
  ('toyuq-mehsullari', 'Toyuq filesi 1kg',       'Soyudulmuş',              'DEMO-010', 'kg',    8.90, null, false),
  ('temizlik-vasiteleri','Qab yuyucu 500ml',     'Limon ətirli',            'DEMO-011', 'ml',    2.95, null, false),
  ('usaq-mehsullari',  'Uşaq bezi (4) 50 əd.',   'Gecə-gündüz',             'DEMO-012', 'pack', 18.50, 15.90, false)
) as v(slug, name, description, sku, unit, price, sale_price, featured)
join public.categories c on c.slug = v.slug
on conflict (sku) do nothing;

update public.inventory i set quantity = 25
from public.products p where p.id = i.product_id and p.sku like 'DEMO-%' and p.sku <> 'DEMO-010';  -- DEMO-010 stays out of stock

insert into public.banners (title, subtitle, image_url, link_type, link_value, sort_order)
select '[DEMO] Ərzaq həftəsi', 'Seçilmiş məhsullarda endirim', '', 'category', c.id::text, 1
from public.categories c where c.slug = 'erzaq'
and not exists (select 1 from public.banners where title = '[DEMO] Ərzaq həftəsi');

-- ---------- Remove all demo data ----------
-- delete from public.banners  where title like '[DEMO]%';
-- delete from public.products where sku like 'DEMO-%';
