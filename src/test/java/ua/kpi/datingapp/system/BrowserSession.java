package ua.kpi.datingapp.system;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Один користувач з власним браузером (окремі cookie та сесія).
 * Дії записані так, як їх виконує людина: відкрити сторінку, ввести текст, натиснути кнопку.
 * Кожне натискання, що відкриває нову сторінку, рахується як перехід і заміряється за часом —
 * це потрібно для перевірки НФВ зручності (до 3 переходів) і продуктивності (до 2 с).
 *
 * Браузер за замовчуванням — HtmlUnit (без вікна, нічого встановлювати не треба).
 * З параметром -Dbrowser=chrome тести виконуються у справжньому Chrome.
 */
final class BrowserSession implements AutoCloseable {

    private final WebDriver driver;
    private final String baseUrl;
    private final List<Long> transitionTimesMs = new ArrayList<>();

    BrowserSession(String baseUrl) {
        this.baseUrl = baseUrl;
        this.driver = createDriver();
    }

    private static WebDriver createDriver() {
        if ("chrome".equalsIgnoreCase(System.getProperty("browser"))) {
            return new ChromeDriver();
        }
        // JavaScript увімкнено лише для того, щоб однаково заповнювати поле дати в обох браузерах
        return new HtmlUnitDriver(true);
    }

    // ---------- Базові дії ----------

    /** Відкрити адресу, ніби її ввели в адресному рядку. Не рахується як перехід. */
    void open(String path) {
        driver.get(baseUrl + path);
    }

    void type(String fieldName, String value) {
        WebElement field = driver.findElement(By.name(fieldName));
        field.clear();
        if (value != null && !value.isEmpty()) {
            field.sendKeys(value);
        }
    }

    /** Поле дати в різних браузерах вводиться по-різному, тому значення задається напряму. */
    void setDate(String fieldName, LocalDate date) {
        WebElement field = driver.findElement(By.name(fieldName));
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", field, date.toString());
    }

    void setChecked(String fieldName, boolean checked) {
        WebElement checkbox = driver.findElement(By.name(fieldName));
        if (checkbox.isSelected() != checked) {
            checkbox.click();
        }
    }

    /** Натиснути кнопку з таким текстом (перша на сторінці). */
    void clickButton(String text) {
        click(button(text));
    }

    /** Натиснути кнопку з таким текстом у блоці (картці результату, рядку таблиці), де є посилання з іменем. */
    void clickButtonNextTo(String linkText, String buttonText) {
        click(By.xpath("//*[self::article or self::tr][.//a[normalize-space()='" + linkText + "']]"
                + "//button[normalize-space()='" + buttonText + "']"));
    }

    void clickLink(String text) {
        click(By.linkText(text));
    }

    private void click(By locator) {
        WebElement target = driver.findElement(locator);
        WebElement currentPage = driver.findElement(By.tagName("html"));
        long start = System.nanoTime();
        target.click();
        waitForNextPage(currentPage);
        transitionTimesMs.add((System.nanoTime() - start) / 1_000_000);
    }

    private void waitForNextPage(WebElement previousPage) {
        if (driver instanceof HtmlUnitDriver) {
            return; // HtmlUnit завантажує наступну сторінку до повернення з click()
        }
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.stalenessOf(previousPage));
    }

    private static By button(String text) {
        return By.xpath("//button[normalize-space()='" + text + "']");
    }

    // ---------- Типові сценарії ----------

    void register(String email, String password, String firstName, LocalDate birthDate) {
        open("/register");
        type("email", email);
        type("password", password);
        type("confirmPassword", password);
        type("firstName", firstName);
        setDate("birthDate", birthDate);
        clickButton("Зареєструватися");
    }

    void login(String email, String password) {
        open("/login");
        type("email", email);
        type("password", password);
        clickButton("Увійти");
    }

    void logout() {
        clickButton("Вийти");
    }

    /** Заповнити анкету через сторінку «Моя анкета → Редагувати анкету». */
    void fillProfile(String description, String keywords, String photo, String contacts,
                     boolean photosPrivate, boolean agePrivate) {
        clickLink("Моя анкета");
        clickLink("Редагувати анкету");
        type("openDescription", description);
        type("keywords", keywords);
        type("openPhotos", photo);
        type("closedContactInfo", contacts);
        setChecked("photosPrivate", photosPrivate);
        setChecked("agePrivate", agePrivate);
        clickButton("Зберегти анкету");
    }

    void search(String query) {
        clickLink("Пошук");
        type("q", query);
        clickButton("Шукати");
    }

    // ---------- Що бачить користувач ----------

    /** Видимий текст сторінки. */
    String pageText() {
        return driver.findElement(By.tagName("body")).getText();
    }

    /** Увесь HTML, отриманий браузером, — для перевірки, що закриті дані не передавалися взагалі. */
    String pageSource() {
        return driver.getPageSource();
    }

    String currentPath() {
        return driver.getCurrentUrl().substring(baseUrl.length());
    }

    // ---------- Заміри ----------

    void resetMeasurements() {
        transitionTimesMs.clear();
    }

    int transitions() {
        return transitionTimesMs.size();
    }

    long slowestTransitionMs() {
        return transitionTimesMs.stream().mapToLong(Long::longValue).max().orElse(0);
    }

    List<Long> transitionTimesMs() {
        return List.copyOf(transitionTimesMs);
    }

    @Override
    public void close() {
        driver.quit();
    }
}
