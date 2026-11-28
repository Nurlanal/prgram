# Fanora: kreator abunəlik platforması (PHP)

Sıfırdan yazılmış, OnlyFans tipli kreator platforması. Fanatlar kreatorlara aylıq abunə olur, pullu (PPV) postları ayrıca alır, bəxşiş göndərir və mesajlaşır. Platforma hər ödənişdən komissiya tutur.

**Texnologiya:** təmiz PHP 8.1+ (framework və Composer yoxdur), PDO (SQLite və ya MySQL), server-tərəfli şablonlar, bir az vanilla JS.

## Funksiyalar

| Fanat | Kreator | Admin |
|---|---|---|
| Qeydiyyat (18+ yaş yoxlaması) | Kreator müraciəti (rəsmi ad + təsdiqlər) | Kreator müraciətlərini təsdiq/rədd |
| Kreatorları kəşf et / axtar | Foto/video postlar (10 fayla qədər) | Şikayətlər: postu sil / blokla / rədd et |
| Abunə ol (30 gün, avto-yeniləmə) | Post görünüşü: pulsuz / yalnız abunəçilər / PPV | Çıxarış sorğuları: ödənildi / rədd et (geri qaytarır) |
| PPV postu aç | Aylıq abunə qiyməti | İstifadəçi axtarışı, blok/blokun götürülməsi |
| Bəxşiş (mesajla birlikdə) | Gəlir paneli: 30 günlük qrafik, top fanatlar, top postlar | Statistika: dövriyyə, komissiya gəliri |
| Bəyənmə, şərh, şikayət | Abunəçi siyahısı, abunəçilərlə mesaj | |
| Mesajlar (abunəlik olduqda) | Pul çıxarma sorğusu | |
| Cüzdan və əməliyyat tarixçəsi | | |

## Quraşdırma

```bash
cp config.sample.php config.php      # parametrləri redaktə edin (admin şifrəsi!)
php bin/install.php                  # cədvəllər + admin hesabı
php bin/seed.php                     # (istəyə görə) demo kreatorlar və fan
php -S localhost:8000 -t public public/index.php
```

http://localhost:8000 ünvanını açın.

Demo hesablar (`seed.php`-dən sonra), şifrə hamısında `password123`:
- `demo_fan` (balansı $100)
- `aylin_art`, `fit_with_murad`, `chef_lala` (kreatorlar)
- Admin: `config.php`-dəki `admin` (default `admin` / `change-me-please`)

Avto-yeniləmə üçün cron əlavə edin:
```
0 * * * * php /path/to/fanora/bin/cron.php
```

### Produksiya (Apache / Nginx)
- **Document root** `public/` qovluğu olmalıdır. `app/`, `views/`, `storage/` və `config.php` vebdən əlçatan olmamalıdır.
- Apache: `public/.htaccess` hazırdır (mod_rewrite).
- Nginx:
  ```nginx
  root /var/www/fanora/public;
  location / { try_files $uri /index.php$is_args$args; }
  location ~ \.php$ { include fastcgi_params; fastcgi_param SCRIPT_FILENAME $document_root/index.php; fastcgi_pass unix:/run/php/php8.3-fpm.sock; }
  client_max_body_size 200M;
  ```
- `php.ini`: `upload_max_filesize = 200M`, `post_max_size = 210M`.
- `storage/` qovluğuna veb serverin yazma icazəsi verin.
- HTTPS istifadə edib `config.php`-də `cookie_secure => true` edin.
- MySQL üçün: `db.driver => 'mysql'`, bazanı yaradın (`CREATE DATABASE fanora CHARACTER SET utf8mb4`), sonra `php bin/install.php`.

## Struktur

```
public/index.php        front controller + marşrutlar
public/assets/          CSS, JS, ikonlar
app/bootstrap.php       DB, sessiya, CSRF, köməkçilər
app/services.php        biznes qaydaları: giriş hüququ, ödənişlər, abunəlik
app/upload.php          təhlükəsiz fayl yükləmə və video stream (Range)
app/handlers/*.php      səhifə və əməliyyat handler-ləri
views/                  PHP şablonları
database/               SQLite və MySQL sxemləri
bin/                    install, seed, cron
storage/                baza və yüklənmiş fayllar (vebdən kənarda)
```

## Təhlükəsizlik

- Şifrələr `password_hash` ilə saxlanılır; giriş zamanı sessiya ID-si yenilənir.
- Bütün POST formalarında CSRF token var; cookie `HttpOnly` + `SameSite=Lax`.
- Bütün SQL sorğuları prepared statement ilə yazılıb; bütün çıxışlar `htmlspecialchars` ilə escape olunur.
- CSP header: yalnız öz domendən skript (inline skript yoxdur).
- Fayllar `public/` xaricində təsadüfi adla saxlanılır. MIME tipi faylın məzmunundan (`finfo`) təyin olunur, şəkillər `getimagesize` ilə yoxlanılır.
- Media yalnız `/media/{id}` ilə verilir və hər sorğuda giriş hüququ yoxlanılır (abunəlik / PPV alışı / sahib).
- Balansdan çıxma atomikdir (`UPDATE ... WHERE balance >= ?`), bütün ödənişlər tranzaksiya daxilindədir.
- Login, qeydiyyat, şərh, mesaj və bəxşiş üçün IP əsaslı rate limit var.

## Produksiyadan əvvəl mütləq edilməli olanlar

1. **Real ödəniş provayderi.** Hazırda `demo_payments = true` rejimində cüzdan saxta artırılır. Kreator/adult məzmuna icazə verən provayderə ehtiyac var (Stripe və PayPal adətən buna icazə vermir; bu sahədə CCBill, Segpay, Verotel kimi provayderlər işləyir). `do_topup()` funksiyasını provayderin checkout-u ilə əvəz edin və balansı yalnız provayderin imzalanmış webhook-u ilə artırın.
2. **KYC / yaş yoxlaması.** Kreator müraciətinə (`do_become_creator`) şəxsiyyət yoxlama xidməti (Veriff, Sumsub, Yoti və s.) qoşun. Məzmunda görünən hər kəsin 18+ olduğunu və razılıq verdiyini sübut edən sənədlər saxlanmalıdır.
3. **Moderasiya.** CSAM üçün hash-matching (məsələn, PhotoDNA) və məzmunun avtomatik yoxlanması tövsiyə olunur. Şikayət sistemi hazırdır.
4. **Hüquqi sənədlər.** `views/terms.php` nümunədir. Məxfilik siyasəti, DMCA prosedurları və yerli qanunlara uyğunluq üçün hüquqşünasla məsləhətləşin.
5. **Media saxlanması.** Böyük həcm üçün S3/R2 + imzalı URL-lər və CDN istifadə edin; videoları HLS-ə çevirin və watermark əlavə edin.
6. **Email.** Email təsdiqi və şifrə bərpası əlavə edin (SMTP lazımdır).
