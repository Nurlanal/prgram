-- =====================================================================
-- 0009 — Stage 4: server-side pricing, catalogue queries and the home feed
--
-- All prices shown in the app are computed HERE, never on the phone:
--   final price = the lowest of
--     * the regular price,
--     * the product's own sale price while its sale window is open,
--     * every active promotion on the product or its category (percent or fixed price).
-- These functions are SECURITY INVOKER (RLS applies). The only SECURITY DEFINER helper returns
-- anonymous sales totals per product (no customer or order data).
-- =====================================================================

-- ---------- Search normalisation: "seker" finds "Şəkər", "cay" finds "Çay" ----------
create or replace function public.az_fold(p text)
returns text
language sql immutable parallel safe
set search_path = ''
as $$
  -- Code points written explicitly so the mapping cannot be broken by Unicode normalisation of the file:
  --   upper Ə Ö Ü Ğ İ I Ş Ç  →  lower ə ö ü ğ i ı ş ç   (works under any database collation)
  --   then ə ö ü ğ ı ş ç     →  e o u g i s c           (users often type without Azerbaijani letters)
  select translate(
           lower(translate(coalesce(p, ''),
                           U&'\018F\00D6\00DC\011E\0130\0049\015E\00C7',
                           U&'\0259\00F6\00FC\011F\0069\0131\015F\00E7')),
           U&'\0259\00F6\00FC\011F\0131\015F\00E7',
           'eougisc')
$$;

-- ---------- Effective price of one product ----------
create or replace function public.effective_price(p public.products)
returns numeric
language sql stable
set search_path = ''
as $$
  select least(
    p.price,
    case when p.sale_price is not null
              and (p.sale_starts_at is null or p.sale_starts_at <= now())
              and (p.sale_ends_at is null or p.sale_ends_at > now())
         then p.sale_price end,
    (select min(case pr.discount_type
                  when 'percent' then round(p.price * (100 - pr.discount_value) / 100, 2)
                  else pr.discount_value end)
       from public.promotions pr
      where pr.is_active and pr.starts_at <= now() and pr.ends_at > now()
        and (pr.product_id = p.id or (pr.product_id is null and pr.category_id = p.category_id)))
  )
$$;

-- ---------- Anonymous popularity (units sold in the last 30 days, completed + open orders) ----------
create or replace function public.product_sales_last_30_days()
returns table (product_id uuid, units numeric)
language sql stable security definer
set search_path = ''
as $$
  select oi.product_id, sum(oi.quantity)
  from public.order_items oi
  join public.orders o on o.id = oi.order_id
  where o.status <> 'cancelled' and o.created_at > now() - interval '30 days' and oi.product_id is not null
  group by oi.product_id
$$;
revoke all on function public.product_sales_last_30_days() from public;
-- Needed by the invoker-rights catalogue query; exposes only anonymous per-product totals.
grant execute on function public.product_sales_last_30_days() to anon, authenticated;

-- ---------- The catalogue query used by home sections, search and category pages ----------
create or replace function public.catalog_products(
  p_category_id     bigint  default null,
  p_search          text    default null,
  p_only_discounted boolean default false,
  p_only_featured   boolean default false,
  p_sort            text    default 'popular',   -- popular | new | discount | price_asc | price_desc | name
  p_limit           integer default 20,
  p_offset          integer default 0
)
returns table (
  id               uuid,
  category_id      bigint,
  name             text,
  unit             public.unit_type,
  image_url        text,
  price            numeric,
  final_price      numeric,
  discount_percent integer,
  in_stock         boolean,
  stock            numeric,
  is_new           boolean,
  sold_30d         numeric
)
language sql stable
set search_path = ''
as $$
  with base as (
    select p.*, public.effective_price(p) as final_price,
           coalesce(inv.quantity, 0) as stock,
           coalesce(s.units, 0) as sold
    from public.products p
    left join public.inventory inv on inv.product_id = p.id
    left join public.product_sales_last_30_days() s on s.product_id = p.id
    where p.is_active                                   -- customers never see inactive products
      and (p_category_id is null or p.category_id = p_category_id)
      and (not p_only_featured or p.is_featured)
      and (p_search is null or btrim(p_search) = ''
           or p.barcode = btrim(p_search)
           or p.sku = btrim(p_search)
           or public.az_fold(p.name || ' ' || p.description) like '%' || public.az_fold(btrim(p_search)) || '%')
  )
  select b.id, b.category_id, b.name, b.unit, b.main_image_url, b.price, b.final_price,
         case when b.final_price < b.price then round((1 - b.final_price / b.price) * 100)::integer else 0 end,
         b.stock > 0, b.stock,
         b.created_at > now() - interval '14 days',
         b.sold
  from base b
  where not p_only_discounted or b.final_price < b.price
  order by
    case when p_sort = 'popular' then b.sold end desc nulls last,
    case when p_sort = 'new' then b.created_at end desc,
    case when p_sort = 'discount' then (b.price - b.final_price) / b.price end desc,
    case when p_sort = 'price_asc' then b.final_price end asc,
    case when p_sort = 'price_desc' then b.final_price end desc,
    (b.stock > 0) desc,
    b.name asc
  limit least(greatest(coalesce(p_limit, 20), 1), 100)
  offset greatest(coalesce(p_offset, 0), 0)
$$;

-- ---------- Home feed: every section in one round trip ----------
create or replace function public.home_feed(p_section_limit integer default 10)
returns jsonb
language plpgsql stable
set search_path = ''
as $$
declare
  n integer := least(greatest(coalesce(p_section_limit, 10), 1), 30);
  v_for_you jsonb;
  v_bonus jsonb;
  v_top_categories bigint[];
begin
  -- Separate statements: anonymous visitors have no privilege on orders / bonus_cards at all,
  -- and PostgreSQL checks table privileges for a whole statement before running it.
  -- "For you": the categories this customer buys most (own orders only, via RLS); else featured products.
  if public.is_customer() then
    select jsonb_build_object('balance', bc.balance, 'card_number', bc.card_number) into v_bonus
    from public.bonus_cards bc where bc.customer_id = auth.uid();

    select array_agg(category_id) into v_top_categories from (
      select p.category_id
      from public.orders o
      join public.order_items oi on oi.order_id = o.id
      join public.products p on p.id = oi.product_id
      where o.customer_id = auth.uid() and o.status <> 'cancelled' and p.category_id is not null
      group by p.category_id
      order by sum(oi.quantity) desc
      limit 3
    ) t;
  end if;

  if v_top_categories is not null then
    select coalesce(jsonb_agg(to_jsonb(c)), '[]') into v_for_you from (
      select * from (
        select cp.* from unnest(v_top_categories) as cat(id)
        cross join lateral public.catalog_products(p_category_id => cat.id, p_limit => n) cp
      ) x order by x.sold_30d desc, x.name limit n
    ) c;
  else
    select coalesce(jsonb_agg(to_jsonb(c)), '[]') into v_for_you
    from public.catalog_products(p_only_featured => true, p_limit => n) c;
  end if;

  return jsonb_build_object(
    'banners', (select coalesce(jsonb_agg(jsonb_build_object(
                    'id', b.id, 'title', b.title, 'subtitle', b.subtitle, 'image_url', b.image_url,
                    'link_type', b.link_type, 'link_value', b.link_value) order by b.sort_order, b.created_at), '[]')
                from public.banners b
                where b.is_active and (b.starts_at is null or b.starts_at <= now()) and (b.ends_at is null or b.ends_at > now())),
    'categories', (select coalesce(jsonb_agg(jsonb_build_object(
                       'id', c.id, 'name', c.name, 'slug', c.slug, 'icon_url', c.icon_url, 'image_url', c.image_url)
                       order by c.sort_order, c.name), '[]')
                   from public.categories c where c.is_active and c.parent_id is null),
    'discounted',   (select coalesce(jsonb_agg(to_jsonb(c)), '[]') from public.catalog_products(p_only_discounted => true, p_sort => 'discount', p_limit => n) c),
    'new_arrivals', (select coalesce(jsonb_agg(to_jsonb(c)), '[]') from public.catalog_products(p_sort => 'new', p_limit => n) c),
    'best_sellers', (select coalesce(jsonb_agg(to_jsonb(c)), '[]') from public.catalog_products(p_sort => 'popular', p_limit => n) c
                     where c.sold_30d > 0),
    'for_you', v_for_you,
    'bonus', v_bonus
  );
end;
$$;

-- ---------- Customer: choose the default delivery address shown in the header ----------
create or replace function public.set_default_address(p_address_id uuid)
returns void
language plpgsql
set search_path = ''
as $$
begin
  -- Runs as the caller: RLS guarantees the address and the customer row are their own.
  if not exists (select 1 from public.delivery_addresses where id = p_address_id and customer_id = auth.uid()) then
    raise exception 'address not found' using errcode = 'P0002';
  end if;
  update public.delivery_addresses set is_default = (id = p_address_id) where customer_id = auth.uid();
  update public.customers set default_address_id = p_address_id where id = auth.uid();
end;
$$;

-- ---------- API surface ----------
revoke all on function public.az_fold(text) from public;
revoke all on function public.effective_price(public.products) from public;
revoke all on function public.catalog_products(bigint, text, boolean, boolean, text, integer, integer) from public;
revoke all on function public.home_feed(integer) from public;
revoke all on function public.set_default_address(uuid) from public;
grant execute on function public.az_fold(text) to anon, authenticated;
grant execute on function public.effective_price(public.products) to anon, authenticated;
grant execute on function public.catalog_products(bigint, text, boolean, boolean, text, integer, integer) to anon, authenticated;
grant execute on function public.home_feed(integer) to anon, authenticated;
grant execute on function public.set_default_address(uuid) to authenticated;

-- Fast folded search.
create index if not exists products_name_fold_idx on public.products (public.az_fold(name));
