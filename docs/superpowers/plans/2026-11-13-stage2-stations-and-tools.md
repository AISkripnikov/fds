# airMAX Field Tool — Этап 2: станции и инструменты. План реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** вкладки «Станции» (кто подключён и с каким сигналом в обе стороны) и «Инструменты» (обзор эфира, резервная копия конфигурации на телефон, перезагрузка) для airOS 6 и 8.

**Architecture:** расширение `AirOsClient` четырьмя методами (`stations`, `survey`, `downloadConfig`, `reboot`), парсеры в `airos/`, все вызовы клиента сериализуются `Mutex`-ом (опрос и инструменты не дерутся за сессию). Экраны в `ui/stations/` и `ui/tools/`. Резервные копии хранятся в памяти приложения (`filesDir/backups/`) — понадобятся для отката на этапе 4 — и по запросу экспортируются через SAF.

**Tech Stack:** как в этапе 1.

**Spec:** `docs/superpowers/specs/2026-11-13-airmax-field-tool-design.md` (5.2 п. 3–4).

## Global Constraints

- Всё из этапа 1 (тема, пороги, только частные IP, только сеть пушки, русский текст).
- **Восстановление из резервной копии — не на этом этапе** (это запись, этап 4 с безопасным применением).
- Обзор эфира на airOS на несколько секунд прерывает связь: перед запуском — предупреждение и подтверждение.
- Перезагрузка — только после диалога подтверждения с именем пушки.
- Эндпоинты собраны по открытым источникам и **требуют сверки с реальными дампами**; все новые пути добавляются в `rawDump()`.

## Эндпоинты [решение исполнителя]

| | airOS 8 | airOS 6 |
|---|---|---|
| Станции | `status.cgi` → `wireless.sta[]` | `GET /sta.cgi` (JSON-массив) |
| Обзор эфира | `GET /survey.json.cgi?iface=ath0` | `GET /survey.json.cgi?iface=ath0` |
| Конфигурация | `GET /getcfg.sh`, при 404 — `GET /cfg.cgi` | `GET /cfg.cgi` |
| Перезагрузка | `POST /reboot.cgi` (`reboot=yes`, `X-CSRF-ID`) | `POST /reboot.cgi` (`reboot=yes`) |

## Review Focus

1. **Обзор эфира разрывает связь с пушкой, к которой подключён телефон** (через её же радио). Ожидается: запрос может упасть по таймауту — показать «связь прервалась на время обзора, повторите получение результатов», а не ошибку протокола; опрос статуса продолжается.
2. **Конфигурация пришла как HTML-страница логина или пустая.** Ожидается: не сохранять мусор как резервную копию. Тест `rejects_non_config` (Task 3).
3. **Перезагрузка отключает пушку на ~60 с.** Ожидается: экран статуса показывает «нет данных», потом сам восстанавливается (сессия заново входит).
4. **Нет станций** (AP без клиентов, Station без связи). Ожидается: понятное «нет подключённых станций», не пустой экран.
5. **Пароли и ключи в резервной копии.** Копия — это полный `system.cfg` (так и нужно для отката), но в диагностический дамп она не попадает, а экспорт — только по явному действию пользователя.

---

### Task 1: Станции

**Files:** Create `airos/StationInfo.kt`, `airos/StationsParser.kt`; Modify `AirOsClient.kt`, `AirOs6Client.kt`, `AirOs8Client.kt`, `DemoClient.kt`; Test `StationsParserTest.kt`, fixtures `airos6/sta.json`.

**Interfaces — Produces:**
```kotlin
data class StationInfo(
    val mac: String?, val name: String?, val ip: String?,
    val signalDbm: Int?, val remoteSignalDbm: Int?, val noiseDbm: Int?, val chainSignalDbm: List<Int>,
    val ccqPercent: Int?, val airmaxQuality: Int?, val airmaxCapacity: Int?,
    val txRateMbps: Double?, val rxRateMbps: Double?, val distanceM: Int?, val uptimeSec: Long?, val latencyMs: Int?,
)
object StationsParser { fun fromStatus8(json: String): List<StationInfo>; fun fromSta6(json: String): List<StationInfo> }
// AirOsClient: suspend fun stations(): List<StationInfo>
```
- v8: каждый элемент `wireless.sta[]`: `mac`, `remote.hostname`→name, `lastip`, `signal`, `remote.signal`, `noisefloor` (или `wireless.noisef`), `chainrssi`, `airmax.quality/capacity`, `tx_rate/rx_rate`, `distance`, `uptime`, `tx_latency`.
- v6 `/sta.cgi`: `mac`, `name`, `lastip`, `signal`, `noisefloor`, `chainrssi`, `ccq` (×10 если >100), `airmax.quality/capacity`, `tx`/`rx` (Мбит/с), `distance`, `uptime`, `remote.signal` (если есть).
- Тесты: v8 full fixture → 1 станция с `remoteSignalDbm=-60`, `name="Sever-B"`, `latencyMs=2`; v6 fixture (2 станции, одна без `remote`) → поля и `null`; `"[]"` → пустой список; мусор → `Protocol`.

### Task 2: Обзор эфира

**Files:** Create `airos/SurveyEntry.kt` (+ parser); Modify клиенты, `DemoClient`; Test `SurveyParserTest.kt`, fixture `survey.json`.

**Produces:**
```kotlin
data class SurveyEntry(val bssid: String?, val ssid: String?, val frequencyMhz: Int?, val channel: Int?,
    val signalDbm: Int?, val noiseDbm: Int?, val security: String?, val mode: String?)
object SurveyParser { fun parse(json: String): List<SurveyEntry> }   // сортировка по сигналу, сильные первыми
data class ChannelLoad(val frequencyMhz: Int, val count: Int, val strongestDbm: Int)
fun channelLoad(entries: List<SurveyEntry>): List<ChannelLoad>        // по возрастанию частоты
// AirOsClient: suspend fun survey(): List<SurveyEntry>
```
- Поля: `mac`→bssid, `essid`, `frequency` (строка `"5.18"`/`"5180"`/число: < 10 → ГГц×1000), `channel`, `signal_level`, `noise_level`, `encryption` / `security`, `ieee_mode`/`mode`. Таймаут чтения для этого запроса 15 с.
- Тесты: частоты `"5.18"`→5180, `5745`→5745; сортировка; `channelLoad` группирует 2 сети на 5180 (count 2, strongest = max).

### Task 3: Резервная копия конфигурации

**Files:** Create `airos/ConfigBackup.kt`, `data/BackupStore.kt`; Modify клиенты; Test `ConfigBackupTest.kt`, клиентские тесты.

**Produces:**
```kotlin
object ConfigBackup {
    fun looksLikeConfig(text: String): Boolean   // ≥ 10 строк вида key=value и есть ключ radio. / wireless. / netconf.
    fun fileName(hostname: String?, host: String, at: java.util.Date): String  // "Sever-A_192-168-1-20_20261113-0930.cfg", небезопасные символы → "_"
}
// AirOsClient: suspend fun downloadConfig(): String  — бросает Protocol("это не конфигурация"), если !looksLikeConfig
class BackupStore(dir: java.io.File) { fun save(name: String, text: String): java.io.File; fun list(): List<java.io.File>; fun delete(f: java.io.File) }
```
- Тесты: `rejects_non_config` (HTML-логин, пустая строка), `accepts_system_cfg`; `fileName` санитизация; v8 клиент: `/getcfg.sh` 404 → `/cfg.cgi`; `BackupStore` на `TemporaryFolder`: save → list (новые первыми) → delete.

### Task 4: Перезагрузка и сериализация клиента

**Files:** Modify клиенты; Create `airos/SerializedClient.kt`; Test клиентские тесты, `SerializedClientTest.kt`.

**Produces:**
```kotlin
// AirOsClient: suspend fun reboot()
class SerializedClient(inner: AirOsClient) : AirOsClient   // все вызовы под одним Mutex
```
- Тесты: v8 reboot → `POST /reboot.cgi`, тело содержит `reboot=yes`, заголовок `X-CSRF-ID`; v6 то же без CSRF; `SerializedClient`: два параллельных `status()` у клиента с задержкой 100 мс не пересекаются (максимум одновременных вызовов = 1).
- Попутно (отложенная мелочь этапа 1, M11): разбор JSON в клиентах — внутри `withContext(Dispatchers.Default)`.

### Task 5: Экраны

**Files:** Create `ui/stations/StationsScreen.kt`, `ui/tools/ToolsScreen.kt`, `ui/tools/ToolsViewModel` (или методы в `AppViewModel`); Modify `AppNav.kt`, `AppViewModel.kt`.

- «Станции»: опрос `stations()` раз в 2 с, пока вкладка открыта. Карточка на станцию: имя/MAC/IP, «Сигнал здесь / там» крупно с цветом, шум, SNR, airMAX качество/ёмкость или CCQ, TX/RX, дистанция, uptime, задержка. Пусто → «Нет подключённых станций».
- «Инструменты»:
  - **Обзор эфира**: кнопка → диалог «На несколько секунд связь с пушкой прервётся. Продолжить?» → список сетей (SSID, BSSID, частота/канал, сигнал с цветом, защита) + блок «Загрузка каналов» (частота, число сетей, сильнейший сигнал). Таймаут/обрыв → текст из Review Focus 1 и кнопка «Получить результаты».
  - **Резервная копия**: «Сохранить конфигурацию» → `downloadConfig()` → `BackupStore.save` → тост; список копий этой пушки (по hostname) с кнопками «Экспорт» (SAF `CreateDocument("text/plain")`) и «Удалить». Подпись: «Восстановление — в следующей версии».
  - **Перезагрузка**: красная кнопка → диалог «Перезагрузить <имя>? Связь пропадёт примерно на минуту.»
- Демо-режим: `DemoClient` отдаёт 1 станцию, 6 сетей обзора, конфигурацию из 20 строк, `reboot()` — no-op.
- Проверка: `assembleRelease` + весь набор тестов; APK в `dist/airmax-pole-stage2.apk`.
