package ua.kpi.datingapp.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Системна перевірка нефункціональних вимог ЛР1 у браузері:
 * - зручність: пошук анкети, надсилання та підтвердження запрошення — не більше 3 переходів
 *   з головної сторінки (після входу головна — «Моя анкета»);
 * - продуктивність: кожна сторінка відкривається менш ніж за 2 с;
 * - використання пам'яті застосунком після сценарію (довідково, для звіту).
 * Навантаження на 100 сеансів і 10 000 анкет перевіряє окремо LoadTestRunner.
 */
@DisplayName("Системні тести НФВ: кількість переходів, час сторінок, пам'ять")
class UsabilityAndPerformanceSystemTest extends SystemTestBase {

    private static final int MAX_TRANSITIONS = 3;
    private static final long MAX_PAGE_TIME_MS = 2_000;

    @Test
    @DisplayName("Знайти анкету й надіслати запрошення — не більше 3 переходів, кожен до 2 с")
    void searchAndInviteTakesAtMostThreeTransitions() {
        String tag = uniqueTag("кіно");
        BrowserSession anna = openBrowser();
        registerAndLogin(anna, "Анна", LocalDate.now().minusYears(24));
        anna.fillProfile("Люблю кіно", tag, null, "@anna_tg", false, false);

        BrowserSession bohdan = openBrowser();
        registerAndLogin(bohdan, "Богдан", LocalDate.now().minusYears(26));
        bohdan.resetMeasurements(); // відлік від головної сторінки

        bohdan.search(tag);                                         // 1: «Пошук», 2: «Шукати»
        bohdan.clickButtonNextTo("Анна", "Надіслати запрошення");   // 3

        assertThat(bohdan.pageText()).contains("Запрошення надіслано");
        report("Пошук і надсилання запрошення", bohdan);
        assertThat(bohdan.transitions()).isLessThanOrEqualTo(MAX_TRANSITIONS);
        assertThat(bohdan.slowestTransitionMs()).isLessThan(MAX_PAGE_TIME_MS);
    }

    @Test
    @DisplayName("Підтвердити запрошення — не більше 3 переходів, одразу видно сумісність")
    void acceptInvitationTakesAtMostThreeTransitions() {
        String tag = uniqueTag("театр");
        BrowserSession anna = openBrowser();
        String annaEmail = registerAndLogin(anna, "Анна", LocalDate.now().minusYears(24));
        anna.fillProfile("Люблю театр", tag, null, "@anna_tg", false, false);
        anna.logout();

        BrowserSession bohdan = openBrowser();
        registerAndLogin(bohdan, "Богдан", LocalDate.now().minusYears(26));
        bohdan.search(tag);
        bohdan.clickButtonNextTo("Анна", "Надіслати запрошення");

        anna.login(annaEmail, PASSWORD);
        anna.resetMeasurements();

        anna.clickLink("Запрошення");                    // 1
        anna.clickButtonNextTo("Богдан", "Підтвердити"); // 2

        assertThat(anna.pageText()).contains("Сумісність за матрицею долі");
        report("Підтвердження запрошення", anna);
        assertThat(anna.transitions()).isLessThanOrEqualTo(MAX_TRANSITIONS);
        assertThat(anna.slowestTransitionMs()).isLessThan(MAX_PAGE_TIME_MS);
    }

    @Test
    @DisplayName("Усі основні сторінки відкриваються до 2 с; пам'ять застосунку після сценарію")
    void mainPagesOpenWithinTwoSeconds() {
        String tag = uniqueTag("книги");
        BrowserSession user = openBrowser();
        registerAndLogin(user, "Олена", LocalDate.now().minusYears(30));
        user.fillProfile("Читаю книги", tag, null, "@olena", false, false);

        visitMainPages(user, tag); // прогрів: перші звернення компілюють шаблони
        user.resetMeasurements();
        visitMainPages(user, tag);

        report("Основні сторінки (після прогріву)", user);
        printMemoryUsage();
        assertThat(user.slowestTransitionMs()).isLessThan(MAX_PAGE_TIME_MS);
    }

    private static void visitMainPages(BrowserSession user, String tag) {
        user.clickLink("Моя анкета");
        user.clickLink("Редагувати анкету");
        user.search(tag);
        user.clickLink("Запрошення");
    }

    private static void report(String scenario, BrowserSession session) {
        System.out.printf("%n%s: переходів %d, час кожного переходу (мс): %s, найповільніший: %d мс%n",
                scenario, session.transitions(), session.transitionTimesMs(), session.slowestTransitionMs());
    }

    /** Застосунок у системних тестах працює в тій самій JVM, тож це пам'ять сервера разом із тестами. */
    private static void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        System.out.printf("Використання пам'яті (heap): %d МБ із доступних %d МБ%n", usedMb, maxMb);
    }
}
