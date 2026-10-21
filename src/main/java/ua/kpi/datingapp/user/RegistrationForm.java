package ua.kpi.datingapp.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Дані форми реєстрації (US-01) з валідацією полів. */
public class RegistrationForm {

    @NotBlank(message = "Вкажіть email")
    @Email(message = "Некоректний email")
    private String email;

    @NotBlank(message = "Вкажіть пароль")
    @Size(min = 8, max = 100, message = "Пароль має містити від 8 до 100 символів")
    private String password;

    @NotBlank(message = "Повторіть пароль")
    private String confirmPassword;

    @NotBlank(message = "Вкажіть ім'я")
    @Size(max = 50, message = "Не більше 50 символів")
    private String firstName;

    @NotNull(message = "Вкажіть дату народження")
    @Past(message = "Дата народження має бути в минулому")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

    public boolean isAdult() {
        return birthDate != null && !birthDate.isAfter(LocalDate.now().minusYears(18));
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
}
