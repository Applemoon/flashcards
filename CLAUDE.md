# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Контекст репозитория

Веб-приложение для зубрёжки сербской лексики. Технологии: Spring Boot 3.2 + Thymeleaf, Kotlin 1.9 на JDK 21, Gradle (Kotlin DSL). 

Если путь содержит эмодзи (`🚀`) и кириллицу, это ломает `gradle-wrapper.jar` (JNI percent-decoding падает). Для запуска `./gradlew` нужно копировать проект в путь без не-ASCII символов (например, `/tmp/flashcards-test`) либо использовать системный `gradle`. При работе вне vault'а возвращать собранные результаты сюда не обязательно — исходник нужно править здесь.

## Команды

Сборка и запуск идут через Gradle Wrapper:

```bash
./gradlew bootRun          # запустить приложение (откроет http://localhost:8080 в браузере)
./gradlew build            # сборка с тестами
./gradlew test             # только тесты
./gradlew test --tests "ru.uvarov.flashcards.service.QuizServiceTest"   # одиночный класс
```

Toolchain в `build.gradle.kts` запрашивает JDK 21 — Gradle ищет его в системе. Если локально нет — нужно установить (например `brew install openjdk@21`) либо добавить `org.gradle.toolchains.foojay-resolver-convention` в `settings.gradle.kts` для авто-скачивания.

`main`-функция в `FlashcardsApplication.kt` после старта Spring'а вызывает `open http://localhost:8080` — приложение само поднимает страницу в браузере; это поведение macOS-специфичное (`ProcessBuilder("open", ...)`).

## Архитектура

Однопроцессное Spring-приложение, состояние держится в памяти, источник истины — текстовый файл словаря.

**Поток данных:**

1. `FileService` при `@PostConstruct` читает `src/main/resources/questions.txt` через classpath (`getResourceAsStream`), парсит в `Map<String, String> wordPairs` (rus → srb) и хранит весь список строк в `fileContent`. Файл загружается **один раз** на старте — изменения в `questions.txt` без перезапуска приложения не подхватятся.
2. `QuizService` берёт `wordPairs` из `FileService` и строит вопросы.
3. `QuizController` рендерит Thymeleaf-шаблоны (`quiz.html`, `words.html`, `type.html`).

**Endpoints:**

- `GET /` → `static/index.html` — меню режимов.
- `GET /quiz` — multiple-choice (4 варианта): по русскому слову выбрать сербский перевод.
- `GET /words` — редактируемый список словаря: пары рендерятся как два `<input>` (ru, srb) с ×-кнопкой; `#`-заголовки и пустые строки сохраняются как визуальные разделители. Счётчик `wordCount` сверху = `wordPairs.size`. Изменения уходят через `PUT /word` на `change`-эвент инпута, удаление — через `DELETE /word`.
- `GET /type-srb-ru`, `GET /type-ru-srb` — режим ввода с клавиатуры, направление меняется.
- `DELETE /word?word=...` — удаляет пару по русскому ключу. Дёргают `quiz.html` (кнопка «Знаю — удалить») и `words.html` (×). `FileService.deleteWord` убирает пару из in-memory и перезаписывает `questions.txt` через `quiz.word-write-filename`.
- `PUT /word?oldRu=...&newRu=...&newSrb=...` — обновляет пару. `FileService.updateWord` валидирует blank/«=»/«#»/коллизию ключей, заменяет совпадающую строку в `fileContent` на `newRu=newSrb` (нормализует пробелы вокруг `=`), перезаписывает файл.
- `POST /word?newRu=...&newSrb=...` — добавляет пару. `FileService.addWord` валидирует через тот же `validateNewPair` + проверку дубликата, аппендит `newRu=newSrb` в конец `fileContent`, перезаписывает файл. Фронтенд (`words.html`, ghost-row сверху списка) на успех динамически вставляет ряд в конец `.words-list` и инкрементит счётчик, без перезагрузки.
- `POST /answer?word=<ru>&correct=<bool>` — фиксирует ответ на квиз/type. `FileService.recordAnswer` меняет вес слова на ±1 и перезаписывает строку в `questions.txt`. Дёргают `quiz.html` (на каждый клик: correct=true/false) и `type.html` (только на правильный ввод по Enter).

**Формат словаря (`src/main/resources/questions.txt`):**

- Строки `русское=сербское` ИЛИ `русское=сербское=вес` (вес — целое, может быть отрицательным).
- Регистр и диакритику сохранять как есть. Сербская сторона может содержать варианты через запятую: `неделя=седмица, недеља`.
- Строки, начинающиеся с `#` — это категории-разделители (`#Дни недели`); пропускаются при парсинге, но видны в `/words`.
- Пустые строки — допустимы (пропускаются).
- Строка без `=` (кроме `#` и пустых), с >2 `=`-разделителями, или с не-числовым весом — роняет приложение на старте через `IllegalArgumentException`.

**Spaced repetition (`FileService.recordAnswer` + `QuizService.pickWeighted`):**

Вес = «насколько хорошо ты знаешь это слово». Правильный ответ → +1, неправильный → -1. По умолчанию вес = 0 (новое слово). При весе 0 строка пишется без суффикса (`ru=srb`), при ненулевом — с (`ru=srb=N`). Выбор слова — взвешенный рандом по `0.7^вес`: новые/трудные слова появляются чаще, выученные — реже (вес 5 → ~17% от равномерного, вес 10 → ~3%, вес -5 → ~6×). `updateWord` сохраняет вес при переименовании ключа.

**Генерация multiple-choice (`QuizService.getQuestion`):**

Размер списка ответов — `quiz.answer-size` из `application.yaml` (по умолчанию 4). Один правильный + дистракторы. Дистракторы сначала ищутся среди слов, у которых сербский перевод **начинается на ту же букву**, что и правильный ответ (`findAndFillStartingSameLetter`) — это удерживает квиз от «угадай длинное слово против коротких». Если таких не набралось — добивает случайными из общего списка (`fillIfNotEnough`). `Answer.equals/hashCode` сравнивают по паре (word, translate), а не по `correct` — это важно для дедупликации в `fillIfNotEnough`.

**Запись словаря (`FileService.persist`):**

`quiz.word-write-filename` в `application.yaml` указывает на `./src/main/resources/questions.txt` — относительный путь от cwd при запуске. Это значит: при `./gradlew bootRun` из корня репо изменения пишут **прямо в исходники**. Из jar в произвольной cwd работать не будет — придётся переопределять путь. Все мутации помечены `@Synchronized`. Запись атомарная: сначала во временный `<name>.tmp`, потом `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` — крэш мид-write не попортит словарь.

**Обработка ошибок:**

`GlobalExceptionHandler` (`@ControllerAdvice`) маппит `IllegalArgumentException` (от `require()` в `FileService`) на `400 Bad Request` с телом-сообщением. Фронтенд читает `response.text()` и показывает в alert. `IllegalStateException` (от `check()`) остаются 500 — это «не должно случаться».

## Соглашения по коду

- Kotlin `data class` для DTO (`WordPair`, `Answer`, `Question`). `Pair` намеренно переименован в `WordPair`, чтобы не конфликтовать с `kotlin.Pair`.
- Lombok не используется — стандартные средства Kotlin (data class, primary constructor) закрывают потребность.
- Логгер — через `org.slf4j.LoggerFactory.getLogger(...)` в companion object или top-level `private val`.
- Spring требует `kotlin("plugin.spring")` (открывает `@Component`/`@Controller`/`@Service` для proxying) и `kotlin-reflect` — оба уже подключены.
- Конфигурация — через `@Value("\${quiz.*}")` в primary constructor, не через `@ConfigurationProperties`. Знак `$` в строке Kotlin экранируется как `\$`.
- Тесты — JUnit 5 + mockito-kotlin (`mock<T>()`, `whenever()`). Имена методов в backtick'ах по схеме `methodName - condition - expectedResult` (см. конвенцию `testing.md` владельца). Юнит-тесты `FileServiceTest`, `QuizServiceTest` гоняют рандомизированные тесты по `RUNS = 100` итераций — не уменьшать без причины, это страховка от флака.
- Тестовый словарь живёт в `src/test/resources/test-words.txt`, отдельно от боевого `questions.txt`.
