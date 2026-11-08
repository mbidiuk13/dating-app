package ua.kpi.datingapp.nfr;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ua.kpi.datingapp.compatibility.ArcanaDestinyMatrixService;
import ua.kpi.datingapp.compatibility.DestinyMatrixResult;
import ua.kpi.datingapp.profile.Profile;
import ua.kpi.datingapp.profile.ProfilePublicDTO;
import ua.kpi.datingapp.user.User;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * Модульні перевірки нефункціональних вимог ЛР1, які можна виміряти без запуску сервера.
 * Навантаження на весь застосунок (2 с, 100 сеансів) перевіряє performance/LoadTestRunner.
 */
class NonFunctionalRequirementsTest {

    // ---------- Продуктивність ----------

    /** «Розрахунок матриці долі — не довше 1 секунди для пари». */
    @Test
    void destinyMatrixForOnePairTakesLessThanOneSecond() {
        ArcanaDestinyMatrixService service = new ArcanaDestinyMatrixService();

        DestinyMatrixResult result = assertTimeout(Duration.ofSeconds(1), () ->
                service.calculateCompatibility(LocalDate.of(2004, 3, 1), LocalDate.of(2003, 7, 15)));

        assertThat(result.getCompatibilityPercentage()).isBetween(40, 100);
    }

    /** Із запасом: навіть 100 000 пар рахуються менш ніж за секунду. */
    @Test
    void hundredThousandPairsAreCalculatedWithinOneSecond() {
        ArcanaDestinyMatrixService service = new ArcanaDestinyMatrixService();
        LocalDate start = LocalDate.of(1970, 1, 1);

        assertTimeout(Duration.ofSeconds(1), () -> {
            for (int i = 0; i < 100_000; i++) {
                service.calculateCompatibility(start.plusDays(i % 15_000), start.plusDays((i * 7L) % 15_000));
            }
        });
    }

    // ---------- Безпека та приватність ----------

    /** «Хешування паролів за алгоритмом BCrypt». */
    @Test
    void passwordIsStoredOnlyAsBCryptHash() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();

        String hash = encoder.encode("password123");

        assertThat(hash).startsWith("$2a$10$").doesNotContain("password123");
        assertThat(encoder.matches("password123", hash)).isTrue();
        assertThat(encoder.matches("password124", hash)).isFalse();
        assertThat(encoder.encode("password123")).isNotEqualTo(hash); // сіль щоразу нова
    }

    /** «Закрита інформація фізично не передається на клієнт до виконання умови взаємної згоди». */
    @Test
    void publicPartOfProfileNeverContainsClosedData() {
        Profile profile = new Profile(new User("anna@test.ua", "hash"), "Анна", LocalDate.of(2000, 5, 5));
        profile.updateOpenData("Анна", "Люблю каву", "https://example.com/photo.jpg");
        profile.updateClosedData("tg-secret-contact", "secret-details");
        profile.updateVisibility(true, true);

        ProfilePublicDTO publicInfo = profile.getPublicInfo();

        assertThat(publicInfo.toString())
                .doesNotContain("tg-secret-contact")
                .doesNotContain("secret-details")
                .doesNotContain("photo.jpg");
        assertThat(publicInfo.age()).isNull();
        assertThat(profile.getPrivateInfo().hiddenPhotoUrls()).containsExactly("https://example.com/photo.jpg");
        assertThat(profile.getPrivateInfo().hiddenAge()).isEqualTo(profile.getAge());
    }

    // ---------- Зручність використання ----------

    /** «Подання результату сумісності у вигляді відсотка чи короткого тексту без складної термінології». */
    @Test
    void compatibilityIsShownAsPercentAndShortPlainText() {
        ArcanaDestinyMatrixService service = new ArcanaDestinyMatrixService();
        LocalDate start = LocalDate.of(1980, 1, 1);

        for (int i = 0; i < 500; i++) {
            DestinyMatrixResult result = service.calculateCompatibility(start.plusDays(i * 13L), start.plusDays(i * 29L));
            assertThat(result.getCompatibilityPercentage()).isBetween(0, 100);
            assertThat(result.getCompatibilityDescription())
                    .hasSizeLessThan(120)
                    .doesNotContainIgnoringCase("аркан")
                    .doesNotContainIgnoringCase("матриц");
        }
    }
}
