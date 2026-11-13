# airMAX Field Tool — Этап 3: настройки (только чтение) и справка. План реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** вкладки «Настройки» (чтение system.cfg, сгруппированное по разделам с полями и помощью) и «Справка» (автономный справочник с поиском) для airOS 6 и 8.

**Architecture:** парсер `SystemConfig` разбирает `system.cfg` (ключ=значение) в структурированную модель с секциями. Справочные данные — объект Kotlin с описаниями полей и статьями на русском. Экраны в `ui/settings/` и `ui/help/`. Все настройки **только для чтения** — запись откладывается на этап 4 с test-mode rollback.

**Tech Stack:** как в этапе 1–2.

**Spec:** `docs/superpowers/specs/2026-11-13-airmax-field-tool-design.md` (5.2 п. 5–6, 5.4, 5.5, 5.7).

## Global Constraints

- Всё из этапов 1–2 (тема, пороги, только частные IP, только сеть пушки, русский текст).
- **Запись настроек — не на этом этапе.** Все поля read-only. Кнопки «Сохранить» нет.
- Пароли и ключи WPA в конфигурации маскируются в UI (`••••••`), но хранятся в памяти без изменений (нужны для этапа 4).
- Справка хранится отдельным файлом данных, чтобы её было легко править без изменения кода (спец. 5.7).
- Поля, для которых нет справки, показываются как есть (ключ=значение) без кнопки «ⓘ».

## Review Focus

1. **Конфигурация пришла пустая или не загрузилась.** Ожидается: вкладка «Настройки» показывает понятное сообщение «Не удалось загрузить конфигурацию» с кнопкой «Повторить», а не пустой экран и не краш.
2. **Неизвестные ключи в system.cfg.** Ожидается: парсер не теряет их (они нужны для записи на этапе 4), но UI не показывает мусор — незнакомые ключи видны в отдельной секции «Прочее» (свёрнутой).
3. **Демо-режим.** Ожидается: DemoClient возвращает правдоподобную конфигурацию со всеми секциями, чтобы все вкладки настроек были заполнены.
4. **Пароли.** Ожидается: значения полей `*.psk`, `*.password`, `users.*.password` замаскированы в UI.
5. **Справочник без подключения.** Ожидается: вкладка «Справка» доступна и до подключения к пушке (из DevicesRoot), отображает общие статьи.

---

### Task 1: SystemConfig — парсер конфигурации

**Files:**
- `app/src/main/java/com/airmaxfield/airos/SystemConfig.kt` — парсер и модель

**Interfaces:**
```kotlin
data class CfgEntry(val key: String, val value: String)

class SystemConfig(val entries: List<CfgEntry>) {
    companion object {
        fun parse(raw: String): SystemConfig
    }
    operator fun get(key: String): String?
    fun section(prefix: String): List<CfgEntry>  // e.g. "radio.1" → all radio.1.* entries
    val raw: String  // round-trip: entries back to key=value text
    
    // Группировка по секциям UI
    val wireless: List<CfgEntry>   // radio.*, wireless.*
    val network: List<CfgEntry>    // netconf.*, bridge.*, vlan.*, route.*
    val advanced: List<CfgEntry>   // radio.1.ieee_mode, *.ack.*, *.distance, *.txpower, airmax.*
    val services: List<CfgEntry>   // snmp.*, sshd.*, httpd.*, ntpclient.*, syslog.*, discovery.*
    val system: List<CfgEntry>     // users.*, resolv.*, system.*, update.*
    val other: List<CfgEntry>      // всё, что не попало выше
}
```

**Steps:**
- [ ] Создать `SystemConfig.kt` с `parse()`: разбить строки по `\n`, пропустить пустые и комментарии (`#`), разделить по первому `=`
- [ ] Реализовать группировку по секциям (wireless/network/advanced/services/system/other) на основе префиксов ключей
- [ ] Реализовать `raw` — round-trip обратно в текст (сохраняя порядок)
- [ ] Добавить helper `isSensitive(key)` — true для ключей, содержащих `password`, `psk`, `secret`

**Tests:** `app/src/test/java/com/airmaxfield/airos/SystemConfigTest.kt`
- [ ] `parses_basic_config` — 5 строк, все поля присутствуют
- [ ] `skips_empty_and_comments` — пустые строки и `#` не попадают в entries
- [ ] `round_trip` — parse → raw → parse даёт тот же результат
- [ ] `sections_group_correctly` — radio.1.freq → wireless, netconf.1.ip → network, и т.д.
- [ ] `sensitive_keys_detected` — wireless.1.psk, users.1.password помечены sensitive
- [ ] `unknown_keys_go_to_other` — ключ `foo.bar=1` попадает в other

---

### Task 2: Справочные данные (help content)

**Files:**
- `app/src/main/java/com/airmaxfield/help/HelpContent.kt` — данные полей и статьи

**Interfaces:**
```kotlin
data class FieldHelp(
    val key: String,           // ключ или паттерн: "radio.1.freq", "wireless.*.psk"
    val title: String,         // "Частота"
    val description: String,   // подробное описание
    val recommendations: String? = null,
    val warnings: String? = null,
)

data class HelpArticle(
    val id: String,
    val title: String,
    val body: String,          // markdown-like text
    val tags: List<String>,
)

object HelpContent {
    val fields: List<FieldHelp>       // справка по полям настроек
    val articles: List<HelpArticle>   // общие статьи
    fun forKey(key: String): FieldHelp?
    fun search(query: String): List<Any>  // поиск по полям и статьям
}
```

**Steps:**
- [ ] Создать `HelpContent.kt` с полями для каждой секции:
  - Беспроводная: freq, ieee_mode (режим), chanbw (ширина канала), txpower, ssid, psk, hide_ssid, wds, security, country
  - Сеть: ip, netmask, gateway, dhcp, vlan, mtu, bridge, dns
  - Дополнительно: distance, ack_timeout, airmax_enabled, airmax_priority, signal_led, rts_threshold
  - Сервисы: snmp, sshd, httpd, ntpclient, syslog, discovery
  - Система: hostname, username, password, timezone, firmware, reset
- [ ] Написать общие статьи (5–7 шт):
  - Юстировка антенн
  - Уровни сигнала и SNR: что нормально
  - Выбор частоты и ширины канала
  - Режимы работы: AP PtP, AP PtMP, Station
  - Частые проблемы и их решения
  - Что такое airMAX Quality и Capacity
- [ ] Реализовать `forKey()` — точное совпадение, затем по паттерну (wildcard `*`)
- [ ] Реализовать `search()` — поиск подстроки в title, description, body (регистронезависимо)

**Tests:** `app/src/test/java/com/airmaxfield/help/HelpContentTest.kt`
- [ ] `field_lookup_exact` — forKey("radio.1.freq") возвращает справку
- [ ] `field_lookup_wildcard` — forKey("wireless.1.psk") матчит "wireless.*.psk"
- [ ] `search_finds_articles` — search("антенн") находит статью про юстировку
- [ ] `search_finds_fields` — search("частот") находит поле freq
- [ ] `all_sections_covered` — каждая секция имеет хотя бы 3 поля со справкой

---

### Task 3: SettingsScreen — экран настроек

**Files:**
- `app/src/main/java/com/airmaxfield/ui/settings/SettingsScreen.kt` — экран с под-вкладками
- `app/src/main/java/com/airmaxfield/ui/settings/SettingsController.kt` — загрузка конфига

**Interfaces:**
```kotlin
// Controller
class SettingsController(
    private val client: AirOsClient,
    private val scope: CoroutineScope,
) {
    val config: StateFlow<SystemConfig?>
    val loading: StateFlow<Boolean>
    val error: StateFlow<String?>
    fun load()
    fun retry()
}

// UI
@Composable
fun SettingsScreen(
    config: SystemConfig?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier,
)
```

**Steps:**
- [ ] Создать `SettingsController` — при `load()` вызывает `client.downloadConfig()`, парсит через `SystemConfig.parse()`, кладёт в StateFlow
- [ ] Создать `SettingsScreen` с фильтром-чипами вверху: Беспроводная | Сеть | Дополнительно | Сервисы | Система
- [ ] Для каждой секции — LazyColumn с карточками полей: `AirCard` с Label (ключ), значение (замаскировать если sensitive), кнопка «ⓘ»
- [ ] Кнопка «ⓘ» открывает `ModalBottomSheet` со справкой из `HelpContent.forKey()`
- [ ] Состояния: загрузка (CircularProgressIndicator), ошибка (текст + кнопка «Повторить»), пустая конфигурация
- [ ] Секция «Прочее» (свёрнутая) для ключей из `config.other`

---

### Task 4: HelpScreen — справочник

**Files:**
- `app/src/main/java/com/airmaxfield/ui/help/HelpScreen.kt`

**Steps:**
- [ ] Создать `HelpScreen` с полем поиска вверху
- [ ] Без поиска: список статей из `HelpContent.articles`, каждая в `AirCard` (заголовок, первая строка)
- [ ] Нажатие на статью — разворачивает содержимое (expandable card)
- [ ] Поиск фильтрует и статьи, и поля: результаты показываются в двух группах «Статьи» и «Поля настроек»
- [ ] Справочник доступен и без подключения к пушке

---

### Task 5: Интеграция

**Files:**
- `app/src/main/java/com/airmaxfield/ui/AppNav.kt` — замена placeholder'ов для tab 2 и tab 4
- `app/src/main/java/com/airmaxfield/ui/AppViewModel.kt` — SettingsController в Session
- `app/src/main/java/com/airmaxfield/airos/DemoClient.kt` — правдоподобная конфигурация

**Steps:**
- [ ] Расширить DemoClient.downloadConfig() — правдоподобная конфигурация ~50 ключей по всем секциям (radio, wireless, netconf, bridge, users, httpd, sshd, snmp, ntpclient, system)
- [ ] Добавить `SettingsController` в `Session` и `startSession()`; `close()` ничего не делает (нет polling)
- [ ] В AppNav tab 2: вместо placeholder показать `SettingsScreen`, загрузить конфиг при первом входе (`DisposableEffect`)
- [ ] В AppNav tab 4: вместо placeholder показать `HelpScreen`
- [ ] Справка (tab 4) должна быть доступна и из `DevicesRoot` — добавить кнопку «Справочник» в меню (ThemeMenu)
- [ ] Обновить `closeSession()` — остановить SettingsController если нужно
- [ ] Прогнать тесты, собрать release APK в `dist/airmax-pole-stage3.apk`, установить на телефон
