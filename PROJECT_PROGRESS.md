# PROJECT_PROGRESS

> Yeni kontekstdə işə başlamazdan əvvəl bu faylı oxuyun. Hər mərhələnin sonunda yenilənir.

## Vəziyyət

| # | Mərhələ | Status |
|---|---|---|
| 1 | Layihənin təməli | ✅ Tamamlandı (istifadəçi təsdiqi gözlənilir) |
| 2 | Backend və məlumat bazası (Supabase) | ⏳ Növbəti |
| 3 | Qeydiyyat və giriş | — |
| 4 | Müştəri ana səhifəsi | — |
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

**Cari mərhələ:** 1 tamamlandı → Mərhələ 2 istifadəçi təsdiqindən sonra başlayacaq.

## Mərhələ 1 — nə edildi

- Gradle 8.14.3 wrapper, AGP 8.13.2, Kotlin 2.2.21, KSP, Hilt 2.57.2, Compose BOM 2025.12.00, Navigation 2.9.6 (versiya kataloqu: `gradle/libs.versions.toml`).
- `minSdk 29` (Android 10), `targetSdk/compileSdk 36`; paket `az.market.app` (debug: `az.market.app.debug`).
- Material 3 tema: açıq/qaranlıq, runtime-da dəyişən brend rəngi, endirim vurğusu, tipoqrafiya, formalar.
- MVVM: `RootViewModel` + `SessionRepository` (hazırda `UnauthenticatedSessionRepository` — həmişə "çıxış edilib").
- Type-safe naviqasiya, rol əsaslı başlanğıc (`startDestinationFor`), müştəri üçün 5 bölməli aşağı menyu, admin və kuryer shell-ləri.
- Splash screen (core-splashscreen), adaptiv tətbiq ikonu (orijinal səbət loqosu), Azərbaycan dilində interfeys.
- Release: R8 minify + resource shrink; backup söndürülüb (sessiya tokenləri köçürülməsin).
- Debug-only "rol interfeyslərinə baxış" düymələri (release-də yoxdur).

## Yaradılmış əsas fayllar

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`
- `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- `app/src/main/java/az/market/app/**` (core, data/session, navigation, feature/auth|customer|admin|courier)
- `app/src/main/res/**` (strings, themes, splash, ikon)
- `app/src/test/**` (Money, UserRole, StartDestination, RootViewModel, Robolectric UI testləri)
- `README.md`, `docs/ARCHITECTURE.md`, `docs/screenshots/*`

## Test nəticələri (Mərhələ 1)

- 15/15 test keçdi (9 unit + 6 Robolectric UI: tətbiqin açılması, giriş, müştəri menyusu, admin, kuryer, qaranlıq rejim).
- `assembleDebug`, `assembleRelease` uğurlu; lint: 0 xəta (yalnız "yeni versiya var" xəbərdarlıqları — qəsdən sabit versiya dəsti).
- Emulyator/real cihazda yoxlama: bu mühitdə KVM yoxdur — real cihazda quraşdırma Mərhələ 15-də (və istənilən vaxt istifadəçi tərəfindən) yoxlanılmalıdır.

## Backend vəziyyəti

- Hələ qoşulmayıb. Mərhələ 2-də Supabase layihəsi tələb olunur.

## Açıq qalan məsələlər / istifadəçidən lazım olanlar

1. **Market adı (brend)** və **paket adı** (`applicationId`, hazırda `az.market.app`) — Play Store-a yükləndikdən sonra paket adı dəyişdirilə bilməz.
2. **Supabase:** Project URL və **anon public key** (service_role key-i APK-ya qoyulmayacaq; yalnız server funksiyalarında istifadə üçün ayrıca verilməlidir).
3. **SMS təsdiqi (Mərhələ 3):** Supabase Phone Auth üçün SMS provayderi (Twilio / MessageBird / Vonage və ya yerli provayder) hesabı.
4. **Firebase (Mərhələ 12):** `google-services.json` və FCM üçün service account.
5. **Xəritə (Mərhələ 8/10):** Google Maps API key (və ya OpenStreetMap seçimi).
6. Debug APK ölçüsü (~63 MB) material-icons-extended səbəbindəndir; release-də R8 ilə ~2 MB.

## Növbəti iş

Mərhələ 2 — Supabase bağlantısı, SQL migration-lar (22 cədvəl), RLS, Storage, Realtime.
