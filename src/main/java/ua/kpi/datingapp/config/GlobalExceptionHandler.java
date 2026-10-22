package ua.kpi.datingapp.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import ua.kpi.datingapp.profile.ProfileNotFoundException;

/**
 * Безпечні повідомлення про помилки (НФВ надійності, ЛР1).
 * Користувач бачить лише сторінку error.html українською з кодом помилки.
 * Технічні подробиці (клас винятку, stack trace) записуються тільки в журнал logs/dating-app.log.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProfileNotFoundException.class)
    public ModelAndView notFound(ProfileNotFoundException ex, HttpServletRequest request) {
        log.info("404 {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return errorPage(HttpStatus.NOT_FOUND);
    }

    /** Неправильні параметри в адресі, наприклад /users/abc/profile. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ModelAndView badRequest(Exception ex, HttpServletRequest request) {
        log.info("400 {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return errorPage(HttpStatus.BAD_REQUEST);
    }

    /** Будь-яка інша помилка: stack trace — у журнал, користувачу — лише код. */
    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex; // їх обробляє Spring Security (сторінка входу або 403)
        }
        if (isClientDisconnect(ex)) {
            log.debug("Клієнт закрив з'єднання: {} {}", request.getMethod(), request.getRequestURI());
            return null;
        }
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ErrorResponse errorResponse) {
            // стандартні винятки Spring (405, 404 для статичних файлів тощо) зберігають свій код
            status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
        }
        if (status.is5xxServerError()) {
            log.error("Помилка під час обробки {} {}", request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.info("{} {} {}: {}", status.value(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
        return errorPage(status);
    }

    /** Браузер закрив сторінку до завершення відповіді — це не помилка застосунку. */
    private static boolean isClientDisconnect(Exception ex) {
        String name = ex.getClass().getSimpleName();
        return name.equals("ClientAbortException") || name.equals("AsyncRequestNotUsableException");
    }

    private static ModelAndView errorPage(HttpStatus status) {
        ModelAndView page = new ModelAndView("error");
        page.setStatus(status);
        page.addObject("status", status.value());
        return page;
    }
}
