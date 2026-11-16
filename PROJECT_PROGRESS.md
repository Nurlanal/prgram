# PROJECT_PROGRESS

> Yeni kontekstdə işə başlamazdan əvvəl bu faylı oxuyun. Hər mərhələnin sonunda yenilənir.

## Vəziyyət

| # | Mərhələ | Status |
|---|---|---|
| 1 | Layihənin təməli | ✅ Tamamlandı, təsdiqləndi |
| 2 | Backend və məlumat bazası (Supabase) | ✅ Təsdiqləndi; canlı Supabase bağlantısı açarlar gələndə |
| 3 | Qeydiyyat və giriş | ✅ Kod tamamlandı (təsdiq gözlənilir); canlı SMS/Auth yoxlaması açarlar + SMS provayderi gələndə |
| 4 | Müştəri ana səhifəsi | ⏳ Növbəti |
| 5 | Məhsul və kateqoriyalar | — |
| 6 | Səbət | — |
| 7 | Sifariş sistemi | — |
| 8 | Çatdırılma | — |
| 9 | Admin paneli | — |
| 10 | Kuryer paneli | — |
| 11 | Bonus kartı | — |
| 12 | Endirim və bildirişlər (FCM) | — |
| 13 | Profil və əlavə funksiyalar | — |
| 14 | Təhlükəsizlik və tam test | — |
| 15 | APK build (imzalı release) | — |

**Cari mərhələ:** 3 tamamlandı → Mərhələ 4 üçün istifadəçi təsdiqi gözlənilir.

## Mərhələ 1 — nə edildi

- Gradle 8.14.3 wrapper, AGP 8.13.2, Kotlin 2.2.21, KSP, Hilt 2.57.2, Compose BOM 2025.12.00, Navigation 2.9.6 (versiya kataloqu: `gradle/libs.versions.toml`).
- `minSdk 29` (Android 10), `targetSdk/compileSdk 36`; paket `az.market.app` (debug: `az.market.app.debug`).
- Material 3 tema: açıq/qaranlıq, runtime-da dəyişən brend rəngi, endirim vurğusu, tipoqrafiya, formalar.
- MVVM: `RootViewModel` + `SessionRepository` (hazırda `UnauthenticatedSessionRepository` — həmişə "çıxış edilib").
- Type-safe naviqasiya, rol əsaslı başlanğıc (`startDestinationFor`), müştəri üçün 5 bölməli aşağı menyu, admin və kuryer shell-ləri.
- Splash screen (core-splashscreen), adaptiv tətbiq ikonu (orijinal səbət loqosu), Azərbaycan dilində interfeys.
- Release: R8 minify + resource shrink; backup söndürülüb (sessiya tokenləri köçürülməsin).
- Debug-only "rol interfeyslərinə baxış" düymələri (release-də yoxdur).

## Mərhələ 3 — nə edildi

- **Müştəri qeydiyyatı:** ad, soyad, telefon, şifrə, kənd/şəhər, ünvan → Supabase `signUp` (telefon) → SMS kodu → təsdiq. Ünvan DB trigger-i ilə hesabla eyni tranzaksiyada saxlanılır. Kodu yenidən göndərmə (60 san. sayğac), nömrəni dəyişmə.
- **Giriş:** müştəri və kuryer telefon + şifrə; admin email + şifrə + **TOTP MFA** (ilk girişdə QR ilə qoşulma). Admin formasından admin olmayan hesab daxil ola bilmir.
- **Şifrə bərpası:** telefon (SMS kodu) və ya admin email (email kodu) → "yeni şifrə" ekranı (sessiya vəziyyəti `PasswordRecovery`); hesabın olub-olmadığı açıqlanmır; bərpa kodu ilə yeni hesab yaranmır.
- **Rol əsaslı yönləndirmə:** `get_my_account()` (rol yalnız serverdən) → Müştəri / Admin (MFA-dan sonra) / Kuryer; bloklanmış hesab, offline başlanğıc (yenidən cəhd) ekranları; hər shell-də "Çıxış".
- **Kuryer hesabı:** admin paneli → Kuryerlər → forma → Edge Function `admin-create-courier` (service_role yalnız serverdə) → `admin_make_courier()` (MFA-lı admin, audit). Xəta olsa auth istifadəçisi geri silinir.
- **Sessiya təhlükəsizliyi:** tokenlər Android Keystore AES-256-GCM ilə şifrələnir; şifrə cihazda saxlanılmır; supabase-kt-nin şifrələnməmiş default settings faylı ləğv edildi (PKCE yaddaşda).
- Migration `20261116000008_auth_functions.sql`: qeydiyyat ünvanı, `get_my_account`, `admin_make_courier`, `admin_set_user_active`.

## Mərhələ 2 — nə edildi

- `supabase/migrations/` — 6 migration: enum-lar, 26 cədvəl (tələb olunan 22 + `product_costs`, `favorites`, `order_status_history`, `device_tokens`), funksiyalar/trigger-lər, RLS + açıq hüquqlar, Storage (3 bucket) + Realtime, seed (12 kateqoriya, parametrlər).
- Server qaydaları: qeydiyyatda rol həmişə `customer` (metadata-dakı rol nəzərə alınmır); `bootstrap_first_admin()` yalnız SQL Editor-dan; sonuncu admin silinə bilməz; deaktiv admin dərhal girişini itirir; işçiyə çevrilən hesabın müştəri datası silinir.
- Bütövlük: stok ≥ 0, bonus ≥ 0, sifariş məbləği formulu, idempotency açarı, eyni sifarişə bonus bir dəfə, "paid" yalnız təsdiqlə, unikal sifariş nömrəsi `MK-YYMMDD-NNNNNN`, Luhn-lu 16 rəqəmli bonus kartı + təsadüfi QR token, audit log.
- Android: supabase-kt 3.2.6 (Auth, Postgrest, Storage, Realtime) + Ktor OkHttp; açarlar `local.properties`/env → BuildConfig; `service_role` açarı daxil edilərsə build dayanır.
- `StoreSettingsRepository`: `app_settings` oxunur və Realtime ilə izlənir → brend rəngi/market adı tətbiqdə canlı dəyişir. Debug panelində server statusu.
- `docs/BACKEND_SETUP.md`, `supabase/schema_full.sql`, `docs/test-reports/stage2-database.md`.

## Mərhələ 2 — təhlükəsizlik yoxlaması (istifadəçi tələbi ilə)

Hesabat: `docs/test-reports/stage2-security-review.md`.

- Parol heç bir public cədvəldə saxlanılmır (avtomatik test ilə qorunur); parollar yalnız Supabase Auth-da bcrypt hash kimi.
- Yeni migration `20261116000007_security_hardening.sql`:
  1. Admin hüquqları rol + **MFA (aal2)** tələb edir.
  2. Telefon yalnız təsdiqlənmiş `auth.users.phone`-dan (nömrə zəbti / qeydiyyat DoS düzəldildi).
  3. Bloklanmış hesablar yaza bilmir.
  4. `PUBLIC` funksiya icazələri bağlandı.
- 41 yeni DB yoxlaması (cəmi 135): parol sütunu yoxdur, RLS hər cədvəldə, view yoxdur, anon yalnız kataloq oxuyur, saxta JWT claim-ləri, metadata ilə rol dəyişmə cəhdi, MFA-sız admin, kuryerin admin datasına girişi, telefon zəbti, bloklanmış hesab.
- Yeni testlər düzəlişsiz migration-lara qarşı işlədildi: 9 uğursuzluq + çökmə (problemlər real idi), düzəlişdən sonra 135/135.
- Gradle `verifyNoSecretsIn{Debug,Release}Apk` — hər build-dən sonra APK skan olunur; yerləşdirilmiş saxta service_role açarı ilə yoxlanıldı (build dayandı).

## Yaradılmış əsas fayllar

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`
- `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- `app/src/main/java/az/market/app/**` (core, data/session, navigation, feature/auth|customer|admin|courier)
- `app/src/main/res/**` (strings, themes, splash, ikon)
- `app/src/test/**` (Money, UserRole, StartDestination, RootViewModel, Robolectric UI testləri)
- `README.md`, `docs/ARCHITECTURE.md`, `docs/screenshots/*`

## Test nəticələri (Mərhələ 3)

- Verilənlər bazası: **156/156** (21 yeni: qeydiyyat ünvanı, get_my_account, kuryer yaratma icazələri — müştəri/kuryer/MFA-sız admin rədd edilir, bloklama).
- Edge Function: **8/8** (Node test runner; MFA-sız admin rədd, telefon artıq var → 409, uğursuz rol → istifadəçi silinir, şifrə cavabda yoxdur).
- Android: **82/82** — yeni: sessiya vəziyyətləri (12), şifrələnmiş sessiya (3), ViewModel-lər (18), real supabase-kt ilə HTTP səviyyəli Auth/Functions testləri (12), ekran UI testləri (6), telefon/şifrə validasiyası.
- debug + release build, lint 0 xəta, APK skaneri: server sirri yoxdur.
- **Yoxlanılmayıb:** real SMS göndərilməsi, real Supabase Auth, Edge Function-un Supabase-də işləməsi, cihazda Keystore (Robolectric-də proqram açarı ilə test olunub).

## Test nəticələri (Mərhələ 2)

- Verilənlər bazası: 135/135 yoxlama (təhlükəsizlik yoxlamasından sonra; `supabase/tests/run_local_tests.sh`, PostgreSQL 16 + Supabase shim).
- APK skaneri: debug + release APK-da server sirri yoxdur (yalnız gözlənilən public anon key).
- Android: 26/26 test (yeni: BackendConfig, DTO kontrakt testləri real DB sətirləri ilə, MockEngine üzərindən real supabase-kt sorğuları, RootViewModel Realtime/status).
- debug + release build, lint 0 xəta. Gizli açar qoruyucusu yoxlanıldı (service_role açarı ilə build dayanır).
- **Yoxlanılmayıb:** real Supabase layihəsinə canlı qoşulma (açarlar hələ verilməyib); release APK-nın cihazda işləməsi.

## Test nəticələri (Mərhələ 1)

- 15/15 test keçdi (9 unit + 6 Robolectric UI: tətbiqin açılması, giriş, müştəri menyusu, admin, kuryer, qaranlıq rejim).
- `assembleDebug`, `assembleRelease` uğurlu; lint: 0 xəta (yalnız "yeni versiya var" xəbərdarlıqları — qəsdən sabit versiya dəsti).
- Emulyator/real cihazda yoxlama: bu mühitdə KVM yoxdur — real cihazda quraşdırma Mərhələ 15-də (və istənilən vaxt istifadəçi tərəfindən) yoxlanılmalıdır.

## Backend vəziyyəti

- Sxem, RLS, Storage, Realtime kodu hazırdır və lokal PostgreSQL-də test olunub.
- Real Supabase layihəsi hələ yoxdur → istifadəçi `docs/BACKEND_SETUP.md` üzrə yaradıb URL + anon key verməlidir.
- Klient yazma yolu olmayan cədvəllər (orders, bonus, rollar, stok sayı) üçün server funksiyaları sonrakı mərhələlərdə: rol/kuryer (3, 10), sifariş+stok (7), bonus (11), stok idarəsi (9).
- Admin MFA ekranı hazırdır (Mərhələ 3).
- Real layihədə Auth parametrləri (`docs/BACKEND_SETUP.md` §4: min. parol uzunluğu, MFA TOTP, anonim girişin söndürülməsi) əl ilə qurulmalıdır.

## Açıq qalan məsələlər / istifadəçidən lazım olanlar

1. **Market adı (brend)** və **paket adı** (`applicationId`, hazırda `az.market.app`) — Play Store-a yükləndikdən sonra paket adı dəyişdirilə bilməz.
2. **Supabase:** Project URL və **anon public key** (service_role key-i APK-ya qoyulmayacaq; yalnız server funksiyalarında istifadə üçün ayrıca verilməlidir).
3. **SMS təsdiqi (Mərhələ 3):** Supabase Phone Auth üçün SMS provayderi (Twilio / MessageBird / Vonage və ya yerli provayder) hesabı.
4. **Firebase (Mərhələ 12):** `google-services.json` və FCM üçün service account.
5. **Xəritə (Mərhələ 8/10):** Google Maps API key (və ya OpenStreetMap seçimi).
6. Debug APK ölçüsü (~63 MB) material-icons-extended səbəbindəndir; release-də R8 ilə ~2 MB.

## Mərhələ 3 üçün xarici konfiqurasiya (hələ edilməyib)

- Supabase layihəsi + URL/anon key → `local.properties`.
- SMS provayderi (Phone provider) — qeydiyyat və SMS ilə şifrə bərpası üçün.
- Magic Link email şablonuna `{{ .Token }}` — admin şifrə bərpası üçün.
- MFA TOTP aktiv; `supabase functions deploy admin-create-courier`.

## Növbəti iş

1. İstifadəçi Supabase URL + anon key verəndə: migration-ları tətbiq etmək, canlı bağlantı testi.
2. Mərhələ 4 — Müştəri ana səhifəsi (bannerlər, kateqoriyalar, endirimli/yeni/populyar məhsullar, axtarış, bonus balansı).
