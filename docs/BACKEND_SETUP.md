# Backend quraşdırma təlimatı (Supabase)

Bu təlimat real serveri sıfırdan qurmaq üçündür. Təxminən 15 dəqiqə çəkir.

## 1. Supabase layihəsi yaradın

1. <https://supabase.com> → **New project**.
2. **Region:** `Central EU (Frankfurt)` — Azərbaycana ən yaxın.
3. Güclü **Database password** seçin və təhlükəsiz yerdə saxlayın (tətbiqə lazım deyil).

## 2. Verilənlər bazasını qurun

**Variant A — SQL Editor (ən sadə):**
Dashboard → **SQL Editor** → **New query** → `supabase/schema_full.sql` faylının tam məzmununu yapışdırın → **Run**.

**Variant B — Supabase CLI:**
```bash
supabase login
supabase link --project-ref <PROJECT_REF>
supabase db push            # supabase/migrations/*.sql ardıcıllıqla tətbiq olunur
```

Yoxlama (SQL Editor):
```sql
select count(*) from public.categories;   -- 12
select * from public.app_settings;        -- 1 sətir
```

## 3. API açarları → Android build

Dashboard → **Project Settings → API**:

| Dəyər | Harada istifadə olunur |
|---|---|
| **Project URL** (`https://xxxx.supabase.co`) | APK (`SUPABASE_URL`) |
| **anon / publishable key** | APK (`SUPABASE_ANON_KEY`) — public açardır, RLS ilə qorunur |
| **service_role / secret key** | ❌ **APK-ya HEÇ VAXT.** Yalnız server funksiyaları (Edge Functions) |

Layihənin kökündəki `local.properties` faylına (git-ə düşmür):
```properties
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOi...
```
CI üçün eyni adlı mühit dəyişənləri də işləyir.

Gizli açara qarşı iki qoruma var:
1. Səhvən `service_role` / `sb_secret_` açarı yazılsa, **build başlamadan dayanır**.
2. Hər `assembleDebug` / `assembleRelease`-dən sonra `verifyNoSecretsIn…Apk` tapşırığı hazır APK-nı skan edir (service_role JWT, `sb_secret_`, private key, Firebase service account). Tapılsa, build uğursuz olur. Eyni yoxlama: `python3 scripts/check_apk_secrets.py <apk>`.

## 4. Authentication təhlükəsizlik parametrləri

**Parollar:** tətbiq heç bir parolu öz cədvəllərində saxlamır. Parolları yalnız Supabase Auth idarə edir və `auth.users.encrypted_password` sütununda yalnız **bcrypt hash** saxlayır. `auth` sxemi API vasitəsilə oxuna bilməz.

Dashboard → **Authentication** bölməsində tövsiyə olunan parametrlər:

| Parametr | Dəyər |
|---|---|
| Providers → Phone | aktiv (müştəri və kuryer girişi, Mərhələ 3) |
| Providers → Email | aktiv (admin girişi) |
| Anonymous sign-ins | **söndürülmüş** |
| Password → Minimum length | **8** (və ya daha çox) |
| Password → Required characters | hərf + rəqəm |
| Password → Leaked password protection | aktiv (Pro plan varsa) |
| Secure password change (reauthentication) | aktiv |
| Multi-Factor → TOTP | **aktiv** (admin üçün mütləqdir, aşağıya baxın) |
| Rate limits | standart dəyərlərdən aşağı salmayın |

**Admin üçün 2 faktorlu giriş (MFA) mütləqdir.** Server hər admin icazəsini yoxlayanda sessiyanın `aal2` olmasını (parol + autentifikator kodu) tələb edir. Admin parolu oğurlansa belə, kodsuz heç bir admin əməliyyatı mümkün deyil. Admin ilk girişdə autentifikator tətbiqini (Google Authenticator, Microsoft Authenticator və s.) qoşacaq; bu ekran Mərhələ 3-də hazırlanır.

## 5. İlk admin hesabı (server tərəfli, bir dəfəlik)

Admin qeydiyyatı tətbiqdə açıq deyil. İlk admin belə yaradılır:

1. Dashboard → **Authentication → Users → Add user → Create new user**: admin email + güclü şifrə, *Auto Confirm User* ✓.
2. **SQL Editor**:
   ```sql
   select public.bootstrap_first_admin('admin@sizin-market.az');
   ```
   Funksiya yalnız hələ heç bir admin olmadıqda işləyir və API vasitəsilə çağırıla bilməz (anon/authenticated/service_role üçün bağlıdır).

Sonrakı adminlər və kuryerlər admin paneldən yaradılacaq (Mərhələ 3 və 10).

## 6. Storage

Migration avtomatik 3 bucket yaradır: `product-images`, `banners`, `branding` (oxumaq açıq, yükləmək yalnız admin).

## 7. Realtime

`orders`, `order_status_history`, `notifications`, `inventory`, `app_settings` cədvəlləri `supabase_realtime` publikasiyasına əlavə olunub. Hadisələr RLS-ə tabedir — istifadəçi yalnız öz sətirlərini alır.

## 8. Lokal test (istəyə görə)

PostgreSQL 15+ olan istənilən maşında:
```bash
PGHOST=localhost PGPORT=5432 PGUSER=postgres supabase/tests/run_local_tests.sh
```
Migration-ları boş bazaya tətbiq edir, 135 təhlükəsizlik/bütövlük yoxlamasını işlədir və Android kontrakt testləri üçün fixture-ları yeniləyir.
