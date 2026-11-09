package ua.kpi.datingapp.user;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Юніт-тести перевірки форми реєстрації (US-01): повідомлення українською та граничні значення. */
@DisplayName("RegistrationForm: перевірка полів форми реєстрації")
class RegistrationFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    @DisplayName("Правильно заповнена форма не має помилок")
    void validFormHasNoViolations() {
        assertThat(validator.validate(validForm())).isEmpty();
    }

    @Test
    @DisplayName("Некоректний email і короткий пароль дають повідомлення українською")
    void invalidFieldsGetUkrainianMessages() {
        RegistrationForm form = validForm();
        form.setEmail("not-an-email");
        form.setPassword("short");
        form.setConfirmPassword("short");

        Set<ConstraintViolation<RegistrationForm>> violations = validator.validate(form);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("Некоректний email", "Пароль має містити від 8 до 100 символів");
    }

    @Test
    @DisplayName("Межа довжини пароля: 8 символів — можна, 7 — ні")
    void passwordLengthBoundary() {
        RegistrationForm form = validForm();
        form.setPassword("12345678");
        assertThat(validator.validate(form)).isEmpty();

        form.setPassword("1234567");
        assertThat(validator.validate(form)).hasSize(1);
    }

    @Test
    @DisplayName("Паролі мають збігатися")
    void passwordsMustMatch() {
        RegistrationForm form = validForm();
        assertThat(form.passwordsMatch()).isTrue();

        form.setConfirmPassword("password124");
        assertThat(form.passwordsMatch()).isFalse();

        form.setPassword(null);
        assertThat(form.passwordsMatch()).isFalse();
    }

    @Test
    @DisplayName("Межа повноліття: рівно 18 років сьогодні — можна, на день менше — ні")
    void adultBoundary() {
        RegistrationForm form = validForm();

        form.setBirthDate(LocalDate.now().minusYears(18));
        assertThat(form.isAdult()).isTrue();

        form.setBirthDate(LocalDate.now().minusYears(18).plusDays(1));
        assertThat(form.isAdult()).isFalse();
    }

    @Test
    @DisplayName("Дата народження в майбутньому відхиляється")
    void birthDateInFutureIsRejected() {
        RegistrationForm form = validForm();
        form.setBirthDate(LocalDate.now().plusDays(1));

        assertThat(validator.validate(form))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Дата народження має бути в минулому");
    }

    private static RegistrationForm validForm() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail("anna@test.ua");
        form.setPassword("password123");
        form.setConfirmPassword("password123");
        form.setFirstName("Анна");
        form.setBirthDate(LocalDate.of(2001, 4, 12));
        return form;
    }
}
