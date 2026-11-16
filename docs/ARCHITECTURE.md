# Arxitektura

## Ümumi baxış

```
┌──────────────────────── Android APK (bir tətbiq, 3 rol) ────────────────────────┐
│  UI (Compose screens)  →  ViewModel (StateFlow)  →  Repository  →  Data source  │
│                                                       │                         │
└───────────────────────────────────────────────────────┼─────────────────────────┘
                                                        │ HTTPS (anon key + user JWT)
┌───────────────────────────── Supabase (Mərhələ 2+) ───┴─────────────────────────┐
│ Auth │ PostgreSQL + RLS │ RPC (security definer) │ Edge Functions │ Storage │ RT │
└──────────────────────────────────────────────────────────────┬──────────────────┘
                                                               │ service account
                                                     Firebase Cloud Messaging
```

## Prinsiplər

1. **Server həqiqət mənbəyidir.** Rol (`user_roles`), qiymət, stok, bonus balansı, sifariş məbləği yalnız serverdə saxlanılır və hesablanır. Tətbiq yalnız göstərir.
2. **Rol əsaslı naviqasiya.** `SessionRepository` serverdən `SessionState.SignedIn(userId, role)` verir; `startDestinationFor()` müvafiq interfeysi seçir. Naməlum rol → heç bir giriş (`UserRole.fromServerValue` null qaytarır).
3. **Rollar arasında izolyasiya.** Root `NavHost` sessiya dəyişdikdə yenidən yaradılır — bir rolun back-stack-i digərinə keçmir.
4. **Atomik əməliyyatlar.** Sifariş yaratma, stok azaltma, bonus qazanma/xərcləmə və ləğv PostgreSQL funksiyalarında bir tranzaksiyada, idempotency açarı ilə (Mərhələ 2, 7, 11).
5. **Pul = qəpik (Long).** `Money` tipi floating-point xətalarının qarşısını alır.
6. **Yarımçıq funksiya işlək kimi göstərilmir.** Hazır olmayan bölmələr `FeaturePlaceholder` ilə hansı mərhələdə qurulacağını açıq göstərir.

## Qatlar

| Qat | Məsul olduğu iş | Nümunə |
|---|---|---|
| `feature/*` | Compose ekranları, ekrana aid ViewModel | `CustomerShell`, `AdminShell` |
| `navigation` | Marşrutlar, rol → başlanğıc ekranı | `Routes.kt`, `MarketNavHost` |
| `data` | Repository interfeysləri və implementasiyaları | `SessionRepository` |
| `core` | Tema, ümumi komponentlər, modellər, DI | `MarketTheme`, `Money` |

## Tema

`MarketTheme(themeMode, brand)` — `BrandColors` (əsas + vurğu rəngi) admin parametrlərindən gələcək; bütün Material 3 sxemi bu rənglərdən avtomatik törədilir (açıq və qaranlıq). Endirim üçün ayrıca semantik rəng (`LocalMarketColors.discount`).

## Backend (Mərhələ 2)

Migration-lar: `supabase/migrations/` (tək fayl: `supabase/schema_full.sql`).

| Fayl | Məzmun |
|---|---|
| `…0001_extensions_and_types.sql` | enum-lar (rol, status, ödəniş, bonus, vahid…) |
| `…0002_tables.sql` | 22 tələb olunan cədvəl + `product_costs`, `favorites`, `order_status_history`, `device_tokens` |
| `…0003_functions_and_triggers.sql` | rol helper-ləri, qeydiyyat trigger-i, sifariş/kart nömrəsi, audit, ilk admin |
| `…0004_rls_and_grants.sql` | RLS siyasətləri + açıq hüquqlar |
| `…0005_storage_and_realtime.sql` | bucket-lər, storage siyasətləri, Realtime |
| `…0006_seed.sql` | 12 kateqoriya, `app_settings` |

### İcazə matrisi (qısa)

| Məlumat | anon | müştəri | kuryer | admin |
|---|---|---|---|---|
| Kataloq, stok, banner, kampaniya, zona, parametrlər | oxu (aktiv) | oxu (aktiv) | oxu (aktiv) | oxu + yaz |
| Alış qiyməti (`product_costs`) | — | — | — | oxu + yaz |
| Profil | — | öz (ad/soyad redaktə) | öz | hamısı (oxu) |
| Rol (`user_roles`) | — | öz (oxu) | öz (oxu) | oxu; yazı yalnız server funksiyası |
| Səbət, favorit, ünvan, cihaz tokeni | — | öz (yaz) | — | ünvan oxu |
| Sifariş + məhsulları | — | öz (oxu) | yalnız təyin edilən (oxu) | hamısı (oxu) |
| Bonus kartı / tarixçə | — | öz (oxu) | — | hamısı (oxu) |
| Audit log | — | — | — | oxu |

**Pul, stok, bonus, sifariş və rol** üçün klientdə yazma yolu **yoxdur** — bunlar yalnız `SECURITY DEFINER` server funksiyaları ilə dəyişəcək (Mərhələ 3, 7, 10, 11). Bazada əlavə zəmanətlər: `stock >= 0`, `bonus balance >= 0`, `total = subtotal − discount − bonus + fee`, sifarişdə `idempotency_key`, eyni sifariş üçün bonus bir dəfə, sonuncu admin silinə bilməz.

### Android ↔ Supabase

- `SupabaseModule` → `BackendConfig` (BuildConfig) → `SupabaseProvider` (açar yoxdursa `null`, UI bunu açıq göstərir).
- `createMarketSupabaseClient()` — Auth, Postgrest, Storage, Realtime; OkHttp engine.
- `StoreSettingsRepository` — `app_settings` + Realtime: admin brend rəngini/adı dəyişəndə tətbiq dərhal yenilənir.
- Kontrakt testləri: DTO-lar real bazadan ixrac olunmuş JSON ilə yoxlanılır (`app/src/test/resources/fixtures`).
