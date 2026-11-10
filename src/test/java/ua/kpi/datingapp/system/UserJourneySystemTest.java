package ua.kpi.datingapp.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Системні тести: сценарії кінцевого користувача від реєстрації до обміну закритою інформацією.
 * Кожен користувач працює у власному браузері, усі дії виконуються через сторінки застосунку.
 * Перевіряються функціональні вимоги ЛР1 (US-01…US-05, UC-1 з альтернативними сценаріями).
 */
@DisplayName("Системні тести: сценарії користувача в браузері")
class UserJourneySystemTest extends SystemTestBase {

    private static final LocalDate ANNA_BIRTH = LocalDate.now().minusYears(25);
    private static final LocalDate BOHDAN_BIRTH = LocalDate.now().minusYears(27);

    @Test
    @DisplayName("US-01: реєстрація, невдалий і успішний вхід, створена порожня анкета")
    void registrationAndLogin() {
        BrowserSession anna = openBrowser();
        String email = uniqueEmail("Анна");

        anna.register(email, PASSWORD, "Анна", ANNA_BIRTH);
        assertThat(anna.currentPath()).startsWith("/login");
        assertThat(anna.pageText()).contains("Реєстрація успішна. Увійдіть у систему.");

        anna.login(email, "wrong-password");
        assertThat(anna.pageText()).contains("Невірний email або пароль.");

        anna.login(email, PASSWORD);
        assertThat(anna.pageText())
                .contains("Анна (моя анкета)")
                .contains("25 р.")
                .contains("Заповніть інформацію про себе");
    }

    @Test
    @DisplayName("US-01: помилки у формі реєстрації показуються українською, обліковий запис не створюється")
    void registrationErrorsAreShown() {
        BrowserSession guest = openBrowser();
        String email = uniqueEmail("Неповнолітній");

        guest.open("/register");
        guest.type("email", email);
        guest.type("password", PASSWORD);
        guest.type("confirmPassword", "other-password");
        guest.type("firstName", "Неповнолітній");
        guest.setDate("birthDate", LocalDate.now().minusYears(17));
        guest.clickButton("Зареєструватися");

        assertThat(guest.pageText())
                .contains("Паролі не збігаються")
                .contains("Реєстрація доступна з 18 років");

        guest.login(email, PASSWORD);
        assertThat(guest.pageText()).contains("Невірний email або пароль.");
    }

    @Test
    @DisplayName("UC-1: пошук → запрошення → підтвердження → закрита інформація й сумісність для обох")
    void mainScenarioFromSearchToPrivateData() {
        String tag = uniqueTag("джаз");

        BrowserSession anna = openBrowser();
        registerAndLogin(anna, "Анна", ANNA_BIRTH);
        anna.fillProfile("Люблю джаз і каву", tag + ", кава", "https://photos.test/anna.jpg", "@anna_tg", true, true);
        assertThat(anna.pageText()).contains("Анкету збережено");

        BrowserSession bohdan = openBrowser();
        registerAndLogin(bohdan, "Богдан", BOHDAN_BIRTH);
        bohdan.fillProfile("Граю на гітарі", tag, null, "@bohdan_tg", false, false);

        // US-03: пошук за ключовим словом; Анна приховала вік і фото
        bohdan.search(tag);
        assertThat(bohdan.pageText())
                .contains("Анна")
                .contains("Спільних інтересів: 1")
                .doesNotContain("25 р.");
        assertThat(bohdan.pageSource()).doesNotContain("@anna_tg", "anna.jpg");

        // US-04: запрошення прямо з результатів пошуку
        bohdan.clickButtonNextTo("Анна", "Надіслати запрошення");
        assertThat(bohdan.pageText())
                .contains("Запрошення надіслано")
                .contains("Стане доступною, коли ви обоє підтвердите запрошення.");
        assertThat(bohdan.pageSource()).doesNotContain("@anna_tg", "anna.jpg");

        // Анна бачить запрошення і підтверджує його
        anna.clickLink("Запрошення");
        assertThat(anna.pageText()).contains("Богдан").contains("PENDING");
        anna.clickButtonNextTo("Богдан", "Підтвердити");
        assertThat(anna.pageText())
                .contains("Запрошення підтверджено")
                .contains("@bohdan_tg")
                .contains("Сумісність за матрицею долі")
                .contains("%");

        // US-02, US-05: Богдан оновлює сторінку Анни — закриті дані, приховані фото й вік відкрилися
        bohdan.open(bohdan.currentPath());
        assertThat(bohdan.pageText())
                .contains("@anna_tg")
                .contains("Вік: 25 р.")
                .contains("Сумісність за матрицею долі");
        assertThat(bohdan.pageSource()).contains("https://photos.test/anna.jpg");
    }

    @Test
    @DisplayName("UC-1, 3a: відхилене запрошення не відкриває закриту інформацію, можна надіслати нове")
    void rejectedInvitationKeepsDataClosed() {
        String tag = uniqueTag("гори");

        BrowserSession anna = openBrowser();
        registerAndLogin(anna, "Анна", ANNA_BIRTH);
        anna.fillProfile("Ходжу в гори", tag, null, "@anna_tg", false, false);

        BrowserSession bohdan = openBrowser();
        registerAndLogin(bohdan, "Богдан", BOHDAN_BIRTH);
        bohdan.search(tag);
        bohdan.clickLink("Анна");
        bohdan.clickButton("Надіслати запрошення");

        anna.clickLink("Запрошення");
        anna.clickButtonNextTo("Богдан", "Відхилити");
        assertThat(anna.pageText()).contains("Запрошення відхилено").contains("REJECTED");

        bohdan.open(bohdan.currentPath());
        assertThat(bohdan.pageText()).contains("Попереднє запрошення було відхилено або скасовано.");
        assertThat(bohdan.pageSource()).doesNotContain("@anna_tg");
    }

    @Test
    @DisplayName("Видалення анкети: підтвердження, вихід, анкета зникає з пошуку, увійти більше не можна")
    void deleteAccount() {
        String tag = uniqueTag("шахи");

        BrowserSession anna = openBrowser();
        String annaEmail = registerAndLogin(anna, "Анна", ANNA_BIRTH);
        anna.fillProfile("Граю в шахи", tag, null, null, false, false);

        anna.clickLink("Моя анкета");
        anna.clickLink("Редагувати анкету");
        anna.setChecked("confirm", true);
        anna.clickButton("Видалити анкету");
        assertThat(anna.pageText()).contains("Анкету та обліковий запис видалено.");

        anna.login(annaEmail, PASSWORD);
        assertThat(anna.pageText()).contains("Невірний email або пароль.");

        BrowserSession bohdan = openBrowser();
        registerAndLogin(bohdan, "Богдан", BOHDAN_BIRTH);
        bohdan.search(tag);
        assertThat(bohdan.pageText()).contains("Анкет за цими ключовими словами не знайдено.");
    }

    @Test
    @DisplayName("Помилки: неіснуюча анкета й неправильна адреса дають зрозумілі сторінки без технічних подробиць")
    void errorPagesAreUserFriendly() {
        BrowserSession user = openBrowser();
        registerAndLogin(user, "Олег", BOHDAN_BIRTH);

        user.open("/users/999999/profile");
        assertThat(user.pageText()).contains("Сторінку не знайдено").contains("Код помилки: 404");
        assertThat(user.pageSource()).doesNotContain("Exception", "at ua.kpi", "ProfileNotFound");

        user.open("/users/abc/profile");
        assertThat(user.pageText()).contains("Некоректний запит").contains("Код помилки: 400");
        assertThat(user.pageSource()).doesNotContain("Exception", "NumberFormat");
    }

    @Test
    @DisplayName("Вихід із системи завершує сесію: закриті сторінки знову вимагають входу")
    void logoutEndsSession() {
        BrowserSession user = openBrowser();
        registerAndLogin(user, "Олег", BOHDAN_BIRTH);

        user.logout();
        assertThat(user.pageText()).contains("Ви вийшли із системи.");

        user.open("/search");
        assertThat(user.currentPath()).startsWith("/login");
    }
}
