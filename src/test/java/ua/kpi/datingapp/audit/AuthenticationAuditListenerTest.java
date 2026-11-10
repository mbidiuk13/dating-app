package ua.kpi.datingapp.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import static org.mockito.Mockito.verify;

/** Юніт-тести: кожна спроба входу потрапляє в журнал аудиту разом з IP-адресою. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationAuditListener: журналювання входів")
class AuthenticationAuditListenerTest {

    @Mock
    private AuditLog auditLog;

    @InjectMocks
    private AuthenticationAuditListener listener;

    @Test
    @DisplayName("Успішний вхід записується з IP-адресою клієнта")
    void successfulLoginIsRecordedWithIp() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("anna@test.ua", null, java.util.List.of());
        authentication.setDetails(new WebAuthenticationDetails("10.0.0.5", "session-id"));

        listener.onSuccess(new AuthenticationSuccessEvent(authentication));

        verify(auditLog).loginSucceeded("anna@test.ua", "10.0.0.5");
    }

    @Test
    @DisplayName("Невдалий вхід записується; без даних про запит замість IP ставиться «-»")
    void failedLoginIsRecorded() {
        UsernamePasswordAuthenticationToken attempt =
                UsernamePasswordAuthenticationToken.unauthenticated("anna@test.ua", "wrong-password");

        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(attempt, new BadCredentialsException("bad")));

        verify(auditLog).loginFailed("anna@test.ua", "-");
    }
}
