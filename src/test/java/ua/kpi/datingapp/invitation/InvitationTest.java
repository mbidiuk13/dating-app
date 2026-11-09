package ua.kpi.datingapp.invitation;

import org.junit.jupiter.api.Test;
import ua.kpi.datingapp.compatibility.DestinyMatrixResult;
import ua.kpi.datingapp.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Перевірка переходів з діаграми станів ЛР2. */
class InvitationTest {

    private final User alice = new User("a@test.ua", "hash");
    private final User bob = new User("b@test.ua", "hash");

    @Test
    void newInvitationIsPending() {
        Invitation invitation = new Invitation(alice, bob);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.getSentAt()).isNotNull();
        assertThat(invitation.getDestinyMatrixResult()).isNull();
    }

    @Test
    void acceptStoresCompatibilityResult() {
        Invitation invitation = new Invitation(alice, bob);
        DestinyMatrixResult result = new DestinyMatrixResult(80, "Висока сумісність");

        invitation.accept(result);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getDestinyMatrixResult()).isSameAs(result);
        assertThat(invitation.getStatusChangedAt()).isNotNull();
    }

    @Test
    void rejectedInvitationHasNoCompatibilityResult() {
        Invitation invitation = new Invitation(alice, bob);

        invitation.reject();

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.REJECTED);
        assertThat(invitation.getDestinyMatrixResult()).isNull();
    }

    @Test
    void terminalStatesCannotChange() {
        Invitation invitation = new Invitation(alice, bob);
        invitation.cancel();

        assertThatThrownBy(() -> invitation.accept(new DestinyMatrixResult(90, "—")))
                .isInstanceOf(InvitationException.class);
        assertThatThrownBy(invitation::reject)
                .isInstanceOf(InvitationException.class);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
    }
}
