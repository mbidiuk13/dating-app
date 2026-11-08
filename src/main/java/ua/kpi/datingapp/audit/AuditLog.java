package ua.kpi.datingapp.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Журнал дій доступу (НФВ безпеки, ЛР1): хто, коли і чию анкету переглядав,
 * входи в систему та видалення облікових записів.
 * Записи потрапляють в окремий файл logs/audit.log (див. logback-spring.xml).
 * Закриті дані анкет у журнал не записуються — лише ідентифікатори користувачів.
 */
@Component
public class AuditLog {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    public void profileViewed(Long viewerUserId, Long ownerUserId, boolean privateAccess) {
        audit.info("PROFILE_VIEW viewer={} owner={} privateData={}",
                viewerUserId, ownerUserId, privateAccess ? "GRANTED" : "DENIED");
    }

    public void loginSucceeded(String email, String ip) {
        audit.info("LOGIN_SUCCESS email={} ip={}", email, ip);
    }

    public void loginFailed(String email, String ip) {
        audit.warn("LOGIN_FAILURE email={} ip={}", email, ip);
    }

    public void accountDeleted(Long userId) {
        audit.info("ACCOUNT_DELETED user={}", userId);
    }
}
