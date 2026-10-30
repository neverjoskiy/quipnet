# QuipNet (Android) — полное описание проекта

> **Язык:** русский  
> **Статус:** активная разработка, сборка через GitHub Actions  
> **Репозиторий:** https://github.com/neverjoskiy/quipnet  
> **Ветка:** `main` (один коммит `Initial commit`, старая история полностью удалена)

---

## 1. Что такое проект

QuipNet — клиент VPN для Android, предназначенный для обхода DPI-фильтрации и замедления интернета. Приложение бесплатное, без подписок, рекламы и телеметрии.

**Минимальная версия Android:** 7.0 (API 24)  
**Целевая версия:** 34 (compileSdk 35)  
**Пакет (applicationId):** `com.quipnet.android`  
**Пространство имён (namespace):** `com.example.quipnet`  
**Текущая версия:** `1.33.1` (versionCode `164`)

**Основные возможности:**
- Встроенные узлы: Cloudflare WARP поверх AmneziaWG, MASQUE поверх QUIC, серверы Opera VPN (Европа, Северная Америка), бесплатные узлы Proton VPN (только по прямому выбору).
- Автоматический переход между транспортом при отказе одного из них.
- Импорт собственных конфигураций: профили AmneziaWG, ссылки `vless://` с транспортами XHTTP, WebSocket, gRPC, HTTP Upgrade.
- Подписки (списки ссылок, в т.ч. base64 с метаданными).
- Раздача VPN на другие устройства без root (телефон как шлюз: HTTP, HTTPS CONNECT, SOCKS5).
- Раздельное туннелирование (правила для отдельных приложений).
- Настройка DNS (DoH, DoT) с правилами по приложениям.
- Поддержка лицензии WARP+.
- Диагностическая сборка с отчётом на экране (для случаев, когда ADB недоступен).

**Что проверяет приложение:** не факт подключения, а живую пробу — счётчики трафика не используются для проверки работоспособности, потому что они накапливаются за всё время существования туннеля и после смены сети остаются прежними.

---

## 2. История переименования (Nova → QuipNet)

Проект изначально назывался **Nova**. Проведено полное переименование на **QuipNet**:

| Область | Было | Стало | Статус |
|---|---|---|---|
| `applicationId` (build.gradle.kts) | — | `com.quipnet.android` | ✅ |
| `namespace` (build.gradle.kts) | `com.example.nova` | `com.example.quipnet` | ✅ |
| `rootProject.name` (settings.gradle.kts) | `NovaAndroid` | `QuipNetAndroid` | ✅ |
| Java/Kotlin пакеты | `com.example.nova.*` | `com.example.quipnet.*` | ✅ |
| Имена классов | `QuipNet*` | `QuipNet*` (оставлены как есть) | ✅ |
| Ресурсы (drawable, xml, theme) | `bg_nova`, `ic_nova`, `Theme_Nova` и т.д. | `bg_quipnet`, `ic_quipnet`, `Theme_QuipNet` | ✅ |
| Go модуль | `nova-core` | `quipnet-core` | ✅ |
| Go модуль xray | `nova-xray` | `quipnet-xray` | ✅ |
| JNI символы | `Java_com_example_nova_*` | `Java_com_example_quipnet_*` | ✅ |
| F-Droid метаданные | `com.nova.android` | `com.quipnet.android` | ✅ |
| README, docs, tools | ссылки на Nova | ссылки на QuipNet | ✅ |
| AAR-файл (классы внутри) | `nova.Nova` | **осталось `nova.Nova`** | ❌ см. раздел 7 |
| Kotlin импорты | `quipnet.Quipnet` | **`nova.Nova`** | ⚠️ приведено к AAR |
| Домен релея | `nova-app.eu` | без изменений (намеренно) | ✅ по решению |
| Нативная библиотека | `novaxray` | без изменений | ✅ по решению |
| Ключ протокола | `nova1` | без изменений | ✅ по решению |
| Wire-format ключ | `NOVAFAKEHOST` | без изменений | ✅ по решению |
| Ссылки в комментариях | `confeden/Nova-Android` | без изменений | ✅ по решению |

**Кто принимал решение оставить внешние идентификаторы без изменений:** пользователь. Оставлены: `nova-app.eu` (домен релея), `novaxray` (нативная библиотека), `nova1` (ключ протокола), `NOVAFAKEHOST` (wire-format ключ), `confeden/Nova-Android` (ссылки в комментариях).

---

## 3. Технический стек

| Компонент | Версия | Назначение |
|---|---|---|
| Kotlin | 2.2.10 | Язык приложения |
| Android Gradle Plugin (AGP) | 9.x | Сборка Android-проекта |
| Gradle | 9.3.1 | Система сборки (wrapper) |
| compileSdk | 35 | Целевой SDK компиляции |
| targetSdk | 34 | Целевой SDK приложения |
| minSdk | 24 | Android 7.0 |
| NDK | 27.2.12479018 | Нативная сборка (закреплена) |
| CMake | 3.22.1 | Сборка JNI-слоя к tun2proxy |
| Go | 1.26.3 | Ядро `quipnet-core` |
| gomobile | v0.0.0-20260120165949-40bd9ace6ce4 | Генерация AAR из Go |
| Java | 17 (Temurin) | Сборка в GitHub Actions |

**Архитектурные решения:**
- `VpnService` заворачивает в TUN трафик только по UID приложения. Трафик клиентов точки доступа не имеет UID и уходит мимо tun, поэтому прозрачная раздача без root невозможна. Вместо этого телефон работает шлюзом (HTTP, HTTPS CONNECT, SOCKS5), исходящие сокеты шлюза — это сокеты самого приложения, они идут в туннель.
- JNI-слой к tun2proxy собирается из исходников (`src/main/cpp/CMakeLists.txt`), а не берется готовым бинарем (правила F-Droid запрещают готовые бинарники).
- QR-библиотека (zxing) подключается только в диагностической сборке через рефлексию; в обычных сборках не включается.
- БouncyCastle Picnic ресурсы исключены из APK (мёртвый вес ~1.2 МБ, не сжимается).

---

## 4. Структура репозитория

```
QuipNet-Android/
├── .github/
│   └── workflows/
│       ├── build.yml               # Сборка APK через GitHub Actions
│       └── publish_apk_update.yml  # Опубликование JSON с версией APK
├── app/
│   ├── build.gradle.kts            # Конфигурация модуля приложения
│   ├── libs/
│   │   └── quipnet-core-api24-stripped.aar   # Go-ядро (старые классы nova.Nova!)
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/quipnet/     # Весь Kotlin-код
│       │   ├── cpp/                          # JNI-слой к tun2proxy (CMake)
│       │   ├── jniLibs/<abi>/                # Готовые .so (libgojni.so и др.)
│       │   └── res/                          # Ресурсы (переименованы на quipnet)
│       └── test/
├── quipnet-core/                     # Ядро на Go (AmneziaWG, MASQUE, DNS)
│   ├── go.mod
│   ├── mobile.go                     # Точка входа для gomobile bind (package quipnet)
│   ├── engine/                       # Движок (WireGuard, MASQUE, DNS, шлюз)
│   └── ...
├── quipnet-xray/                     # Обёртка над Xray-core (VLESS + REALITY)
│   ├── go.mod
│   ├── jni/quipnetxray_jni.c         # JNI символы: Java_com_example_quipnet_XrayBridge_*
│   ├── main.go
│   └── build.sh                      # Сборка libnovaxray.so
├── tools/
│   ├── build_nova_core_aar.sh        # Сборка AAR из quipnet-core (gomobile bind)
│   ├── build_tun2proxy.sh
│   ├── build_opera_proxy.sh
│   ├── build_stormdns_client.sh
│   ├── deps/                         # Пины и патчи Go-зависимостей
│   ├── connect-ip-go/                # Форк connect-ip-go
│   ├── xray-core/                    # Вендоренный форк Xray-core
│   └── ...
├── docs/
│   ├── BUILD.md                      # Инструкция по сборке
│   ├── gateway-sharing.md            # Раздача VPN без root
│   ├── masque-connect-ip.md          # MASQUE подключение
│   ├── vless-reality-plan.md         # VLESS + REALITY
│   ├── warp-api-sni-block.md         # Блокировка по SNI
│   └── telegram-wss-signature.md
├── fdroid/
│   └── com.quipnet.android.yml       # Метаданные для F-Droid
├── fastlane/
│   └── metadata/                     # Тексты для каталогов (F-Droid забирает оттуда)
├── gradle/
│   └── wrapper/                      # Gradle wrapper
├── README.md
├── THIRD-PARTY.md
├── LICENSE (GPL-3.0)
├── PROJECT.md                        # этот файл
└── .gitignore
```

---

## 5. Модули и их назначение

### `app/` — основное приложение
- Интерфейс, служба VPN (`QuipNetVpnService`), шлюз раздачи, разбор конфигураций.
- Зависимости: OkHttp, BouncyCastle, Tor Android, WorkManager, androidx, Material, ConstraintLayout.
- Локальный AAR: `libs/quipnet-core-api24-stripped.aar` (подключается как `implementation(files(...))`).
- Нативные библиотеки в `app/src/main/jniLibs/<abi>/` имеют приоритет над `jni/` внутри AAR.

### `quipnet-core/` — ядро на Go
Пакет `quipnet` (точка входа `mobile.go`). Экспортирует функции для Android через `gomobile bind`:
- `StartVPN`, `StopVPN`, `ForceHandshake`, `SwitchPeerEndpoint`, `GetVPNStats`
- `StartMasqueVPN`, `StartMasqueVPNWithSNI`, `StopMasque`
- `EnsureMasqueConfig`, `LastMasqueEnrollResult`, `ForgetMasqueEnrollResult`
- `StartGatewayServer`, `UpdateGatewayPeers`, `StopGatewayServer` (WireGuard шлюз)
- `SetSocketProtector`, `SetMasqueSocketProtector` (исключение сокетов из VPN)
- `ScanWarpEndpoints`, `ScanMasqueEndpoints`
- `StartDNSTunnel`, `StopDNSTunnel`, `StartTorPtProxy`, `StopTorPtProxy`
- `SetDnsInterceptPolicy`, `SetDomainBypassPolicy`, и др.

Зависимости подключены через `replace` по локальным путям (три из пяти пропатчены):
- `amneziawg-go` v3.1.20260828 (патч только `tun/netstack/`)
- `warp-plus` (патчи `wireguard/tun/netstack/`, `wiresocks/`)
- `gvisor` (снят `pkg/sync/runtime_constants_go125.go`)
- `usque` (без патча)
- `connect-ip-go` (форк в репозитории)
- `anet` (патч для IPv6 zoneCache)
- `snowflake/v2` (патч для ловушек сети)

Скрипт сборки: `tools/build_nova_core_aar.sh` — вызывает `gomobile bind`, снимает символы с `libgojni.so`, раскладывает в `app/libs/` и `app/src/main/jniLibs/`.

### `quipnet-xray/` — VLESS + REALITY
Обёртка над Xray-core (форк в `tools/xray-core`). JNI-слой в `jni/quipnetxray_jni.c` со символами `Java_com_example_quipnet_XrayBridge_*`. Собирается отдельно через `quipnet-xray/build.sh` в `app/src/main/jniLibs/<abi>/` (`libnovaxray.so`, `libnovaxrayjni.so`).

---

## 6. Система сборки (GitHub Actions)

### Workflow `.github/workflows/build.yml`

Сборка запускается по `push` в `main` и вручную (`workflow_dispatch`).

**Шаги:**
1. `Checkout` —actions/checkout@v4
2. `Set up JDK 17` — actions/setup-java@v4 (temurin)
3. `Ensure Android SDK` — ручная установка SDK в `/usr/local/lib/android/sdk`:
   - Качает commandline-tools через curl
   - Принимает лицензии (`yes | sdkmanager --licenses`)
   - Устанавливает: `platforms;android-35`, `build-tools;35.0.1`, `cmake;3.22.1`, `ndk;27.2.12479018`
4. `Grant execute permission for gradlew` — `chmod +x gradlew`
5. `Build debug APK` — `./gradlew :app:assembleGithubDebug --stacktrace`
   - Переменные: `ANDROID_HOME`, `ANDROID_SDK_ROOT` = `/usr/local/lib/android/sdk`
6. `Upload APK` — actions/upload-artifact@v4, путь `app/build/outputs/apk/github/debug/*.apk`

**Команда сборки:** `./gradlew :app:assembleGithubDebug --stacktrace`

### Почему ручная установка SDK, а не `setup-android` action

Ранее использовался `setup-android` action, но возникали проблемы:
- Несколько переменных окружения содержали разные пути к SDK
- Решено оставить консистентный путь `/usr/local/lib/android/sdk` с `ANDROID_HOME` и `ANDROID_SDK_ROOT`

### Флаги сборки (в `app/build.gradle.kts`)

| Флаг | Команда | Эффект |
|---|---|---|
| `quipnetDiagnostics` | `-PquipnetDiagnostics` | Включает `DiagnosticsActivity`, отчёт на экране, QR-код |
| `quipnetFastBuild` | `-PquipnetFastBuild` | Выключает R8 и lint, только для проверок |
| `quipnetAllowUnsignedRelease` | `-PquipnetAllowUnsignedRelease=true` | Разрешает релиз без секрета WSS |

### productFlavors

| Flavor | Размерность | `UPDATER_ENABLED` | По умолчанию |
|---|---|---|---|
| `github` | `distribution` | `true` | ✅ |
| `fdroid` | `distribution` | `false` | — |

### Имя выходного APK

Все варианты собираются в `QuipNet_<версия>.apk` (не зависит от flavor).

---

## 7. ⚠️ КРИТИЧЕСКАЯ ПРОБЛЕМА: AAR и имя класса Go-ядра

### Суть проблемы

AAR-файл `app/libs/quipnet-core-api24-stripped.aar` содержит внутри `classes.jar` со структурой:

```
nova/Nova.class
nova/SocketProtector.class
go/...
```

То есть пакет **`nova`**, класс **`Nova`** — это **старые, не пересобранные после переименования** классы.

В то же время Go-источник `quipnet-core/mobile.go` deklarерирует `package quipnet`, и Kotlin-код импортирует `quipnet.Quipnet`. Соответственно, компилятор не находит пакет `quipnet` в AAR и выдает ошибку `Unresolved reference 'quipnet'` на уровне импорта.

### Почему AAR не пересобран

- Пересборка требует Go 1.26+, gomobile, Android NDK 27.
- Пользователь禁止л скачивание anything на свой ПК.
- Локально Go/gomobile инструментарий недоступен.

### Временное решение (применено)

Весь Kotlin-код приведен к тому, что реально есть в AAR:

```kotlin
import nova.Nova   // вместо import quipnet.Quipnet
Nova.setSocketProtector(...)   // вместо Quipnet.setSocketProtector(...)
Nova.startVPN(...)             // вместо Quipnet.startVPN(...)
```

Исправлено в 10 файлах:
- `GatewayWireGuard.kt`
- `MainActivity.kt`
- `OperaProxyManager.kt`
- `QuipNetVpnService.kt` (включая `object : nova.SocketProtector`)
- `WarpClient.kt`
- `WarpProfileGenerator.kt`
- `SessionHoldMetric.kt` (только комментарий)
- `TorTransport.kt` (добавлен импорт)
- `WarpIdentityBackfill.kt` (добавлен импорт)
- `SettingsActivity.kt` (добавлен импорт)

### Что нужно сделать для полного переименования

На машине с Go, gomobile и Android NDK:

```bash
cd quipnet-core
gomobile bind -trimpath -ldflags="-buildid=" -androidapi 24 -target=android -o quipnet-core.aar .
```

Затем обновить `app/libs/quipnet-core-api24-stripped.aar` и `app/src/main/jniLibs/<abi>/libgojni.so`, и вернуть Kotlin-импорты к `import quipnet.Quipnet`.

---

## 8. Нативные библиотеки (.so)

| Библиотека | Архитектуры | Источник | Сборка |
|---|---|---|---|
| `libgojni.so` | arm64-v8a, armeabi-v7a | `quipnet-core/` | `tools/build_nova_core_aar.sh` |
| `libnovaxray.so` | arm64-v8a, armeabi-v7a | `quipnet-xray/` | `quipnet-xray/build.sh` |
| `libnovaxrayjni.so` | arm64-v8a, armeabi-v7a | `quipnet-xray/` | `quipnet-xray/build.sh` |
| `libnative-lib.so` | arm64-v8a, armeabi-v7a | `app/src/main/cpp/native-lib.cpp` | CMake в составе проекта |
| `libtun2proxy.so` | arm64-v8a, armeabi-v7a | tun2proxy v0.7.21 | `tools/build_tun2proxy.sh` |
| `liboperaproxy.so` | arm64-v8a, armeabi-v7a | opera-proxy v1.15.0-fork | `tools/build_opera_proxy.sh` |

**Важно:** `libgojni.so` в `jniLibs/` имеет приоритет над `jni/` внутри AAR. Если обновить только AAR, на ARM-устройствах будет загружаться старая библиотека — нужно обновлять оба места.

ABI-фильтр: только `arm64-v8a` и `armeabi-v7a` (нет x86/x86_64, так как для них нет `libtun2proxy.so`).

---

## 9. Секреты и конфигурация

### Файл `local.properties` (не коммитится)

```
sdk.dir=/путь/к/Android/Sdk
quipnetTgCfSecret=...
quipnetOperaRelayPassword=...
quipnetAllowUnsignedRelease=true
```

### Переменные окружения (для GitHub Actions)

| Переменная | Назначение |
|---|---|
| `QUIPNET_TG_CF_SECRET` | Секрет подписи WSS рукопожатий к поддоменам `nova-app.eu` |
| `QUIPNET_OPERA_RELAY_PASSWORD` | Пароль релея API SurfEasy (Opera) |
| `QUIPNET_ALLOW_UNSIGNED_RELEASE=1` | Разрешить релиз без секрета WSS |
| `QUIPNET_SKIP_CF_SECRET_PARITY=1` | Пропустить проверку совпадения секрета с общим файлом |

### Защита релиза

Релизная сборка без секрета WSS не собирается (ошибка на этапе конфигурации). Это предотвращает выпуск APK, который выглядит рабочим, но теряет доступ к `nova-app.eu` при включении обязательной проверки на сервере.

Ключ релея: `quipnet-android-164` (логин несёт `versionCode`, по правилу D17 ключ выдаётся сервером на каждый выпуск).

---

## 10. F-Droid

### Метаданные: `fdroid/com.quipnet.android.yml`

- **License:** GPL-3.0-only
- **AuthorName:** confeden
- **SourceCode:** https://github.com/confeden/Nova-Android
- **AntiFeatures:** NonFreeNet (встроенные транспорт подключаются к Cloudflare WARP/MASQUE и прокси Opera)
- **AutoName:** QuipNet
- **CurrentVersion:** 1.30.1 (versionCode 150)
- **Binaries:** ссылка на эталонный APK в релизе GitHub
- **AllowedAPKSigningKeys:** отпечаток сертификата владельца

**Особенности сборки для F-Droid:**
- Удаляется `app/src/main/jniLibs` и `app/libs/quipnet-core-api24-stripped.aar` перед сборкой (готовые бинарники запрещены)
- Пересобираются все нативные библиотеки: `quipnet-core` (gomobile), `quipnet-xray`, `opera-proxy`, `tun2proxy`, `stormdns`
- `gomobile bind` запускается с `-trimpath` для воспроизводимости
- Go устанавливается из trixie-backports (golang-1.26-go 1.26.5)
- Rust (cargo-ndk 4.1.2) для tun2proxy
- CMake 3.22.1 из SDK
- Секрет WSS не требуется (`quipnetAllowUnsignedRelease=true`)

### Почему ссылка в README ведет на старый репозиторий

В README всё ещё указана ссылка `https://github.com/confeden/Nova-Android/releases` — это старое место релиза. Новый репозиторий: `https://github.com/neverjoskiy/quipnet`.

---

## 11. Текущее состояние git

```
Ветка: main
Коммит: a620bcc "Initial commit" (единственный)
remote: origin -> https://github.com/neverjoskiy/quipnet
Статус: чистый (все изменения закоммичены)
```

Старая история полностью удалена. Один коммит "Initial commit" без упоминания переименования.

---

## 12. Известные проблемы и план работ

### Критические

1. **AAR содержит старые классы `nova.Nova` вместо `quipnet.Quipnet`** — временно обход приведеном импортов к `nova.Nova`. Для полного исправления нужно пересобрать AAR через `gomobile bind`.

### Неблокирующие (косметические)

2. Файл `tools/gen_nova_themes.py` — имя всё ещё содержит `nova`.
3. Файл `res/values/attrs_nova_theme.xml` — имя содержит `nova`.
4. Различные комментарии в коде с упоминанием "Nova" (например, "туннель Quipnet" → "туннель Nova" в некоторых местах).
5. Ссылка в README на старый репозиторий `confeden/Nova-Android`.

### План

1. Запустить GitHub Actions сборку на коммите `a620bcc` — проверить, что APK собирается.
2. Если сборка проходит — проект готов к выпуску.
3. При возможности пересобрать AAR на машине с Go/gomobile и вернуть импорты к `quipnet.Quipnet`.
4. Обновить ссылку в README на новый репозиторий.

---

## 13. Лицензия

QuipNet распространяется под **GNU General Public License v3.0**. Сторонние компоненты сохраняют свои лицензии (см. `THIRD-PARTY.md`).

---

*Документ составлен на основе текущего состояния репозитория `https://github.com/neverjoskiy/quipnet` (ветка `main`, коммит `a620bcc`).*