# Dating App

Веб-застосунок для знайомств, у якому анкета поділена на **Відкриту** та **Закриту**
інформацію. Закриті дані (контакти, особисті деталі) відкриваються лише після
взаємного підтвердження запрошення, і в цей момент система автоматично розраховує
сумісність партнерів за матрицею долі.

Навчальний проєкт з дисципліни «Життєвий цикл розробки програмного забезпечення»,
КПІ ім. Ігоря Сікорського, група ІП-44.

## Стек

| Шар (ЛР2) | Технологія |
| --- | --- |
| Security Layer | Spring Security, BCrypt |
| Presentation Layer | Spring MVC, Thymeleaf |
| Business Layer | Spring Boot 3, Java 21 |
| Persistence Layer | Spring Data JPA (Hibernate), PostgreSQL |
| Тести | JUnit 5, Mockito, AssertJ |

## Структура проєкту

Архітектура — модульний моноліт: код розбито на модулі за предметною областю,
усередині кожного модуля — сутності, репозиторії, сервіси та контролери.

```
dating-app/
├── docs/                         документація: ЛР1 (SRS), ЛР2 (архітектура)
├── src/
│   ├── main/
│   │   ├── java/ua/kpi/datingapp/
│   │   │   ├── DatingAppApplication.java   точка входу
│   │   │   ├── config/                     SecurityConfig: доступ до сторінок, BCrypt
│   │   │   ├── user/                       User, реєстрація та вхід
│   │   │   ├── profile/                    Profile, Keyword, відкриті/закриті DTO, пошук
│   │   │   ├── invitation/                 Invitation, InvitationStatus, обмін запрошеннями
│   │   │   ├── compatibility/              DestinyMatrixService, DestinyMatrixResult
│   │   │   └── web/                        головна сторінка
│   │   └── resources/
│   │       ├── templates/                  Thymeleaf-сторінки
│   │       ├── static/css/                 стилі
│   │       └── application.properties      налаштування
│   └── test/java/ua/kpi/datingapp/         модульні тести
├── pom.xml                       залежності Maven
└── .gitignore
```

### Відповідність діаграмі класів ЛР2

| Клас | Модуль | Призначення |
| --- | --- | --- |
| `User` | user | обліковий запис: email, BCrypt-хеш пароля, стан активності |
| `Profile` | profile | анкета: ім'я та дата народження; відкриті поля `open*` і закриті `closed*` фізично розділені; створюється під час реєстрації |
| `ProfilePublicDTO` / `ProfilePrivateDTO` | profile | відкрита та закрита частини анкети окремо |
| `Keyword` | profile | довідник інтересів, зв'язок з анкетою «багато-до-багатьох» |
| `Invitation`, `InvitationStatus` | invitation | запрошення та його стани PENDING → ACCEPTED / REJECTED / CANCELLED |
| `InvitationService` | invitation | сценарій UC-1: надсилання, підтвердження, розкриття даних |
| `DestinyMatrixService` | compatibility | інтерфейс розрахунку сумісності |
| `ArcanaDestinyMatrixService` | compatibility | реалізація розрахунку (спрощена для MVP) |
| `DestinyMatrixResult` | compatibility | результат: відсоток та опис |

### Принципи SOLID у коді

- **S** — кожен клас має одну відповідальність: `UserService` лише реєструє користувачів,
  `ProfileService` керує анкетами, `InvitationService` — запрошеннями.
- **O** — новий алгоритм сумісності додається новим класом, що реалізує `DestinyMatrixService`,
  без змін у `InvitationService`.
- **L** — будь-яку реалізацію `DestinyMatrixService` можна підставити в `InvitationService`
  без зміни його поведінки.
- **I** — репозиторії та сервіси мають вузькі інтерфейси під конкретний модуль.
- **D** — сервіси залежать від абстракцій (`DestinyMatrixService`, `PasswordEncoder`,
  репозиторії Spring Data), конкретні реалізації підставляє Spring.

## Запуск

### Потрібно встановити

- JDK 21
- PostgreSQL 17 (разом з pgAdmin)
- IntelliJ IDEA (Maven вбудований)

### Кроки

1. У pgAdmin створіть базу даних `dating_app`.
2. Скопіюйте `src/main/resources/application-local.properties.example`
   у `src/main/resources/application-local.properties` і вкажіть пароль користувача `postgres`.
   Цей файл у Git не потрапляє.
3. Відкрийте папку проєкту в IntelliJ IDEA і дочекайтеся завантаження залежностей Maven.
4. Запустіть `DatingAppApplication` (зелена кнопка ▶ біля методу `main`).
5. Відкрийте http://localhost:8080 і зареєструйтеся.

Таблиці в базі створюються автоматично під час першого запуску.

Щоб перевірити обмін запрошеннями, зареєструйте двох користувачів
(наприклад, у звичайному вікні браузера та в режимі інкогніто).

### Тести

У IntelliJ: правою кнопкою на папці `src/test/java` → **Run 'All Tests'**.
Тести не потребують бази даних.

## Команда та внесок

| Учасниця | Роль | Внесок |
| --- | --- | --- |
| Бідюк Марія | Backend, архітектура, БД, безпека | каркас проєкту; Spring Security та BCrypt; сутності `User`, `Profile`, `Invitation`, `DestinyMatrixResult`; сервіси анкет, запрошень і розрахунку матриці долі; правила розкриття закритої інформації |
| Хмелюк Діана | Frontend, пошук, тестування, документація | сутність `Keyword` і розбір тегів; контролери та Thymeleaf-сторінки (вхід, реєстрація, анкета, пошук, запрошення); стилі; модульні тести; README |

## Процес роботи з Git

- `main` — стабільна гілка, прямі коміти в неї не робляться.
- Кожна задача — окрема гілка `feature/...`.
- Зміни потрапляють у `main` лише через Pull Request після код-рев'ю іншої учасниці.

| Гілка | Автор | Рев'ю |
| --- | --- | --- |
| `feature/keyword-entity` | Хмелюк Діана | Бідюк Марія |
| `feature/backend-core` | Бідюк Марія | Хмелюк Діана |
| `feature/web-ui` | Хмелюк Діана | Бідюк Марія |

## Документація

- [docs/ЛР1-специфікація-вимог.pdf](docs/ЛР1-специфікація-вимог.pdf) — вимоги, користувацькі історії, UC-1
- [docs/ЛР2-архітектура.pdf](docs/ЛР2-архітектура.pdf) — архітектура та UML-діаграми
- Беклог і спринти: [дошка Trello](https://trello.com/b/3sk4AyKO)
