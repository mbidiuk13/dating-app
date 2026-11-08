package ua.kpi.datingapp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ua.kpi.datingapp.compatibility.DestinyMatrixResult;
import ua.kpi.datingapp.compatibility.DestinyMatrixService;
import ua.kpi.datingapp.invitation.InvitationDTO;
import ua.kpi.datingapp.invitation.InvitationService;
import ua.kpi.datingapp.invitation.InvitationStatus;
import ua.kpi.datingapp.user.User;
import ua.kpi.datingapp.user.UserService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * НФВ надійності: «атомарне виконання операцій у межах однієї транзакції».
 * Підтвердження запрошення змінює статус і зберігає результат сумісності.
 * Тут збереження результату навмисно ламається (опис довший за 500 символів колонки),
 * і перевіряється, що статус теж не змінився — транзакцію відкочено повністю.
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionAtomicityIntegrationTest {

    @MockitoBean
    private DestinyMatrixService destinyMatrixService;

    @Autowired
    private UserService userService;
    @Autowired
    private InvitationService invitationService;

    @Test
    void failedCompatibilitySaveRollsBackStatusChange() {
        User sender = userService.register("atomic-sender@test.ua", "password123", "Sender", LocalDate.of(2000, 1, 1));
        User receiver = userService.register("atomic-receiver@test.ua", "password123", "Receiver", LocalDate.of(2001, 2, 2));
        InvitationDTO invitation = invitationService.sendInvitation(sender.getId(), receiver.getId());

        when(destinyMatrixService.calculateCompatibility(any(), any()))
                .thenReturn(new DestinyMatrixResult(80, "x".repeat(600)));

        assertThatThrownBy(() -> invitationService.acceptInvitation(invitation.id(), receiver.getId()))
                .isInstanceOf(RuntimeException.class);

        InvitationDTO afterFailure = invitationService.getReceived(receiver.getId()).get(0);
        assertThat(afterFailure.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(afterFailure.statusChangedAt()).isNull();
        assertThat(afterFailure.compatibilityPercentage()).isNull();
        assertThat(invitationService.hasMutualAccess(sender.getId(), receiver.getId())).isFalse();
    }
}
