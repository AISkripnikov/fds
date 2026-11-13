# airMAX Field Tool — Этап 1: подключение и экран статуса. План реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android-приложение (APK), которое находит пушку airMAX (airOS 6 и 8) в сети телефона (Wi‑Fi или USB-Ethernet), входит в неё и в тёмной теме раз в секунду показывает сигнал, шум, SNR, airMAX качество и ёмкость, TX/RX, цепи и график. Есть звук юстировки, демо-режим и диагностический дамп.

**Architecture:** Один Gradle-модуль `app`. Сетевой слой (OkHttp, привязанный к конкретной Android `Network`) → клиенты `AirOs6Client` / `AirOs8Client` за общим интерфейсом `AirOsClient` → парсеры в единую модель `DeviceStatus` → `StatusViewModel` (поток опроса) → Compose-экраны. Вся логика протоколов покрыта JVM-тестами на `MockWebServer` и JSON-фикстурах.

**Tech Stack:** Kotlin 2.0, Jetpack Compose (Material 3, BOM 2024.10), OkHttp 4.12 + MockWebServer, kotlinx.serialization-json 1.7, kotlinx.coroutines 1.9, androidx.security-crypto 1.1.0-alpha06, JUnit 4, AGP 8.7, Gradle 8.10, JDK 17.

**Spec:** `docs/superpowers/specs/2026-11-13-airmax-field-tool-design.md`

**Следующие этапы (отдельные планы):** 2 — станции и инструменты (site survey, бэкап, перезагрузка); 3 — чтение настроек и справка (bottom sheet); 4 — безопасная запись, первичная настройка, мастер моста, изменения на обоих концах.

## Global Constraints

- Пакет `com.airmaxfield`, имя приложения «airMAX Поле». minSdk 26, targetSdk 35, compileSdk 35.
- Приложение не делает ни одного запроса в интернет. Разрешения: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `VIBRATE`.
- Самоподписанные сертификаты принимаются **только** для хостов из частных диапазонов (10/8, 172.16/12, 192.168/16, 169.254/16) и только в клиенте пушек.
- Пароли хранятся только в `EncryptedSharedPreferences`.
- Весь текст интерфейса на русском.
- Тёмная тема (спец. 5.1a): фон `#14181E`, карточки `#1E242C`, рамка `#2D3540`, текст `#EEF2F6`, вторичный `#9AA6B4`, акцент `#3D8EF0`, хорошо `#5FD38A`, средне `#E0B44A`, плохо `#E05A5A`. Цифра сигнала 56sp, подписи не меньше 13sp. Светлая тема-опция: фон `#F2F4F7`, карточки `#FFFFFF`, рамка `#DFE3E8`, текст `#1D2733`, вторичный `#5F6B7A`, акцент `#1F6FD1`.
- Пороги качества **[решение исполнителя, спец. 5.5]**: сигнал ≥ −65 хорошо, −65…−75 средне, < −75 плохо; SNR ≥ 25 хорошо, 15…25 средне, < 15 плохо; airMAX качество и ёмкость ≥ 80 хорошо, 50…80 средне, < 50 плохо.
- Период опроса 1000 мс. Таймаут HTTP 4 с.
- Адреса быстрого подключения: `192.168.1.20`, `192.168.172.1`. Логин по умолчанию `ubnt`.

## Review Focus

1. **Телефон на Wi‑Fi пушки без интернета, мобильные данные включены.** Все запросы должны идти через выбранную сеть, а не через мобильную. Тест: `NetworkSelectorTest` (Task 3).
2. **Сессия истекла посреди опроса** (пушка перезагрузилась, кто-то вошёл в веб). Ожидается тихий повторный вход, без ошибки на экране. Тесты `relogin on 401` / `relogin on login redirect` (Tasks 4, 5).
3. **Старая или другая прошивка без части полей** (нет `polling`, нет `chainrssi`, нет `remote`). Ожидается «—» на месте значения, без падения. Тесты `parses minimal status` (Tasks 4, 5).
4. **Неверный пароль.** Ожидается понятная ошибка «Неверный логин или пароль» и остановка повторных попыток. Тесты `wrong password` (Tasks 4, 5).
5. **Пушка пропала (повернули антенну, отвалилось питание).** Экран не очищается, показывается «нет данных N с», опрос продолжается. Тест `StatusViewModelTest.keeps last status on failure` (Task 8).

---

### Task 0: Окружение сборки

Нет кода и коммита: это подготовка ПК. **Скачивание требует разрешения пользователя**: перед шагами 1–2 назвать файлы и размеры и получить «да».

- [ ] **Step 1:** Установить JDK 17 (Eclipse Temurin, `winget install EclipseAdoptium.Temurin.17.JDK`, ~190 МБ). Проверка: `java -version` → `17.x`.
- [ ] **Step 2:** Установить Android command-line tools (zip с developer.android.com, ~150 МБ) в `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`. Задать `ANDROID_HOME`. `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`, `sdkmanager --licenses` (лицензии показать пользователю и принять только с его согласия).
- [ ] **Step 3:** Установить Gradle 8.10 (zip) только для генерации wrapper. Проверка: `gradle -v`.

### Task 1: Каркас проекта и тема

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `.gitignore` (включая `.superpowers/`, `build/`, `local.properties`)
- Create: `app/src/main/java/com/airmaxfield/MainActivity.kt`
- Create: `app/src/main/java/com/airmaxfield/ui/theme/Theme.kt`, `Colors.kt`, `Type.kt`
- Create: `app/src/main/java/com/airmaxfield/ui/theme/Quality.kt`
- Test: `app/src/test/java/com/airmaxfield/ui/theme/QualityTest.kt`

**Interfaces:**
- Produces: `enum class Quality { GOOD, FAIR, BAD, UNKNOWN }`; `fun signalQuality(dbm: Int?): Quality`; `fun snrQuality(db: Int?): Quality`; `fun percentQuality(p: Int?): Quality`; `@Composable fun AirmaxTheme(dark: Boolean = true, content: @Composable () -> Unit)`; `@Composable fun Quality.color(): Color`.

- [ ] **Step 1:** `git init` в `airmax-tool/`, сгенерировать `gradle wrapper --gradle-version 8.10`, написать Gradle-файлы с зависимостями из Tech Stack, плагины `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`.
- [ ] **Step 2: Failing test**

```kotlin
class QualityTest {
    @Test fun signal() {
        assertEquals(Quality.GOOD, signalQuality(-65))
        assertEquals(Quality.FAIR, signalQuality(-66))
        assertEquals(Quality.FAIR, signalQuality(-75))
        assertEquals(Quality.BAD, signalQuality(-76))
        assertEquals(Quality.UNKNOWN, signalQuality(null))
    }
    @Test fun snr() {
        assertEquals(Quality.GOOD, snrQuality(25)); assertEquals(Quality.FAIR, snrQuality(15)); assertEquals(Quality.BAD, snrQuality(14))
    }
    @Test fun percent() {
        assertEquals(Quality.GOOD, percentQuality(80)); assertEquals(Quality.FAIR, percentQuality(50)); assertEquals(Quality.BAD, percentQuality(49))
    }
}
```

- [ ] **Step 3:** `./gradlew :app:testDebugUnitTest --tests '*QualityTest'` → FAIL (не компилируется).
- [ ] **Step 4:** Реализовать `Quality.kt` и тему с цветами из Global Constraints. `MainActivity` показывает `AirmaxTheme { Text("airMAX Поле") }`.
- [ ] **Step 5:** Тест → PASS. `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`, есть `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 6:** Commit `feat: project skeleton, theme, quality thresholds`.

### Task 2: Единая модель статуса

**Files:**
- Create: `app/src/main/java/com/airmaxfield/airos/DeviceStatus.kt`

**Interfaces:**
- Produces:

```kotlin
enum class AirOsVersion { V6, V8 }
data class DeviceStatus(
    val version: AirOsVersion,
    val hostname: String?, val model: String?, val firmware: String?, val uptimeSec: Long?,
    val mode: String?,            // "ap-ptp", "sta-ptp", "ap", "sta" — как отдаёт пушка
    val essid: String?, val frequencyMhz: Int?, val channelWidthMhz: Int?, val txPowerDbm: Int?, val distanceM: Int?,
    val signalDbm: Int?, val noiseDbm: Int?, val chainSignalDbm: List<Int>,   // пустой список, если нет
    val remoteSignalDbm: Int?, val ccqPercent: Int?,
    val airmaxQuality: Int?, val airmaxCapacity: Int?,
    val txRateMbps: Double?, val rxRateMbps: Double?,
    val lanSpeed: String?,
) { val snrDb: Int? get() = if (signalDbm != null && noiseDbm != null) signalDbm - noiseDbm else null }
```

Без отдельного теста: модель проверяется тестами парсеров. Коммит вместе с Task 4.

### Task 3: Сетевой слой с привязкой к сети

**Files:**
- Create: `app/src/main/java/com/airmaxfield/net/NetworkSelector.kt`
- Create: `app/src/main/java/com/airmaxfield/net/DeviceHttp.kt`
- Test: `app/src/test/java/com/airmaxfield/net/NetworkSelectorTest.kt`, `DeviceHttpTest.kt`

**Interfaces:**
- Produces:
  - `data class LocalNet(val id: String, val kind: Kind, val ipv4: String?, val prefix: Int?, val hasInternet: Boolean) { enum class Kind { WIFI, ETHERNET, CELLULAR, OTHER } }`
  - `fun chooseNetwork(nets: List<LocalNet>, targetIp: String): LocalNet?`: чистая функция. Выбирает WIFI/ETHERNET, в чьей подсети лежит `targetIp`. Если такой нет, первую WIFI, затем ETHERNET. CELLULAR никогда.
  - `fun isPrivateIp(host: String): Boolean`
  - `class AndroidNetworks(ctx: Context) { fun list(): List<Pair<LocalNet, android.net.Network>> }`: обёртка над `ConnectivityManager`, без тестов.
  - `fun deviceHttpClient(socketFactory: SocketFactory? = null, allowLoopback: Boolean = false): OkHttpClient`: таймауты 4 с, in-memory `CookieJar`, `followRedirects(false)`, TrustManager и HostnameVerifier, принимающие любой сертификат только если `isPrivateIp(host)` (или хост 127.0.0.1 при `allowLoopback`, только для тестов), иначе ошибка.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun prefers_wifi_in_same_subnet_and_never_cellular() {
    val cell = LocalNet("c", CELLULAR, "10.20.0.5", 24, true)
    val wifi = LocalNet("w", WIFI, "192.168.172.10", 24, false)
    val eth  = LocalNet("e", ETHERNET, "192.168.1.100", 24, false)
    assertEquals("e", chooseNetwork(listOf(cell, wifi, eth), "192.168.1.20")?.id)
    assertEquals("w", chooseNetwork(listOf(cell, wifi, eth), "192.168.172.1")?.id)
    assertEquals("w", chooseNetwork(listOf(cell, wifi), "10.0.0.1")?.id)
    assertNull(chooseNetwork(listOf(cell), "192.168.1.20"))
}
@Test fun private_ranges() {
    assertTrue(isPrivateIp("192.168.1.20")); assertTrue(isPrivateIp("10.1.2.3")); assertTrue(isPrivateIp("172.31.0.1")); assertTrue(isPrivateIp("169.254.1.1"))
    assertFalse(isPrivateIp("172.32.0.1")); assertFalse(isPrivateIp("8.8.8.8")); assertFalse(isPrivateIp("example.com"))
}
```

`DeviceHttpTest`: `MockWebServer` с самоподписанным сертификатом (`okhttp-tls` `HeldCertificate`) на `127.0.0.1`. С `deviceHttpClient(allowLoopback = true)` запрос проходит, с `allowLoopback = false` падает с `SSLException`. Cookie из `Set-Cookie` первого ответа уходят во втором запросе.

- [ ] **Step 2:** Тесты → FAIL. **Step 3:** Реализовать. **Step 4:** Тесты → PASS. **Step 5:** Commit `feat: network selection and device http client`.

### Task 4: Клиент airOS 8

**Files:**
- Create: `app/src/main/java/com/airmaxfield/airos/AirOsClient.kt`, `AirOs8Client.kt`, `StatusParser8.kt`
- Create: `app/src/test/resources/fixtures/airos8/status_full.json`, `status_minimal.json`
- Test: `app/src/test/java/com/airmaxfield/airos/AirOs8ClientTest.kt`, `StatusParser8Test.kt`

**Interfaces:**
- Consumes: `deviceHttpClient`, `DeviceStatus`.
- Produces:

```kotlin
sealed class AirOsError(msg: String) : Exception(msg) {
    class BadCredentials : AirOsError("Неверный логин или пароль")
    class Unreachable(cause: Throwable?) : AirOsError("Пушка не отвечает")
    class Protocol(detail: String) : AirOsError("Непонятный ответ пушки: $detail")
    class FactoryDefault : AirOsError("Пушка в заводском состоянии")   // нужен первый вход (этап 4)
}
interface AirOsClient {
    val version: AirOsVersion
    suspend fun login()
    suspend fun status(): DeviceStatus          // сам делает повторный вход один раз при истёкшей сессии
    suspend fun rawDump(): Map<String, String>  // путь → тело ответа, для диагностики
}
class AirOs8Client(baseUrl: HttpUrl, user: String, pass: String, http: OkHttpClient) : AirOsClient
object StatusParser8 { fun parse(json: String): DeviceStatus }
```

- Протокол: `POST /api/auth` form `username`, `password` → 200 + cookie (`AIROS_*`) + заголовок `X-CSRF-ID`, его прикладывать ко всем последующим запросам. 401/403 на auth → `BadCredentials`. `GET /status.cgi` → JSON. 401/403 или редирект на `/login.cgi` при опросе → один повторный `login()` и повтор запроса, вторая неудача пробрасывается.
- `rawDump`: `/status.cgi`, `/getcfg.sh` (если 404, то пропустить).
- Маппинг `StatusParser8` (все поля необязательны, `ignoreUnknownKeys`): `host.hostname/devmodel/fwversion/uptime`; `wireless.mode/essid/frequency/chanbw/txpower/distance/noisef`; первый элемент `wireless.sta[]` → `signal`, `chainrssi` (перевести каждую ненулевую цепь в dBm как `rssi + noisef`), `remote.signal`, `airmax.quality`, `airmax.capacity`, `tx_rate`/`rx_rate` или `airmax.downlink_capacity`/`uplink_capacity` в Мбит/с; если `sta` пуст, то `wireless.signal`/`wireless.rssi`. **Структура фикстур собрана по открытым образцам и требует сверки с реальным дампом (спец. 5.6)**, поэтому парсер обязан выживать при отсутствии любого поля.

- [ ] **Step 1:** Написать фикстуры. `status_full.json`: NanoStation 5AC loco, `fwversion` `"v8.7.11"`, mode `"ap-ptp"`, frequency 5180, chanbw 40, noisef −92, одна станция: signal −58, chainrssi `[33,31,0]`, remote.signal −60, airmax.quality 96, capacity 78. `status_minimal.json`: только `host.hostname` и `wireless.mode`.
- [ ] **Step 2: Failing tests**

```kotlin
@Test fun parses_full() { val s = StatusParser8.parse(fixture("airos8/status_full.json"))
    assertEquals(-58, s.signalDbm); assertEquals(-92, s.noiseDbm); assertEquals(34, s.snrDb)
    assertEquals(listOf(-59, -61), s.chainSignalDbm); assertEquals(-60, s.remoteSignalDbm)
    assertEquals(96, s.airmaxQuality); assertEquals(78, s.airmaxCapacity); assertEquals(5180, s.frequencyMhz); assertEquals(40, s.channelWidthMhz) }
@Test fun parses_minimal() { val s = StatusParser8.parse(fixture("airos8/status_minimal.json"))
    assertNull(s.signalDbm); assertTrue(s.chainSignalDbm.isEmpty()); assertNull(s.snrDb) }
@Test fun garbage_is_protocol_error() { assertThrows(AirOsError.Protocol::class.java) { StatusParser8.parse("<html>") } }
```

`AirOs8ClientTest` (MockWebServer, HTTP): `login sends form and stores csrf` (второй запрос содержит `X-CSRF-ID: abc` и cookie); `wrong password` (auth → 403, ожидается `BadCredentials`, ровно 1 запрос); `relogin on 401` (status 401 → auth 200 → status 200: результат распарсен, 4 запроса); `second failure propagates`; `unreachable` (сервер выключен → `Unreachable`).

- [ ] **Step 3:** → FAIL. **Step 4:** Реализовать. **Step 5:** → PASS. **Step 6:** Commit `feat: airOS 8 client and status parser`.

### Task 5: Клиент airOS 6

**Files:**
- Create: `app/src/main/java/com/airmaxfield/airos/AirOs6Client.kt`, `StatusParser6.kt`
- Create: `app/src/test/resources/fixtures/airos6/status_full.json`, `status_minimal.json`
- Test: `AirOs6ClientTest.kt`, `StatusParser6Test.kt`

**Interfaces:**
- Consumes: `AirOsClient`, `AirOsError`, `DeviceStatus`, `deviceHttpClient`.
- Produces: `class AirOs6Client(baseUrl: HttpUrl, user: String, pass: String, http: OkHttpClient) : AirOsClient`; `object StatusParser6 { fun parse(json: String): DeviceStatus }`.

- Протокол: `GET /login.cgi` (получить cookie `AIROS_SESSIONID`) → `POST /login.cgi` multipart `username`, `password`, `uri=/status.cgi`. Успех: 302 на `/status.cgi` или любой путь, кроме `/login.cgi`. Если снова `/login.cgi` или 200 со страницей логина, то `BadCredentials`. Опрос `GET /status.cgi`; если ответ HTML (страница логина) или 302 на `/login.cgi`, то один повторный вход. `rawDump`: `/status.cgi`, `/sta.cgi`, `/signal.cgi`.
- Маппинг: `host.*` как в v8; `wireless.signal`, `wireless.noisef`, `wireless.chainrssi` (цепь в dBm = `rssi + noisef`, нули отбросить), `wireless.ccq` (в прошивке ×10: 951 → 95), `wireless.polling.quality/capacity`, `wireless.txrate`/`rxrate` (строки → Double), `wireless.frequency` (строка `"5180 MHz"` или число → Int), `wireless.chwidth`, `wireless.mode`, `wireless.distance`, `wireless.txpower`. Удалённого сигнала в status.cgi v6 нет (`remoteSignalDbm = null`, позже `/sta.cgi`).

- [ ] **Step 1:** Фикстуры: `status_full.json`: NanoStation M5, `fwversion` `"XW.ar934x.v6.3.6"`, mode `"sta"`, frequency `"5180 MHz"`, chwidth 40, signal −62, noisef −95, chainrssi `[32,34,0]`, ccq 951, polling.quality 91, capacity 74, txrate `"300"`, rxrate `"270"`. `status_minimal.json`: `host.hostname` и `wireless.mode`.
- [ ] **Step 2: Failing tests:** `parses_full` (−62, −95, SNR 33, цепи `[-63,-61]`, ccq 95, частота 5180, rate 300.0/270.0, `remoteSignalDbm` null), `parses_minimal`, `login_follows_cookie_flow` (сервер: GET /login.cgi → Set-Cookie; POST multipart с полями `username`/`password`/`uri` и той же cookie → 302 /status.cgi), `wrong password` (POST → 302 /login.cgi → `BadCredentials`), `relogin on login redirect` (status → 302 /login.cgi → полный повторный вход → status 200).
- [ ] **Step 3:** → FAIL. **Step 4:** Реализовать. **Step 5:** → PASS. **Step 6:** Commit `feat: airOS 6 client and status parser`.

### Task 6: Определение версии и фабрика клиента

**Files:**
- Create: `app/src/main/java/com/airmaxfield/airos/AirOsConnector.kt`
- Test: `AirOsConnectorTest.kt`

**Interfaces:**
- Consumes: оба клиента.
- Produces: `suspend fun connect(host: String, user: String, pass: String, http: OkHttpClient, scheme: String = "https"): AirOsClient`. Пробует `POST /api/auth`: 200 → `AirOs8Client`, уже вошедший; 401/403 → `BadCredentials`; 404 → `AirOs6Client` + `login()`. Ошибка TLS или соединения по https → один повтор по `http`. Признак заводского состояния на этапе 1 не обрабатывается, кроме случая, когда ответ auth явно требует первого входа: тогда `FactoryDefault`.

- [ ] **Step 1: Failing tests:** `detects_v8`, `detects_v6_on_404`, `bad_credentials_v8`, `falls_back_to_http` (scheme `https` на MockWebServer без TLS → коннектор пробует `http` и находит v8).
- [ ] **Steps 2–4:** FAIL → реализовать → PASS. **Step 5:** Commit `feat: airOS version detection`.

### Task 7: Хранилище пушек и обнаружение

**Files:**
- Create: `app/src/main/java/com/airmaxfield/data/SavedDevice.kt`, `DeviceStore.kt`
- Create: `app/src/main/java/com/airmaxfield/discovery/UbntDiscovery.kt`
- Test: `app/src/test/java/com/airmaxfield/discovery/UbntDiscoveryParserTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class SavedDevice(val id: String, val name: String, val host: String, val user: String, val pass: String)`
  - `class DeviceStore(ctx: Context) { fun all(): List<SavedDevice>; fun save(d: SavedDevice); fun delete(id: String) }`: JSON-список в `EncryptedSharedPreferences` (`MasterKey.KeyScheme.AES256_GCM`). Без unit-теста (нужен Android Keystore), проверяется руками в Task 10.
  - `data class Discovered(val ip: String, val mac: String?, val hostname: String?, val model: String?, val firmware: String?, val essid: String?)`
  - `object UbntDiscoveryParser { fun parse(packet: ByteArray, fromIp: String): Discovered? }`
  - `class UbntDiscovery(socketBinder: (DatagramSocket) -> Unit) { suspend fun scan(timeoutMs: Long = 2500): List<Discovered> }`: шлёт `byteArrayOf(1,0,0,0)` на `255.255.255.255:10001` через сокет, привязанный к выбранной `Network` (`network.bindSocket`), собирает ответы, дедуп по IP.
- Формат ответа: байт 0 = версия (1), байт 1 = команда (0), байты 2–3 = длина тела (BE), далее TLV: тип 1 байт, длина 2 байта BE, значение. Типы: `0x02` MAC(6)+IPv4(4) (может повторяться), `0x01` MAC, `0x03` прошивка (строка), `0x0B` hostname, `0x0C` короткая модель, `0x0D` ESSID, `0x14` полная модель (предпочесть её `0x0C`).

- [ ] **Step 1: Failing test:** собрать пакет в тесте: TLV `0x02` (MAC `24:5A:4C:11:22:33`, IP `192.168.1.20`), `0x0B` `"Sever-A"`, `0x0C` `"LOCO5AC"`, `0x14` `"NanoStation 5AC loco"`, `0x03` `"WA.v8.7.11"`. Ожидание `Discovered("192.168.1.20", "24:5A:4C:11:22:33", "Sever-A", "NanoStation 5AC loco", "WA.v8.7.11", null)`. Плюс `truncated packet returns null` и `unknown tlv skipped`.
- [ ] **Steps 2–4:** FAIL → реализовать → PASS. **Step 5:** Commit `feat: device store and ubnt discovery`.

### Task 8: Опрос статуса (ViewModel) и демо-режим

**Files:**
- Create: `app/src/main/java/com/airmaxfield/ui/status/StatusViewModel.kt`
- Create: `app/src/main/java/com/airmaxfield/airos/DemoClient.kt`
- Test: `app/src/test/java/com/airmaxfield/ui/status/StatusViewModelTest.kt`, `DemoClientTest.kt`

**Interfaces:**
- Consumes: `AirOsClient`, `DeviceStatus`, `AirOsError`.
- Produces:

```kotlin
data class StatusUi(
    val status: DeviceStatus?,          // последний успешный
    val history: List<Int>,             // сигнал dBm, последние 60 точек
    val secondsSinceUpdate: Int,        // 0, если последний опрос успешен
    val error: AirOsError?,             // последняя ошибка, null при успехе
    val stopped: Boolean,               // true после BadCredentials: опрос остановлен
)
class StatusPoller(client: AirOsClient, scope: CoroutineScope, periodMs: Long = 1000, clock: () -> Long) {
    val state: StateFlow<StatusUi>; fun start(); fun stop()
}
class StatusViewModel : ViewModel   // держит StatusPoller, подключение через AirOsConnector
class DemoClient(seed: Long = 1) : AirOsClient   // AirOsVersion.V8; сигнал плавно гуляет −72…−55 (синус + шум), остальное из fixtures-подобных констант
```

- [ ] **Step 1: Failing tests** (`kotlinx-coroutines-test`, `runTest`, виртуальное время):
  - `history keeps last 60 points`: 70 успешных опросов → `history.size == 60`, последний элемент = последний сигнал.
  - `keeps last status on failure`: успех, затем 3 `Unreachable` → `status` прежний, `secondsSinceUpdate == 3`, `error is Unreachable`, `stopped == false`.
  - `stops on bad credentials`: `BadCredentials` → `stopped == true`, дальнейших вызовов `status()` нет.
  - `DemoClientTest`: 100 вызовов `status()` → все `signalDbm` в `-72..-55`, не все равны.
- [ ] **Steps 2–4:** FAIL → реализовать → PASS. **Step 5:** Commit `feat: status poller and demo client`.

### Task 9: Экраны: список пушек, подключение, статус

**Files:**
- Create: `app/src/main/java/com/airmaxfield/ui/AppNav.kt` (нижняя панель: Статус, Станции, Настройки, Инструменты, Справка; все, кроме «Статус», на этапе 1 показывают заглушку «Будет в следующей версии»)
- Create: `ui/devices/DevicesScreen.kt`, `ui/devices/ConnectSheet.kt`
- Create: `ui/status/StatusScreen.kt`, `ui/components/SignalCard.kt`, `MetricTile.kt`, `SignalGraph.kt`, `ConnectionBar.kt`
- Create: `ui/status/AlignmentTone.kt`

**Interfaces:**
- Consumes: `DeviceStore`, `UbntDiscovery`, `AndroidNetworks`, `chooseNetwork`, `AirOsConnector.connect`, `StatusViewModel`, `DemoClient`, `Quality`.
- Produces: `class AlignmentTone { fun update(signalDbm: Int?); fun setEnabled(on: Boolean); fun release() }`: `AudioTrack` (синус), частота = линейно 300 Гц при −90 dBm … 1500 Гц при −45 dBm, коротким писком каждые 200 мс; `null` → тишина. Плюс вибрация 30 мс при улучшении на ≥ 2 dB.

- Экран «Устройства» (стартовый): блок «Сеть телефона» (каждая `LocalNet`: тип, IP, «без интернета»); кнопки быстрых адресов `192.168.1.20` и `192.168.172.1`; кнопка «Найти пушки» (результаты `UbntDiscovery`: модель, имя, IP, прошивка); список сохранённых; «Демо-режим»; в меню «⋮» переключатель «Светлая тема» (хранится в обычных `SharedPreferences`, применяется через `AirmaxTheme(dark = …)`). Нажатие → `ConnectSheet` (IP, логин `ubnt`, пароль, галка «Сохранить»). Если `chooseNetwork` вернул null или IP телефона не в подсети цели, то жёлтое предупреждение: «Телефон не в сети пушки. Для сброшенной пушки задайте телефону адрес 192.168.1.100, маска 255.255.255.0» (спец. 5.9).
- Экран «Статус» по макету B (спец. 5.1a): шапка (имя, модель · IP, точка «на связи» зелёная / «нет данных N с» жёлтая); карточка сигнала (56sp, цвет по `signalQuality`, градиентная шкала −90…−40 с маркером, «Цепь 0 / Цепь 1», «Удалённый сигнал»); сетка 2×3 `MetricTile` (Шум, SNR, airMAX качество — для v6 при отсутствии показывать CCQ, airMAX ёмкость, TX, RX); `SignalGraph` (Canvas, 60 точек); сводка (режим, SSID, частота/ширина, мощность, дистанция, uptime, прошивка); переключатель «🔊 Звук юстировки». Отсутствующее значение → «—».
- Ошибки: `BadCredentials` → диалог с кнопкой «Изменить пароль»; `Protocol` → текст + кнопка «Сохранить диагностический дамп».

- [ ] **Step 1:** Реализовать экраны. Для каждого экрана `@Preview` на `DemoClient`-данных в тёмной теме.
- [ ] **Step 2:** `./gradlew :app:assembleDebug` → успех; `./gradlew :app:testDebugUnitTest` → все PASS.
- [ ] **Step 3:** Commit `feat: devices, connect and status screens`.

### Task 10: Диагностический дамп, иконка, финальная сборка

**Files:**
- Create: `app/src/main/java/com/airmaxfield/diag/DiagDump.kt`, иконка `app/src/main/res/mipmap-*/ic_launcher*` (простая: антенна на тёмном фоне)
- Modify: `StatusScreen.kt` (меню «⋮ → Сохранить дамп»)
- Test: `app/src/test/java/com/airmaxfield/diag/DiagDumpTest.kt`

**Interfaces:**
- Consumes: `AirOsClient.rawDump()`.
- Produces: `object DiagDump { fun buildZip(dump: Map<String, String>, meta: Map<String, String>, out: OutputStream) }`: zip с файлом на каждый путь (`status.cgi.json`, …) и `meta.txt` (модель, версия, время, версия приложения). **В дамп не попадают пароль, cookie и CSRF**. Сохранение через `ACTION_CREATE_DOCUMENT` (SAF), чтобы пользователь сам выбрал папку и мог переслать файл.

- [ ] **Step 1: Failing test:** `zip contains each path and meta, no secrets`: в meta передан `pass=secret`, функция обязана выбросить ключи `pass`, `password`, `cookie`, `csrf` (регистр не важен); распаковать и проверить имена файлов и отсутствие `secret`.
- [ ] **Steps 2–4:** FAIL → реализовать → PASS.
- [ ] **Step 5:** `./gradlew :app:assembleRelease` с debug-подписью (`signingConfig = signingConfigs.getByName("debug")`, на этапе 1 этого достаточно для установки файлом) → скопировать в `airmax-tool/dist/airmax-pole-stage1.apk`.
- [ ] **Step 6:** Ручная проверка в эмуляторе или на телефоне пользователя: демо-режим, график, звук, список сетей. На реальной пушке (у пользователя): подключение v6 и v8, переход на другую сеть, неверный пароль, снятие дампа. Результаты записать в `docs/superpowers/plans/stage1-field-check.md`.
- [ ] **Step 7:** Commit `feat: diagnostic dump, icon, stage 1 build`.
