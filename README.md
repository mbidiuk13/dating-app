# Dating App

Веб-застосунок для знайомств, у якому анкета поділена на **Відкриту** та **Закриту**
інформацію. Закриті дані (контакти, особисті деталі, за бажанням — фото й вік) відкриваються
лише після взаємного підтвердження запрошення, і в цей момент система автоматично розраховує
сумісність партнерів за матрицею долі.

Навчальний проєкт з дисципліни «Життєвий цикл розробки програмного забезпечення»,
КПІ ім. Ігоря Сікорського, група ІП-44.

## Стек

| Шар (ЛР2) | Технологія |
| --- | --- |
| Security Layer | Spring Security 6.5, BCrypt, HTTPS (Tomcat, сертифікат PKCS12) |
| Presentation Layer | Spring MVC, Thymeleaf |
| Business Layer | Spring Boot 3.5, Java 21 |
| Persistence Layer | Spring Data JPA (Hibernate 6.6), PostgreSQL 18 |
| Моніторинг і журнали | Spring Boot Actuator, Logback, журнал доступу Tomcat |
| Тести | JUnit 5, Mockito, AssertJ, Spring MockMvc, H2 |

## Структура проєкту

Архітектура — модульний моноліт: код розбито на модулі за предметною областю,
усередині кожного модуля — сутності, репозиторії, сервіси та контролери.

```
dating-app/
├── docs/                              документація ЛР1, ЛР2
│   └── sql/                           скрипти тестових даних (10 000 анкет) та їх видалення
├── src/
│   ├── main/
│   │   ├── java/ua/kpi/datingapp/
│   │   │   ├── DatingAppApplication.java    точка входу
│   │   │   ├── config/                      безпека, HTTPS, обробка помилок
│   │   │   ├── audit/                       журнал дій доступу (logs/audit.log)
│   │   │   ├── user/                        User, реєстрація, вхід, видалення облікового запису
│   │   │   ├── profile/                     Profile, Keyword, відкриті/закриті DTO, пошук
│   │   │   ├── invitation/                  Invitation, InvitationStatus, обмін запрошеннями
│   │   │   ├── compatibility/               DestinyMatrixService, DestinyMatrixResult
│   │   │   └── web/                         головна сторінка
│   │   └── resources/
│   │       ├── templates/                   Thymeleaf-сторінки
│   │       ├── static/css/                  стилі
│   │       ├── application.properties       налаштування
│   │       └── logback-spring.xml           налаштування журналів
│   └── test/
│       ├── java/ua/kpi/datingapp/
│       │   ├── compatibility/, invitation/, profile/   модульні тести
│       │   ├── integration/                 інтеграційні тести всіх шарів разом
│       │   ├── nfr/                         перевірки нефункціональних вимог
│       │   └── performance/                 навантажувальний тест LoadTestRunner
│       └── resources/application-test.properties   БД H2 для тестів
├── pom.xml                            залежності Maven
└── .gitignore
```

### Відповідність діаграмі класів ЛР2

| Клас | Модуль | Призначення |
| --- | --- | --- |
| `User` | user | обліковий запис: email, BCrypt-хеш пароля, стан активності |
| `Profile` | profile | анкета: ім'я, дата народження; відкриті поля `open*` і закриті `closed*` фізично розділені; налаштування видимості фото та віку |
| `ProfilePublicDTO` / `ProfilePrivateDTO` | profile | відкрита та закрита частини анкети окремо |
| `Keyword` | profile | довідник інтересів, зв'язок з анкетою «багато-до-багатьох» |
| `Invitation`, `InvitationStatus` | invitation | запрошення та його стани PENDING → ACCEPTED / REJECTED / CANCELLED |
| `InvitationService` | invitation | сценарій UC-1: надсилання, підтвердження, розкриття даних |
| `DestinyMatrixService` | compatibility | інтерфейс розрахунку сумісності |
| `ArcanaDestinyMatrixService` | compatibility | реалізація розрахунку (спрощена для MVP) |
| `DestinyMatrixResult` | compatibility | результат: відсоток та опис |

### Принципи SOLID у коді

- **S** — кожен клас має одну відповідальність: `UserService` керує обліковими записами,
  `ProfileService` — анкетами та пошуком, `InvitationService` — запрошеннями, `AuditLog` — журналом доступу.
- **O** — новий алгоритм сумісності додається новим класом, що реалізує `DestinyMatrixService`,
  без змін у `InvitationService`.
- **L** — будь-яку реалізацію `DestinyMatrixService` можна підставити в `InvitationService`
  без зміни його поведінки (так і зроблено в тесті атомарності).
- **I** — репозиторії та сервіси мають вузькі інтерфейси під конкретний модуль.
- **D** — сервіси залежать від абстракцій (`DestinyMatrixService`, `PasswordEncoder`,
  репозиторії Spring Data), конкретні реалізації підставляє Spring.

## Виконання функціональних вимог (ЛР1)

| Вимога | Пріоритет | Де реалізовано | Чим перевірено |
| --- | --- | --- | --- |
| US-01 Реєстрація та вхід (email, пароль, ім'я, дата народження) | високий | `AuthController`, `UserService`, `SecurityConfig` | `UseCaseIntegrationTest`: реєстрація, вхід, невірний пароль, гість → `/login` |
| Вихід із системи | середній | `SecurityConfig` (logout), кнопка «Вийти» | `logoutEndsSession` |
| US-02 Розподіл анкети на Відкриту/Закриту інформацію | високий | `Profile`, `ProfilePublicDTO`, `ProfilePrivateDTO`, `ProfileService.viewProfile` | `mainScenario…`: закритих даних немає в HTML до підтвердження |
| Самостійне налаштування відкритих/закритих полів (фото, вік) | середній | `Profile.updateVisibility`, форма анкети | `hiddenPhotoAndAgeAreSentOnlyAfterAcceptance` |
| Створення й редагування анкети | високий | `ProfileController`, `ProfileService.saveOwnProfile` | інтеграційні тести (кожен тест заповнює анкету через форму) |
| Видалення анкети | низький | `UserService.deleteAccount`, `POST /profile/delete` | `deletedProfileDisappearsFromSearchButKeywordsStay` |
| US-03 Пошук за ключовими словами | високий | `SearchController`, `ProfileService.search`, `ProfileRepository.findMatches` | `mainScenario…`, `searchSortsByCommonInterestsAndFiltersByAge` |
| Сортування/фільтрація результатів пошуку | низький | `SearchSort`, `SearchCriteria` | `ProfileSearchSortTest`, `searchSorts…` |
| US-04 Надсилання та підтвердження запрошень (UC-1) | високий | `InvitationService`, `InvitationController` | `mainScenarioOpensPrivateDataForBothAfterAcceptance` |
| Перегляд списку надісланих/отриманих запрошень | середній | сторінка `/invitations` | `mainScenario…` (запрошення в списку зі станом PENDING) |
| Заборона повторного запрошення (UC-1, 1a) | середній | `InvitationService.sendInvitation` | `secondInvitationIsRefusedWhileFirstIsActive`, `InvitationServiceTest` |
| Відхилення запрошення (UC-1, 3a) | — | `InvitationService.rejectInvitation` | `rejectedInvitationKeepsPrivateData…` |
| Відкликання запрошення (UC-1, 3b) | низький | `InvitationService.cancelInvitation` | `senderCanCancelPendingInvitation` |
| US-05 Сумісність за матрицею долі після підтвердження | низький | `ArcanaDestinyMatrixService` | `ArcanaDestinyMatrixServiceTest`, `mainScenario…` |

Обмеження доступу: гість бачить лише вхід і реєстрацію; редагувати можна тільки власну анкету
(адреса `/profile/edit` завжди відкриває анкету поточного користувача).

## Виконання нефункціональних вимог (ЛР1)

| Категорія | Вимога | Як забезпечено | Як перевірено |
| --- | --- | --- | --- |
| Продуктивність | сторінки анкети й пошуку до 2 с при 10 000 анкет | індекси (`birth_date`, `profile_keywords.keyword_id`, запрошення); пошук вибирає лише короткі рядки й показує до 50 анкет; пакетне завантаження тегів (`default_batch_fetch_size`) | `docs/sql/seed-10000-profiles.sql` + `LoadTestRunner`: 95-й перцентиль ≤ 2 с |
| Продуктивність | щонайменше 100 одночасних сеансів без помилок, відповідь до 2 с | вбудований Tomcat (200 потоків), пул з'єднань HikariCP | `LoadTestRunner`: 100 сеансів одночасно, 0 помилок, 95-й перцентиль ≤ 2 с |
| Продуктивність | матриця долі до 1 с для пари | простий розрахунок без звернень до БД | `NonFunctionalRequirementsTest`: одна пара та 100 000 пар менш ніж за 1 с |
| Надійність | стан системи перевіряється службовим ендпоінтом моніторингу | `/actuator/health` повертає `UP`, коли застосунок і БД працюють, і `DOWN`, коли БД недоступна | `healthEndpointReportsSystemIsUp`, ручна перевірка https://localhost:8443/actuator/health |
| Надійність | атомарні операції в одній транзакції | `@Transactional` у сервісах (реєстрація, підтвердження, видалення) | `TransactionAtomicityIntegrationTest`: збій збереження результату відкочує зміну статусу |
| Надійність | помилки українською без stack trace | `GlobalExceptionHandler`, `error.html`, `server.error.include-stacktrace=never` | `missingProfileShows…`, `malformedAddressShows…` |
| Безпека | хешування паролів BCrypt | `BCryptPasswordEncoder` | `passwordIsStoredOnlyAsBCryptHash`, `registeredUserLogsIn…` |
| Безпека | закрита інформація не передається на клієнт без взаємної згоди | `ProfileView` отримує `privateInfo = null`; приховані фото й вік не потрапляють у `ProfilePublicDTO` | `publicPartOfProfileNeverContainsClosedData`, інтеграційні тести перевіряють HTML |
| Безпека | журналювання дій доступу | `logs/audit.log` (входи, перегляди анкет, видалення), `logs/access.*.log` (усі HTTP-запити) | перегляд файлів журналу після запуску |
| Безпека | захист з'єднання HTTPS | порт 8443 по HTTPS, порт 8080 перенаправляє на HTTPS; cookie сесії `Secure` і `HttpOnly` | відкрити http://localhost:8080 — браузер переходить на https://localhost:8443 |
| Зручність | основні дії не більше ніж за 3 переходи | пошук: «Пошук» → «Шукати» (2); запрошення: «Пошук» → «Шукати» → «Надіслати запрошення» прямо в результатах (3); підтвердження: «Запрошення» → «Підтвердити» (2) | ручна перевірка |
| Зручність | сумісність відсотком і коротким текстом | `ArcanaDestinyMatrixService.describe` | `compatibilityIsShownAsPercentAndShortPlainText` |

## Інтеграція компонентів

Метод інтеграції — **локальний**: застосунок і PostgreSQL працюють на комп'ютері розробника.
Модулі взаємодіють у межах одного процесу через виклики сервісів (модульний моноліт, ЛР2):

```
Браузер ──HTTPS──▶ Security Layer (Spring Security: вхід, сесії, CSRF)
                    └─▶ Контролери (Auth, Profile, Search, Invitation)
                          └─▶ Сервіси (UserService, ProfileService, InvitationService, DestinyMatrixService)
                                ├─▶ Репозиторії Spring Data JPA ──▶ PostgreSQL
                                └─▶ AuditLog ──▶ logs/audit.log
```

Точки інтеграції та їх перевірка:

| Точка інтеграції | Перевірка |
| --- | --- |
| Security ↔ контролери (доступ лише після входу, CSRF) | `guestIsRedirectedToLoginPage`, усі POST-запити тестів ідуть з CSRF-токеном |
| Security ↔ user ↔ БД (вхід за BCrypt-хешем із бази) | `registeredUserLogsInAndPasswordIsStoredAsBCryptHash` |
| profile ↔ invitation (право на закриті дані) | `mainScenario…`, `rejectedInvitation…`, `hiddenPhotoAndAge…` |
| invitation ↔ compatibility (розрахунок під час підтвердження) | `mainScenario…`, `TransactionAtomicityIntegrationTest` |
| user ↔ profile ↔ invitation (видалення облікового запису) | `deletedProfileDisappearsFromSearchButKeywordsStay` |

Інтеграційні тести (`src/test/java/.../integration`) піднімають увесь застосунок і працюють
з вбудованою БД H2 у режимі PostgreSQL, тому не потребують встановленого PostgreSQL.

## Управління залежностями

- Усі залежності описано в `pom.xml`. Їхні версії задає батьківський `spring-boot-starter-parent 3.5.0`,
  тому Spring, Hibernate, драйвер PostgreSQL, H2, JUnit і Mockito гарантовано сумісні між собою.
  Власних номерів версій у залежностях немає навмисно.
- Тестові бібліотеки (`spring-boot-starter-test`, `spring-security-test`, `h2`) підключено зі `scope test`:
  у робочий застосунок вони не потрапляють.
- `maven-enforcer-plugin` перевіряє, що збірка йде на Java 21+ і Maven 3.6.3+, тож середовище
  в обох учасниць однакове.
- Дерево залежностей: IntelliJ → праворуч вкладка **Maven** → **Dependencies**.

## Запуск

### Потрібно встановити

- JDK 21
- PostgreSQL 18 (разом з pgAdmin)
- IntelliJ IDEA (Maven вбудований)

### Кроки

1. У pgAdmin створіть базу даних `dating_app`.
2. Скопіюйте `src/main/resources/application-local.properties.example`
   у `src/main/resources/application-local.properties` і вкажіть пароль користувача `postgres`.
   Цей файл у Git не потрапляє.
3. Створіть сертифікат для HTTPS. У терміналі IntelliJ (у папці проєкту) виконайте:
   ```
   keytool -genkeypair -alias dating-app -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore src/main/resources/keystore.p12 -validity 365 -storepass changeit -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
   ```
   Якщо команду `keytool` не знайдено, вкажіть повний шлях до неї в папці `bin` вашого JDK.
   Сертифікат у Git не потрапляє: у кожної учасниці свій.
4. Відкрийте папку проєкту в IntelliJ IDEA і дочекайтеся завантаження залежностей Maven.
5. Запустіть `DatingAppApplication` (зелена кнопка ▶ біля методу `main`).
6. Відкрийте https://localhost:8443 і зареєструйтеся. Браузер попередить про самопідписаний
   сертифікат: **Додатково → Перейти на localhost**.

Таблиці та індекси в базі створюються автоматично під час першого запуску.
Щоб перевірити обмін запрошеннями, зареєструйте двох користувачів
(наприклад, у звичайному вікні браузера та в режимі інкогніто).

Якщо сертифікат ще не створено, застосунок можна тимчасово запустити по HTTP:
Run → Edit Configurations → Environment variables → `SSL_ENABLED=false`, адреса http://localhost:8443
(для `LoadTestRunner` тоді додайте у VM options `-DbaseUrl=http://localhost:8443`).

### Тести

У IntelliJ: правою кнопкою на папці `src/test/java` → **Run 'All Tests'**.
Тести не потребують PostgreSQL: модульні працюють з моками, інтеграційні — з вбудованою БД H2.

### Навантажувальне тестування

1. У pgAdmin відкрийте Query Tool для бази `dating_app` і виконайте `docs/sql/seed-10000-profiles.sql`
   (10 000 анкет, користувачі `load1@test.ua` … `load10000@test.ua`, пароль `password123`).
2. Запустіть застосунок.
3. Запустіть `src/test/java/ua/kpi/datingapp/performance/LoadTestRunner` (зелена кнопка ▶ біля `main`).
   У консолі з'явиться час відповіді для 1 користувача та для 100 одночасних сеансів і висновок
   «ВИКОНАНО / НЕ ВИКОНАНО» для кожної вимоги.
4. Видалити тестові дані: `docs/sql/delete-test-profiles.sql`.

### Журнали

| Файл | Що записується |
| --- | --- |
| `logs/audit.log` | входи (успішні й невдалі), перегляди чужих анкет з результатом доступу, видалення облікових записів |
| `logs/access.РРРР-ММ-ДД.log` | кожен HTTP-запит: час, IP, адреса, код відповіді, тривалість у мс |
| `logs/dating-app.log` | робочий журнал застосунку, технічні подробиці помилок |

Папка `logs/` у Git не потрапляє.

## Команда та внесок

| Учасниця | Роль | Внесок |
| --- | --- | --- |
| Бідюк Марія | Backend, архітектура, БД, безпека | каркас проєкту; Spring Security, BCrypt, HTTPS; сутності `User`, `Profile`, `Invitation`, `DestinyMatrixResult`; сервіси анкет, запрошень і матриці долі; правила розкриття закритої інформації; ЛР5: індекси та оптимізований пошук із сортуванням і фільтром, налаштування видимості полів, видалення облікового запису, журнал аудиту, безпечна обробка помилок, SQL-скрипти тестових даних, оновлення `pom.xml` |
| Хмелюк Діана | Frontend, пошук, тестування, документація | сутність `Keyword` і розбір тегів; контролери та Thymeleaf-сторінки; стилі; модульні тести; ЛР5: фільтри пошуку й кнопка запрошення в результатах, перемикачі видимості та видалення анкети на сторінках, інтеграційні тести, тести НФВ, навантажувальний тест, README |

## Процес роботи з Git

- `main` — стабільна гілка, прямі коміти в неї не робляться.
- Кожна задача — окрема гілка `feature/...`.
- Зміни потрапляють у `main` лише через Pull Request після код-рев'ю іншої учасниці.

| Гілка | Лабораторна | Автор | Рев'ю |
| --- | --- | --- | --- |
| `feature/keyword-entity` | ЛР4 | Хмелюк Діана | Бідюк Марія |
| `feature/backend-core` | ЛР4 | Бідюк Марія | Хмелюк Діана |
| `feature/web-ui` | ЛР4 | Хмелюк Діана | Бідюк Марія |
| `feature/nfr-backend` | ЛР5 | Бідюк Марія | Хмелюк Діана |
| `feature/ui-tests-docs` | ЛР5 | Хмелюк Діана | Бідюк Марія |

## Документація

- [docs/ЛР1-специфікація-вимог.pdf](docs/ЛР1-специфікація-вимог.pdf) — вимоги, користувацькі історії, UC-1
- [docs/ЛР2-архітектура.pdf](docs/ЛР2-архітектура.pdf) — архітектура та UML-діаграми
- [docs/sql/](docs/sql/) — тестові дані для перевірки продуктивності
- Беклог і спринти: [дошка Trello](https://trello.com/b/3sk4AyKO)
