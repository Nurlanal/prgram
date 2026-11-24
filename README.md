# Mağaza POS — offline satış və anbar proqramı

Telefon, elektronika və aksesuar mağazaları üçün **tam offline** işləyən Android satış, anbar, kassa və borc idarəetmə proqramı.

- İnternet, server və qeydiyyat tələb etmir (proqramda İnternet icazəsi belə yoxdur)
- Bütün məlumatlar telefonun daxili yaddaşında (Room/SQLite) saxlanılır, proqram bağlananda itmir
- Azərbaycan dili, AZN (₼) valyutası
- Material 3, işıqlı və qaranlıq rejim
- Kotlin · Jetpack Compose · Room · MVVM

## 1. Quraşdırma (APK)

1. `apk/MagazaPOS-v1.3.0.apk` faylını telefona köçürün (USB, Telegram, WhatsApp, Google Drive və s.).
2. Telefonda faylı açın.
3. "Naməlum mənbələrdən quraşdırma" soruşularsa: **Ayarlar → İcazə ver** (bu fayl üçün) seçin.
4. **Quraşdır** düyməsini basın.
5. Proqram "Mağaza POS" adı ilə menyuda görünəcək.

Tələblər: Android 8.0 və daha yeni.

> **Yeniləmə:** Yeni versiyanı köhnənin üzərindən quraşdırın — məlumatlar qalır. Proqramı **silməyin**: silinəndə bütün məlumatlar da silinir (əvvəlcədən backup çıxarın).

## 2. İlk addımlar

1. **Menyu → Ayarlar**: mağaza adını, ünvanı, telefonu yazın (çekdə görünür), istəsəniz PIN kod qoyun.
2. **Anbar → + Məhsul**: məhsulları əlavə edin (ad, şəkil, barkod, kateqoriya, alış/satış qiyməti, stok).
3. **Kassa → Aç**: günün əvvəlində kassadakı nağd pulu yazın.
4. **Satış**: məhsula toxunun (və ya barkodu skan edin) → **Ödəniş** → **Satışı tamamla**.

## 3. Bölmələr

| Bölmə | Nə edir |
|---|---|
| **Ana səhifə** | Satış, qazanc, kassa, mədaxil, məxaric, müştəri borcu, anbar sayı və dəyəri; qrafik; tarix seçimi (gün/həftə/ay/il/ümumi/aralıq) |
| **Satış** | Ad/barkod/SKU axtarışı, kamera ilə skan, kateqoriya filtri, səbət, say ±, qiymət dəyişmə, endirim (₼ və ya %), nağd / kart / borc / qarışıq ödəniş, qalıq hesablanması, çek (Bluetooth printer və PDF) |
| **Anbar** | Məhsul əlavə/redaktə/sil, şəkil, barkod skan, stok dəyişmə, qiymət dəyişmə, az qalan və bitmiş məhsullar, məhsulun satış və stok tarixçəsi |
| **Kassa** | Cari balans (nağd + kart), pul əlavə et/çıxar, kassa açılışı və bağlanışı (fərq avtomatik qeyd olunur), günlük kassa hesabatı, əməliyyat tarixçəsi |
| **Hesabat** | Satış, qazanc (brüt/xalis), anbar, kassa, borc hesabatları; tarix aralığı; Excel (CSV) ixracı və paylaşma |
| **Mədaxil** | Anbara mal daxili: say, alış qiyməti, təchizatçı, tarix, ödəniş vəziyyəti (ödənilib / borc / qismən), ləğv |
| **Məxaric** | Xərclər (icarə, elektrik, internet, maaş, nəqliyyat, təmir...), kassadan avtomatik çıxılır, ləğv |
| **Borclar / Müştərilər** | Borclular siyahısı, qismən/tam ödəniş, əl ilə borc əlavə etmə, borc tarixçəsi, müştərinin bütün satışları |
| **Təchizatçılar** | Təchizatçı borcları və ödənişləri |
| **Satış tarixçəsi** | Bütün satışlar, detallar, çek çapı/PDF, səbəb göstərməklə ləğv |
| **Əməliyyat tarixçəsi** | Bütün əməliyyatların silinməyən jurnalı |
| **Backup** | ZIP/JSON backup, paylaşma, Download qovluğuna saxlama, avtomatik gündəlik backup, bərpa, bütün məlumatları silmə (təsdiqlə) |

## 4. Hesablama məntiqi

Qazanc, kassa və borc **ayrı-ayrı** hesablanır:

- **Satış məbləği** = Σ(say × satış qiyməti) − endirim
- **Maya dəyəri** = Σ(say × satış anındakı alış qiyməti)
- **Brüt qazanc** = satış məbləği − maya dəyəri
- **Xalis qazanc** = brüt qazanc − məxaric
- **Kassa** yalnız real daxil olan/çıxan pulu göstərir (nağd və kart ayrıca)
- **Borc** = satış məbləği − nağd − kart (müştəri seçmək mütləqdir)

Nümunə: alış 5 ₼, satış 10 ₼, stok 20. 5 ədəd satılır, müştəri 30 ₼ ödəyir:
satış 50 ₼, maya 25 ₼, qazanc 25 ₼, stok 15, kassa +30 ₼, müştəri borcu 20 ₼.
(Bu nümunə avtomatik testdə yoxlanılır: `RepositoryTest.specExample_mixedCashAndDebt`.)

Digər qaydalar:
- Stokdan çox satmaq mümkün deyil.
- **Anbar dəyəri qəpiyinə qədər dəqiqdir.** Hər məhsul üçün anbardakı malların *ümumi* alış dəyəri saxlanılır
  (məs. 2 ədəd × 5.00 + 1 ədəd × 6.00 = **16.00 ₼**, "3 × 5.33 = 15.99" deyil). Orta alış qiyməti yalnız göstərmək üçündür.
- Satışda hər sətir anbardan öz dəqiq mayasını götürür; sonuncu ədəd qalan dəyərin hamısını aparır. Nəticədə həmişə:
  **alışlar = satılanların mayası + anbar dəyəri** (sayım düzəlişləri ayrıca göstərilir).
- Mədaxildə **"Ümumi məbləğ"** rejimi var: "3 ədəd cəmi 10.00 ₼" yazılır, anbar dəyəri dəqiq 10.00 artır.
- Satış ləğv edildikdə: məhsullar anbara qayıdır, kassa və borc əks yazılışla düzəlir, səbəb saxlanılır. Kassa tarixçəsindən heç nə silinmir.
- Bütün məbləğlər qəpiklə (tam ədəd) saxlanılır — yuvarlaqlaşdırma xətası olmur.

## 4.1. Borcun ödəniş tarixi və bildiriş

- **Borca satışda** (ödəniş: Borc və ya Qarışıq) "Borcun ödəniş tarixi" seçilir: *1 həftə*, *2 həftə*, *1 ay* və ya təqvimdən istənilən gün.
- **Əl ilə borc əlavə edəndə** də tarix təyin etmək olar.
- Həmin gün (Ayarlarda seçilmiş saatdan sonra, standart 10:00) telefona **bildiriş gəlir**: "Bu gün borc ödəniş günüdür — Müştəri: 20.00 ₼". Borc ödənilməyibsə, sonrakı günlərdə **"Gecikmiş borc … N gün gecikib"** bildirişi gündə bir dəfə gəlir.
- Bildirişə toxunduqda həmin müştərinin səhifəsi açılır.
- Müştəri möhlət istəsə: **Müştəri → Açıq borclar → Tarixi dəyiş** (və ya Satış detalında "Borcun ödəniş tarixi").
- Qismən ödənişlər ən köhnə borcdan başlayaraq bağlanır; borc tam ödənəndə bildiriş dayanır.
- Ana səhifədə və Borclar bölməsində bu gün ödənilməli və gecikmiş borclar ayrıca göstərilir.
- İnternet lazım deyil — yoxlama telefonda lokal işləyir və telefon yenidən başladılanda da davam edir.
- **Ayarlar → Borc xatırlatmaları**: aç/söndür, bildiriş saatı, bildiriş icazəsi.
- Android 13+ telefonlarda ilk dəfə tarix seçəndə bildiriş icazəsi soruşulur — **İcazə ver** seçin.

## 4.2. Anbar qalığının düzəldilməsi (sayım)

- **Menyu → Anbar sayımı** (və ya Anbar ekranında ✓ düyməsi).
- Hər məhsulun **faktiki sayını** yazın, ya da ± düymələri ilə dəyişin. Boş saxlanılanlar dəyişmir.
- **Skan edərək saymaq:** yuxarıdakı skan düyməsi — hər skan həmin məhsulun sayını +1 artırır.
- Altda fərqlər görünür: neçə məhsul, say fərqi və **anbar dəyərinin dəyişməsi (₼)**.
- **Təsdiqlə** — qalıqlar və dəyər bir əməliyyatla düzəldilir; hər düzəliş məhsulun "Stok hərəkəti" tarixçəsinə və Əməliyyat tarixçəsinə yazılır.
- Azalanda (itki, zədə) dəyər mütənasib çıxılır; artanda (tapılan mal) orta alış qiyməti ilə əlavə olunur.
- Tək məhsul üçün: məhsul səhifəsi → **Stoku dəyiş**.

## 5. Backup və bərpa (çox vacib)

Məlumatlar yalnız telefonda saxlanıldığı üçün **müntəzəm backup çıxarın**:

- **Menyu → Backup və bərpa → Backup faylı yarat (ZIP)** — faylı istədiyiniz yerə saxlayın (içində bütün məlumatlar və məhsul şəkilləri var).
- **Backup-ı paylaş** — Telegram/WhatsApp/e-poçtla özünüzə göndərin.
- **Avtomatik gündəlik backup** (standart olaraq aktivdir) — gündə bir dəfə `Download/MagazaPOS` qovluğuna və proqramın daxili yaddaşına (son 7 nüsxə) yazılır. `Download` qovluğundakı fayllar proqram silinsə belə qalır.
- **Bərpa:** yeni telefonda proqramı quraşdırın → Backup və bərpa → **Backup faylından bərpa et** → faylı seçin. Bərpadan əvvəl hazırkı vəziyyət avtomatik saxlanılır.

## 6. Avadanlıq

- **Barkod skaneri:** telefon kamerası (proqramdakı skan düyməsi) və ya USB/Bluetooth skaner (klaviatura kimi işləyir — axtarış sahəsinə yazıb Enter göndərir, məhsul avtomatik səbətə düşür).
- **Çek printeri:** ESC/POS dəstəkləyən 58 mm və 80 mm Bluetooth printerlər. Printeri əvvəlcə telefonun Bluetooth ayarlarında cütləşdirin, sonra **Ayarlar → Printer seç → Test çapı**. Printerlərin çoxu Azərbaycan hərflərini dəstəkləmədiyi üçün çekdə latın ekvivalentləri çap olunur (ə→e, ş→s...). PDF çekdə hərflər tam görünür.

## 6.1. Barkodsuz mallar

Zavod barkodu olmayan mallar (qablar, kabellər, aksesuarlar) üçün iki yol var:

**A) Satış zamanı birbaşa təyin etmək (ən sürətli):**
1. Satış ekranında malın üzərindəki istənilən barkodu (və ya yapışdırdığınız etiketi) skan edin.
2. Proqram barkodu tanımırsa, **"Barkod tapılmadı"** pəncərəsi açılır — barkodsuz məhsulların siyahısı göstərilir.
3. Məhsulu seçib **Təyin et** basın: barkod həmin məhsula yazılır və məhsul **dərhal səbətə düşür**.
4. Növbəti dəfə həmin barkod skan ediləndə məhsul avtomatik tanınır. (Siyahıda yoxdursa — **Yeni məhsul yarat**, barkod formada hazır olur.)

**B) Mağaza daxili barkod yaratmaq və etiket çap etmək:**
1. **Anbar → "Barkodsuz"** filtri → **Hamısına barkod yarat** (və ya məhsulun səhifəsində **Barkod yarat**, ya da məhsul formasında ▦ düyməsi).
2. Yaradılan barkodlar `20…` ilə başlayan EAN-13 kodlarıdır (mağaza daxili istifadə üçün ayrılmış diapazon — zavod barkodları ilə toqquşmur).
3. Etiket çapı: **Bluetooth çek printerinə** (ad + qiymət + barkod) və ya **PDF (A4, 3×8 yapışqanlı etiket vərəqi)**. Say: hər məhsuldan N ədəd və ya stok sayı qədər.
4. Etiketləri mallara yapışdırın — satışda kamera və ya skanerlə vurulur.

## 7. Layihəni Android Studio-da açmaq və APK yığmaq

1. Android Studio (Ladybug və ya daha yeni) → **Open** → bu qovluq.
2. Gradle sinxronizasiyası bitəndən sonra **Run** (telefon USB ilə qoşulu) və ya **Build → Build APK(s)**.

Komanda sətrindən:

```bash
./gradlew testDebugUnitTest   # testlər (30 test: biznes məntiqi + bütün ekranlar)
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

İmza: `keystore.properties` faylı (repoya daxil edilmir) varsa, release APK həmin açarla imzalanır; yoxdursa debug açarı ilə. Yeniləmələrin köhnə versiyanın üzərinə quraşdırılması üçün **eyni açardan** istifadə edin. `keystore.properties` nümunəsi:

```
storeFile=keystore/magaza-pos-release.jks
storePassword=...
keyAlias=magazapos
keyPassword=...
```

## 8. Layihə strukturu

```
app/src/main/java/az/magaza/pos/
├── data/            Room entity-ləri, DAO, PosRepository (bütün biznes əməliyyatları),
│                    BackupManager, hesabatlar, ayarlar
├── ui/              Compose ekranları (dashboard, sales, inventory, cash, reports,
│                    purchase, expense, customers, suppliers, history, settings, lock)
└── util/            Pul/tarix formatı, çek (PDF + ESC/POS), Bluetooth printer, CSV
app/src/test/        Robolectric testləri (RepositoryTest, UiSmokeTest)
```
