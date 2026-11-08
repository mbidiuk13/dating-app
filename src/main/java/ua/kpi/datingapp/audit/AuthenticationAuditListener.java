package ua.kpi.datingapp.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/** Записує в журнал аудиту кожну спробу входу (успішну й невдалу). */
@Component("loginAuditListener")
public class AuthenticationAuditListener {

    private final AuditLog auditLog;

    public AuthenticationAuditListener(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        auditLog.loginSucceeded(authentication.getName(), remoteAddress(authentication));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Authentication authentication = event.getAuthentication();
        auditLog.loginFailed(authentication.getName(), remoteAddress(authentication));
    }

    private static String remoteAddress(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return "-";
    }
}
