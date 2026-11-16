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
