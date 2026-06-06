# Crypto Arbitrage Simulator (Exchange Monitor)

Пет-проект, представляющий собой симулятор межбиржевого криптовалютного арбитража. Система отслеживает расхождения в курсах валютных пар в реальном времени, анализирует потенциальную прибыль с учетом комиссий и симулирует одновременное исполнение ордеров на покупку и продажу (двухплечевые сделки).

Основная цель проекта — построение расширяемой, отказоустойчивой и событийно-ориентированной архитектуры для мониторинга высоконагруженных потоков данных на базе **Kotlin Coroutines** и **Ktor**.

---

## Технологический стек

* **Kotlin (JVM Target 21)** — основной язык разработки с использованием преимуществ статической типизации и современных возможностей платформы JVM.
* **Kotlin Coroutines & Flow** — неблокирующая реактивная обработка рыночных котировок и управление пулом параллельно выполняющихся воркеров.
* **Ktor (Server & Client)** — легковесный асинхронный фреймворк для реализации REST API, веб-сокет клиентов (Binance, Bybit) и отображения интерактивного Swagger UI.
* **ktoml** — сериализация и сохранение текущего состояния системы (списков бирж, кошельков и запущенных анализаторов) в файл `config.toml`.
* **Logback & SLF4J** — гибкое логирование работы с разделением вывода в цветную консоль и ведением архива сделок в файле `logs/trades.log`.
* **kotlin-test & Coroutine Testing** — модульное тестирование бизнес-логики и асинхронного поведения компонентов.

---

## Архитектурные решения и фокус на технологиях

Этот проект спроектирован с упором на лучшие практики бэкенд-разработки на Kotlin, принципы SOLID и высокую производительность:

### 1. Инкапсуляция инфраструктуры и инверсия зависимостей (DIP / ISP)
Бизнес-логика поиска арбитражных окон полностью изолирована от инфраструктурного слоя.
* Доменные интерфейсы [Exchange](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/Exchange.kt) и [Wallet](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/wallet/Wallet.kt) описывают абстрактное поведение торговой площадки и кошелька.
* Благодаря этому ядро системы ([ArbitrageAnalyzer](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/analyzer/Analyzer.kt) и [TradeExecutionManager](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/trading/TradeExecutionManager.kt)) работает исключительно с абстракциями. Это позволяет бесшовно сочетать симуляционный контур ([VirtualExchange](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/simulation/VirtualExchange.kt), [VirtualWallet](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/simulation/VirtualWallet.kt)) и интеграции с реальными биржами по API ([Binance](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/clients/Binance.kt), [Bybit](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/clients/Bybit.kt)).

### 2. Реактивный конвейер данных (Kotlin Coroutines & Flows)
Рыночные апдейты представляют собой непрерывный и высокоинтенсивный поток событий.
* Вместо блокирующих вызовов или громоздких коллбэков, обработка событий [TradeEvent](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/analyzer/TradeEvent.kt) реализована на базе корутин и асинхронных потоков `SharedFlow`/`Channel`.
* Класс [AbstractExchangeClient](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/clients/AbstractExchangeClient.kt) обеспечивает автоматическое управление WebSocket-соединениями, включая периодические пинги (20 секунд) для контроля зависания соединения и авто-восстановление подписок на каналы данных при переподключениях.

### 3. Декларативное управление конфигурацией (TOML Persistence)
* Конфигурация системы (список бирж, кошельков с их балансами и активных анализаторов) сохраняется в читаемый файл `config.toml` с помощью библиотеки `ktoml-core`.
* Управление жизненным циклом и загрузка ресурсов вынесены в [ConfigManager](file:///home/egor/Projects/exchange-monitor/src/main/kotlin/ru/jinushi/exchange/config/ConfigManager.kt). Для простоты pet-проекта инициализация компонентов выполняется напрямую через явные вызовы конструкторов (без усложнения фабриками или рефлексией).
* Изменения конфигурации (например, регистрация кошелька или запуск нового анализатора через API) автоматически сохраняются на диск.

### 4. Точность расчетов и надежное логирование
* Балансы кошельков и расчеты прибыли защищены от деградации точности чисел с плавающей точкой (IEEE 754) с помощью `BigDecimal`. Установлены строгие правила округления (2 знака для фиата, 8 знаков для криптовалюты).
* Настроено гибкое логирование через Logback: консольный цветной вывод для отладки процессов и выделенный ротируемый файл `logs/trades.log` для записи результатов всех совершенных арбитражных сделок.

---

## Структура проекта

```text
src/main/kotlin/ru/jinushi/exchange/
├── Application.kt                  # Точка входа в приложение, инициализация Ktor (роуты, плагины, жизненный цикл)
├── Exchange.kt                     # Базовый интерфейс биржи
│
├── analyzer/
│   ├── Analyzer.kt                 # Анализатор спреда (ArbitrageAnalyzer)
│   └── TradeEvent.kt               # События рынка (тикеры, найденные окна)
│
├── clients/
│   ├── AbstractExchangeClient.kt   # Базовый клиент с WebSocket auto-ping & reconnect
│   ├── Binance.kt                  # WebSocket-интеграция с API Binance
│   └── Bybit.kt                    # WebSocket-интеграция с API Bybit
│
├── simulation/
│   ├── VirtualExchange.kt          # Симулятор биржевого стакана
│   └── VirtualWallet.kt            # Виртуальный кошелек с точным округлением балансов
│
├── trading/
│   ├── TradeOrder.kt               # DTO ордеров (сторона, тип, статус)
│   └── TradeExecutionManager.kt    # Пул воркеров для асинхронного исполнения сделок
│
├── accounting/
│   └── ProfitTracker.kt            # Атомарный учет накопленной прибыли
│
├── registry/
│   └── Registries.kt               # Реестры бирж, кошельков и анализаторов (Closeable)
│
├── config/
│   └── ConfigManager.kt            # Менеджер сохранения/загрузки состояния в TOML
│
├── routes/
│   ├── ExchangeRoutes.kt           # API работы со списком бирж
│   ├── WalletRoutes.kt             # API управления кошельками (балансы, регистрация)
│   ├── AnalyzerRoutes.kt           # API управления анализаторами (запуск, остановка, профит)
│   └── TradeRoutes.kt              # API истории сделок (в разработке)
│
└── serializers/
    └── BigDecimalSerializer.kt     # Сериализатор BigDecimal для JSON/Ktor
```

---

## API-эндпоинты и документация

В проект встроена спецификация OpenAPI. Интерактивная документация Swagger UI доступна при запущенном приложении по адресу:
👉 `http://localhost:8080/swagger`

Основные возможности API:
* `GET /exchanges` — получить список доступных бирж.
* `GET /wallets` — просмотр балансов всех кошельков.
* `POST /wallets` — создание/пре-фандинг нового виртуального кошелька.
* `GET /analyzers` — список активных пар, по которым запущен анализ котировок.
* `POST /analyzers` — запуск анализатора для конкретной валютной пары (с маппингом бирж на кошельки).
* `DELETE /analyzers/{pair}` — остановка анализатора и закрытие соответствующих стримов.
* `GET /analyzers/profit` — просмотр накопленной прибыли по результатам симуляций.

---

## Требования и запуск

* **Стек:** Kotlin (JVM Toolchain 21), Gradle.
* **Сборка проекта:**
  ```shell
  ./gradlew build
  ```
* **Запуск тестов:**
  ```shell
  ./gradlew test
  ```
* **Запуск приложения локально:**
  ```shell
  ./gradlew run
  ```
