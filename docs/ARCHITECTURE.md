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
