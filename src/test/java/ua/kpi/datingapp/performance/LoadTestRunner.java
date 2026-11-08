package ua.kpi.datingapp.performance;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Навантажувальне тестування НФВ продуктивності (ЛР1):
 * «завантаження сторінки анкети та результатів пошуку — до 2 секунд при базі до 10 000 анкет»,
 * «одночасна обробка — щонайменше 100 сеансів без помилок із часом відповіді до 2 секунд».
 *
 * Як запустити:
 * 1) виконати docs/sql/seed-10000-profiles.sql у базі dating_app;
 * 2) запустити застосунок (DatingAppApplication);
 * 3) запустити цей клас зеленою кнопкою ▶ біля методу main.
 *
 * Сценарій: 100 користувачів load1…load100@test.ua входять у систему (100 окремих сеансів),
 * потім одночасно по 5 разів відкривають результати пошуку та сторінку анкети.
 * Це не JUnit-тест, тому «Run All Tests» його не запускає: йому потрібен працюючий сервер.
 */
public final class LoadTestRunner {

    private static final String BASE_URL = System.getProperty("baseUrl", "https://localhost:8443");
    private static final int SESSIONS = Integer.getInteger("sessions", 100);
    private static final int ROUNDS = Integer.getInteger("rounds", 5);
    private static final String SEARCH_QUERY = "музика кава";
    private static final String PASSWORD = "password123";
    private static final long LIMIT_MS = 2_000;

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern PROFILE_LINK = Pattern.compile("/users/(\\d+)/profile");

    private LoadTestRunner() {
    }

    public static void main(String[] args) throws Exception {
        // самопідписаний сертифікат localhost: довіряємо йому лише в цьому тесті
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        SSLContext trustAll = trustAllContext();

        System.out.printf("Навантажувальний тест: %s, сеансів: %d, повторів: %d%n%n", BASE_URL, SESSIONS, ROUNDS);

        // 0. Базовий час відповіді для одного користувача (без навантаження)
        Session single = new Session(trustAll, 1);
        single.login();
        String profilePath = single.findProfilePath();
        List<Long> baseline = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            baseline.add(single.timedGet(searchPath()));
            baseline.add(single.timedGet(profilePath));
        }
        Stats baselineStats = Stats.of(baseline);
        System.out.println("1 користувач (без навантаження): " + baselineStats);

        // 1. Вхід 100 користувачів — 100 окремих сеансів
        ExecutorService pool = Executors.newFixedThreadPool(SESSIONS);
        List<Session> sessions = new ArrayList<>();
        List<Future<?>> logins = new ArrayList<>();
        for (int i = 1; i <= SESSIONS; i++) {
            Session session = new Session(trustAll, i);
            sessions.add(session);
            logins.add(pool.submit(() -> {
                session.login();
                return null;
            }));
        }
        for (Future<?> login : logins) {
            login.get(2, TimeUnit.MINUTES);
        }
        System.out.println("Увійшли в систему: " + sessions.size() + " сеансів");

        // 2. Усі сеанси одночасно відкривають пошук і анкету
        Queue<Long> searchTimes = new ConcurrentLinkedQueue<>();
        Queue<Long> profileTimes = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> work = new ArrayList<>();
        long startedAt = System.nanoTime();
        for (Session session : sessions) {
            work.add(pool.submit(() -> {
                start.await();
                for (int round = 0; round < ROUNDS; round++) {
                    try {
                        searchTimes.add(session.timedGet(searchPath()));
                        profileTimes.add(session.timedGet(profilePath));
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : work) {
            future.get(5, TimeUnit.MINUTES);
        }
        double totalSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        pool.shutdown();

        Stats search = Stats.of(new ArrayList<>(searchTimes));
        Stats profile = Stats.of(new ArrayList<>(profileTimes));
        int requests = search.count() + profile.count();

        System.out.println();
        System.out.println("Результати під навантаженням " + SESSIONS + " одночасних сеансів:");
        System.out.println("  пошук «" + SEARCH_QUERY + "»: " + search);
        System.out.println("  сторінка анкети:        " + profile);
        System.out.printf("  запитів: %d за %.1f с (%.0f запитів/с), помилок: %d%n",
                requests, totalSeconds, requests / totalSeconds, errors.get());

        boolean fast = search.p95() <= LIMIT_MS && profile.p95() <= LIMIT_MS;
        boolean noErrors = errors.get() == 0;
        System.out.println();
        System.out.println("Сторінки до 2 с (95-й перцентиль): " + (fast ? "ВИКОНАНО" : "НЕ ВИКОНАНО"));
        System.out.println(SESSIONS + " одночасних сеансів без помилок:  " + (noErrors ? "ВИКОНАНО" : "НЕ ВИКОНАНО"));
    }

    private static String searchPath() {
        return "/search?q=" + URLEncoder.encode(SEARCH_QUERY, StandardCharsets.UTF_8);
    }

    /** Окремий сеанс: власний HttpClient і власні cookie (JSESSIONID). */
    private static final class Session {

        private final HttpClient client;
        private final String email;

        Session(SSLContext sslContext, int number) {
            this.email = "load" + number + "@test.ua";
            this.client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .sslContext(sslContext)
                    .cookieHandler(new CookieManager())
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }

        void login() throws Exception {
            String loginPage = get("/login").body();
            Matcher csrf = CSRF.matcher(loginPage);
            if (!csrf.find()) {
                throw new IllegalStateException("На сторінці входу не знайдено CSRF-токен");
            }
            String form = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + PASSWORD
                    + "&_csrf=" + URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8);
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(BASE_URL + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.uri().getPath().startsWith("/login")) {
                throw new IllegalStateException("Не вдалося увійти як " + email
                        + ". Чи виконано docs/sql/seed-10000-profiles.sql?");
            }
        }

        String findProfilePath() throws Exception {
            Matcher link = PROFILE_LINK.matcher(get(searchPath()).body());
            if (!link.find()) {
                throw new IllegalStateException("Пошук нічого не знайшов. Чи виконано seed-10000-profiles.sql?");
            }
            return link.group();
        }

        long timedGet(String path) throws Exception {
            long start = System.nanoTime();
            HttpResponse<String> response = get(path);
            long millis = (System.nanoTime() - start) / 1_000_000;
            if (response.statusCode() != 200) {
                throw new IllegalStateException(path + " → HTTP " + response.statusCode());
            }
            return millis;
        }

        private HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    /** Середнє, медіана, 95-й перцентиль і максимум часу відповіді в мілісекундах. */
    private record Stats(int count, double avg, long median, long p95, long max) {

        static Stats of(List<Long> times) {
            if (times.isEmpty()) {
                return new Stats(0, 0, 0, 0, 0);
            }
            List<Long> sorted = new ArrayList<>(times);
            Collections.sort(sorted);
            double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
            return new Stats(sorted.size(), avg,
                    sorted.get(sorted.size() / 2),
                    sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1),
                    sorted.get(sorted.size() - 1));
        }

        @Override
        public String toString() {
            return String.format("запитів %d, середнє %.0f мс, медіана %d мс, 95%% — %d мс, максимум %d мс",
                    count, avg, median, p95, max);
        }
    }

    private static SSLContext trustAllContext() throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        return context;
    }
}
