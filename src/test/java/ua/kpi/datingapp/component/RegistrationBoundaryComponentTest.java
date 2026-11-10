package ua.kpi.datingapp.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ua.kpi.datingapp.user.UserRepository;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Компонентні тести реєстрації за методом граничних значень:
 * AuthController + перевірка форми + UserService + БД H2.
 * Для кожного обмеження перевіряється значення на межі (приймається) і за межею (відхиляється
 * зрозумілим повідомленням, а не помилкою сервера).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Реєстрація: граничні значення полів")
class RegistrationBoundaryComponentTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Рівно 18 років сьогодні — реєстрація успішна")
    void exactlyEighteenIsAccepted() throws Exception {
        register(uniqueEmail(), LocalDate.now().minusYears(18))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("До 18 років бракує одного дня — реєстрацію відхилено")
    void oneDayBeforeEighteenIsRejected() throws Exception {
        String email = uniqueEmail();

        register(email, LocalDate.now().minusYears(18).plusDays(1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Реєстрація доступна з 18 років")));
        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    @Test
    @DisplayName("Email довжиною 254 символи (максимум за RFC 5321) — реєстрація успішна")
    void emailOfMaximumLengthIsAccepted() throws Exception {
        String email = emailOfLength(254);

        register(email, LocalDate.of(2000, 1, 1))
                .andExpect(redirectedUrl("/login"));
        assertThat(userRepository.existsByEmail(email)).isTrue();
    }

    @Test
    @DisplayName("Задовгий email (300 символів) — повідомлення у формі, а не помилка сервера")
    void tooLongEmailIsRejectedWithMessage() throws Exception {
        String email = emailOfLength(300);

        register(email, LocalDate.of(2000, 1, 1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email має бути не довшим за 254 символи")));
        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    @Test
    @DisplayName("Вік 120 років (максимум у фільтрі пошуку) — реєстрація успішна")
    void ageOfOneHundredTwentyIsAccepted() throws Exception {
        register(uniqueEmail(), LocalDate.now().minusYears(120))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Вік 121 рік (більше за максимум 120) — реєстрацію відхилено")
    void ageAboveMaximumIsRejected() throws Exception {
        String email = uniqueEmail();

        register(email, LocalDate.now().minusYears(121))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Перевірте дату народження")));
        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    @Test
    @DisplayName("Дата народження 1850 року — відхилено (раніше приймалася, а анкета не знаходилася пошуком)")
    void unrealisticBirthDateIsRejected() throws Exception {
        String email = uniqueEmail();

        register(email, LocalDate.of(1850, 1, 1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Перевірте дату народження")));
        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    private ResultActions register(String email, LocalDate birthDate) throws Exception {
        return mockMvc.perform(post("/register")
                .param("email", email)
                .param("password", "password123")
                .param("confirmPassword", "password123")
                .param("firstName", "Тест")
                .param("birthDate", birthDate.toString())
                .with(csrf()));
    }

    private static String uniqueEmail() {
        return "boundary" + SEQUENCE.incrementAndGet() + "@test.ua";
    }

    /**
     * Коректний за форматом email заданої довжини: локальна частина до 64 символів,
     * домен із частин до 63 символів (обмеження перевірки @Email).
     */
    private static String emailOfLength(int length) {
        String local = ("u" + SEQUENCE.incrementAndGet() + "x".repeat(64)).substring(0, 60);
        int remaining = length - local.length() - "@".length() - ".ua".length();
        StringBuilder domain = new StringBuilder();
        while (remaining > 0) {
            if (!domain.isEmpty()) {
                domain.append('.');
                remaining--;
            }
            int label = Math.min(60, remaining);
            if (remaining - label == 1) {
                label--; // щоб наступна частина домену не була порожньою
            }
            domain.append("d".repeat(label));
            remaining -= label;
        }
        String email = local + "@" + domain + ".ua";
        assertThat(email).hasSize(length);
        return email;
    }
}
