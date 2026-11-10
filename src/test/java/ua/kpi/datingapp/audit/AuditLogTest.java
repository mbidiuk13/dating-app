package ua.kpi.datingapp.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юніт-тести журналу аудиту (НФВ безпеки: журналювання дій доступу).
 * Записи перехоплюються в пам'яті, файл logs/audit.log не потрібен.
 */
@DisplayName("AuditLog: записи журналу дій доступу")
class AuditLogTest {

    private final AuditLog auditLog = new AuditLog();
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    private final ListAppender<ILoggingEvent> captured = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        captured.start();
        auditLogger.addAppender(captured);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(captured);
    }

    @Test
    @DisplayName("Перегляд анкети: лише ідентифікатори та рішення про доступ, без даних анкети")
    void profileViewRecordsOnlyIdsAndDecision() {
        auditLog.profileViewed(2L, 1L, true);
        auditLog.profileViewed(3L, 1L, false);

        assertThat(captured.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "PROFILE_VIEW viewer=2 owner=1 privateData=GRANTED",
                        "PROFILE_VIEW viewer=3 owner=1 privateData=DENIED");
    }

    @Test
    @DisplayName("Невдалий вхід записується з рівнем WARN, успішний — INFO")
    void loginsAreRecordedWithLevels() {
        auditLog.loginFailed("anna@test.ua", "127.0.0.1");
        auditLog.loginSucceeded("anna@test.ua", "127.0.0.1");

        assertThat(captured.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN, Level.INFO);
        assertThat(captured.list.get(0).getFormattedMessage())
                .isEqualTo("LOGIN_FAILURE email=anna@test.ua ip=127.0.0.1");
    }

    @Test
    @DisplayName("Видалення облікового запису фіксується в журналі")
    void accountDeletionIsRecorded() {
        auditLog.accountDeleted(7L);

        assertThat(captured.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("ACCOUNT_DELETED user=7");
    }
}
