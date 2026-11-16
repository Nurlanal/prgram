# Mərhələ 2 — Təhlükəsizlik yoxlaması

Tarix: 2026-11-16 · Əhatə: `supabase/migrations/*.sql`, Android klienti, build konfiqurasiyası.

## Tələblər üzrə nəticə

| # | Tələb | Nəticə | Sübut |
|---|---|---|---|
| 1 | Parol açıq mətnlə saxlanılmasın | ✅ Heç bir public cədvəldə parol/hash/secret sütunu yoxdur | DB test: *no password / secret / hash column in any public table* |
| 2 | Parol idarəsi Supabase Auth ilə | ✅ Yalnız `auth.users.encrypted_password` (bcrypt); API rolları `auth.users`-i oxuya bilmir | DB test: *API users cannot read auth.users / password hashes* |
| 3 | `profiles`-də parol olmasın | ✅ `profiles` yalnız 9 qeyri-məxfi sahədən ibarətdir | DB test: *profiles contains only non-secret identity fields* |
| 4 | Rollar serverdə yoxlanılsın | ✅ Rol yalnız `user_roles`-dan; JWT/metadata-ya etibar edilmir | DB testləri: forged claims, metadata dəyişikliyi, naməlum istifadəçi |
| 5 | RLS bütün cədvəllərdə | ✅ 26/26 cədvəldə RLS + açıq siyasət; view yoxdur | DB testləri: *RLS enabled on every public table*, *every public table has explicit policies* |
| 6 | Admin hesabına icazəsiz giriş olmasın | ✅ Admin hüquqları rol **+ MFA (aal2)** tələb edir; admin qeydiyyatı yoxdur; bootstrap API-dən çağırıla bilməz | DB testləri: *admin without MFA …* (7 yoxlama) |
| 7 | Service Role Key APK-da olmasın | ✅ Build əvvəli qoruyucu + build sonrası APK skaneri | Gradle `verifyNoSecretsIn{Debug,Release}Apk`, `scripts/check_apk_secrets.py` |
| 8 | Migration-ların yoxlanması | ✅ 6 fayl nəzərdən keçirildi, 4 problem tapıldı və düzəldildi | aşağıda |
| 9 | Təhlükəsizlik testləri | ✅ 135/135 DB yoxlaması, Android testləri, APK skaneri | `docs/test-reports/stage2-database.md` |

## Tapılan və düzəldilən problemlər

Düzəlişlər ayrıca migration-dadır: `supabase/migrations/20261116000007_security_hardening.sql`.

| # | Problem | Risk | Düzəliş | Düzəlişdən əvvəl test nəticəsi |
|---|---|---|---|---|
| 1 | Admin hüquqları yalnız parolla verilirdi | Parol oğurlanarsa tam nəzarət | `is_admin()` indi `aal2` (parol + MFA kodu) tələb edir | Parolla daxil olan admin 4 profil, 38 audit qeydi, alış qiymətləri oxudu, kataloq dəyişdi, fayl yüklədi |
| 2 | Qeydiyyat telefonu yoxlanılmamış metadata-dan da götürürdü | Başqasının nömrəsini "zəbt etmək"; əsl sahib qeydiyyatdan keçə bilmirdi | Telefon yalnız təsdiqlənmiş `auth.users.phone`-dan | Əsl sahibin qeydiyyatı `duplicate key … profiles_phone_key` ilə çökdü |
| 3 | Bloklanmış hesab öz sətirlərinə yaza bilirdi | Bloklanmış istifadəçi səbət/ünvan/push tokeni dəyişir | Yazma siyasətləri aktiv hesab tələb edir | — |
| 4 | 2 köməkçi funksiya standart `PUBLIC` icazəsi ilə çağırıla bilirdi | Zərərsiz, amma lazımsız API səthi | `PUBLIC` icazəsi ləğv edildi, gələcək funksiyalar üçün default da bağlandı | Katalog testi 2 funksiya tapdı |

## Qeydlər / sonrakı mərhələlər

- Admin MFA qoşulma ekranı (TOTP) — **Mərhələ 3**. O vaxta qədər admin heç bir admin əməliyyatı edə bilmir (gözlənilən, təhlükəsiz vəziyyət).
- Android sessiya tokenləri hazırda supabase-kt-nin standart yaddaşındadır → **Mərhələ 3**-də şifrələnmiş yaddaşa keçiriləcək. Tətbiq parolu heç vaxt cihazda saxlamayacaq.
- Real Supabase layihəsində Auth parametrləri (`docs/BACKEND_SETUP.md` §4) əl ilə tətbiq olunmalıdır — bunlar SQL ilə idarə edilmir.
