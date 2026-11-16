# Supermarket Android tətbiqi (Müştəri · Admin · Kuryer)

Bir market, bir filial üçün tam mobil alış-veriş platforması. Bütün sistem **bir Android APK** daxilində işləyir; istifadəçi daxil olduqda server tərəfindən təyin edilmiş roluna uyğun interfeys açılır.

> Layihə mərhələlərlə hazırlanır. Cari vəziyyət üçün **[PROJECT_PROGRESS.md](PROJECT_PROGRESS.md)**, arxitektura üçün **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** faylına baxın.

## Texnologiyalar

| Sahə | Seçim |
|---|---|
| Dil / UI | Kotlin 2.2, Jetpack Compose, Material Design 3 |
| Arxitektura | MVVM (ViewModel + StateFlow), Hilt DI, Coroutines/Flow |
| Naviqasiya | Navigation Compose (type-safe, kotlinx.serialization) |
| Backend (Mərhələ 2+) | Supabase: Auth, PostgreSQL, Storage, RLS, Realtime, Edge Functions |
| Push (Mərhələ 12) | Firebase Cloud Messaging |
| Minimum Android | Android 10 (API 29), target API 36 |

## Tələblər

- Android Studio (Narwhal və ya daha yeni) və ya JDK 17+ (21 tövsiyə olunur)
- Android SDK Platform 36, Build-Tools 36.0.0

## Build

```bash
# local.properties faylında SDK yolunu göstərin (Android Studio bunu avtomatik edir):
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew testDebugUnitTest   # unit + Robolectric UI testləri
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # R8 ilə kiçildilmiş release (imzalama Mərhələ 15-də)
./gradlew lintDebug
```

UI testləri ekran görüntülərini `app/build/screenshots/` qovluğuna yazır.

## Təhlükəsizlik qaydaları

- `local.properties`, `secrets.properties`, `keystore.properties`, `*.jks`, `google-services.json` **heç vaxt commit edilmir** (`.gitignore`).
- APK-ya yalnız Supabase **anon (public) key** daxil ediləcək; `service_role` açarı yalnız serverdə (Edge Functions) qalır.
- Rol, qiymət, stok və bonus hesablamaları yalnız serverdə aparılır.

## Paket strukturu

```
az.market.app
├── MarketApplication / MainActivity   # Hilt giriş nöqtəsi, splash, tema
├── core
│   ├── common        # Money (qəpik), UiState
│   ├── designsystem  # tema (açıq/qaranlıq, dəyişən brend rəngi), komponentlər
│   ├── di            # Dispatcher modulları
│   └── model         # UserRole, SessionState, ThemeMode
├── data              # repository-lər (session; Mərhələ 2+ Supabase)
├── navigation        # marşrutlar, rol əsaslı başlanğıc, root NavHost
└── feature
    ├── auth          # xoş gəlmisiniz, giriş/qeydiyyat
    ├── customer      # 5 bölməli aşağı menyu
    ├── admin         # admin paneli
    └── courier       # kuryer paneli
```
