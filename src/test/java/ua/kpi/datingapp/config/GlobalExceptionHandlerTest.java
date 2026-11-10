package ua.kpi.datingapp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.ModelAndView;
import ua.kpi.datingapp.profile.ProfileNotFoundException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Юніт-тести безпечних повідомлень про помилки (НФВ надійності):
 * користувач отримує лише сторінку error з кодом, без тексту винятку.
 */
@DisplayName("GlobalExceptionHandler: безпечні сторінки помилок")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/5/profile");

    @Test
    @DisplayName("Анкету не знайдено → сторінка 404")
    void missingProfileGivesNotFoundPage() {
        ModelAndView page = handler.notFound(new ProfileNotFoundException(5L), request);

        assertErrorPage(page, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Відсутній або неправильний параметр запиту → сторінка 400")
    void wrongParameterGivesBadRequestPage() {
        ModelAndView page = handler.badRequest(new MissingServletRequestParameterException("receiverId", "Long"), request);

        assertErrorPage(page, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Неочікувана помилка → 500, текст винятку не потрапляє на сторінку")
    void unexpectedErrorHidesDetails() throws Exception {
        ModelAndView page = handler.unexpected(new IllegalStateException("SQL: select password_hash from users"), request);

        assertErrorPage(page, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(page.getModel()).containsOnlyKeys("status");
        assertThat(page.getModel().values().toString()).doesNotContain("SQL", "password_hash");
    }

    @Test
    @DisplayName("Стандартні помилки Spring зберігають свій код (метод не підтримується → 405)")
    void springErrorsKeepTheirStatus() throws Exception {
        ModelAndView page = handler.unexpected(new HttpRequestMethodNotSupportedException("DELETE"), request);

        assertErrorPage(page, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Помилки доступу передаються Spring Security, а не перетворюються на 500")
    void securityExceptionsAreRethrown() {
        assertThatThrownBy(() -> handler.unexpected(new AccessDeniedException("denied"), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Закрита браузером сторінка не вважається помилкою застосунку")
    void clientDisconnectIsIgnored() throws Exception {
        assertThat(handler.unexpected(new ClientAbortException(), request)).isNull();
    }

    private static void assertErrorPage(ModelAndView page, HttpStatus status) {
        assertThat(page.getViewName()).isEqualTo("error");
        assertThat(page.getStatus()).isEqualTo(status);
        assertThat(page.getModel()).containsEntry("status", status.value());
    }

    /** Імітує виняток Tomcat, коли користувач закрив сторінку до кінця відповіді. */
    private static class ClientAbortException extends IOException {
    }
}
