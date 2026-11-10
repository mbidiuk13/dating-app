package ua.kpi.datingapp.system;

import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Основа системних тестів: застосунок запускається повністю, зі справжнім вебсервером Tomcat
 * на випадковому порту (без MockMvc), а користувачі працюють з ним через браузер (Selenium WebDriver).
 * Перевіряється система в цілому: сторінки, форми, сесії, cookie, CSRF, сервіси й БД.
 * БД — вбудована H2 (профіль test), тому PostgreSQL для тестів не потрібен.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class SystemTestBase {

    protected static final String PASSWORD = "password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @LocalServerPort
    private int port;

    private final List<BrowserSession> sessions = new ArrayList<>();

    /** Новий користувач із власним браузером. */
    protected BrowserSession openBrowser() {
        BrowserSession session = new BrowserSession("http://localhost:" + port);
        sessions.add(session);
        return session;
    }

    /** Реєстрація та вхід через сторінки застосунку. Повертає email. */
    protected String registerAndLogin(BrowserSession browser, String firstName, LocalDate birthDate) {
        String email = uniqueEmail(firstName);
        browser.register(email, PASSWORD, firstName, birthDate);
        browser.login(email, PASSWORD);
        return email;
    }

    protected static String uniqueEmail(String firstName) {
        return "system" + SEQUENCE.incrementAndGet() + "." + Integer.toHexString(firstName.hashCode()) + "@test.ua";
    }

    /** Ключове слово, якого немає в інших тестах, — щоб пошук знаходив лише потрібні анкети. */
    protected static String uniqueTag(String base) {
        return base + SEQUENCE.incrementAndGet();
    }

    @AfterEach
    void closeBrowsers() {
        sessions.forEach(BrowserSession::close);
        sessions.clear();
    }
}
