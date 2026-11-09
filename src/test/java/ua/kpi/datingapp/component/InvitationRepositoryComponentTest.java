package ua.kpi.datingapp.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.datingapp.compatibility.DestinyMatrixResult;
import ua.kpi.datingapp.invitation.Invitation;
import ua.kpi.datingapp.invitation.InvitationRepository;
import ua.kpi.datingapp.user.User;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Компонентні тести InvitationRepository на вбудованій БД H2:
 * пошук активних запрошень в обох напрямках, порядок, каскадне видалення результату сумісності.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("InvitationRepository + БД: запити запрошень")
class InvitationRepositoryComponentTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private InvitationRepository invitationRepository;

    private User anna;
    private User bohdan;
    private User carl;

    @BeforeEach
    void createUsers() {
        anna = entityManager.persist(new User("anna@test.ua", "hash"));
        bohdan = entityManager.persist(new User("bohdan@test.ua", "hash"));
        carl = entityManager.persist(new User("carl@test.ua", "hash"));
    }

    @Test
    @DisplayName("Активне запрошення знаходиться незалежно від того, хто його надіслав")
    void activeInvitationIsFoundInBothDirections() {
        entityManager.persist(new Invitation(anna, bohdan));
        entityManager.flush();

        assertThat(invitationRepository.findActiveBetween(bohdan.getId(), anna.getId())).hasSize(1);
        assertThat(invitationRepository.findActiveBetween(anna.getId(), carl.getId())).isEmpty();
    }

    @Test
    @DisplayName("Відхилені та скасовані запрошення не вважаються активними")
    void rejectedAndCancelledAreNotActive() {
        Invitation rejected = new Invitation(anna, bohdan);
        rejected.reject();
        Invitation cancelled = new Invitation(bohdan, anna);
        cancelled.cancel();
        entityManager.persist(rejected);
        entityManager.persist(cancelled);
        entityManager.flush();

        assertThat(invitationRepository.findActiveBetween(anna.getId(), bohdan.getId())).isEmpty();
        assertThat(invitationRepository.findAllBetween(anna.getId(), bohdan.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Останнім вважається запрошення з найпізнішим часом надсилання")
    void latestInvitationIsReturnedFirst() {
        Invitation older = new Invitation(anna, bohdan);
        older.reject();
        ReflectionTestUtils.setField(older, "sentAt", LocalDateTime.now().minusHours(1));
        Invitation newer = new Invitation(bohdan, anna);
        entityManager.persist(older);
        entityManager.persist(newer);
        entityManager.flush();

        assertThat(invitationRepository.findLatestBetween(anna.getId(), bohdan.getId()))
                .hasValueSatisfying(latest -> assertThat(latest.getId()).isEqualTo(newer.getId()));
    }

    @Test
    @DisplayName("Для видалення облікового запису знаходяться і надіслані, і отримані запрошення")
    void findAllInvolvingReturnsSentAndReceived() {
        entityManager.persist(new Invitation(anna, bohdan));
        entityManager.persist(new Invitation(carl, anna));
        entityManager.persist(new Invitation(bohdan, carl));
        entityManager.flush();

        assertThat(invitationRepository.findAllInvolving(anna.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Видалення запрошення видаляє й результат сумісності (каскад)")
    void deletingInvitationDeletesCompatibilityResult() {
        Invitation invitation = new Invitation(anna, bohdan);
        invitation.accept(new DestinyMatrixResult(80, "Висока сумісність"));
        entityManager.persist(invitation);
        entityManager.flush();
        Long resultId = invitation.getDestinyMatrixResult().getId();

        invitationRepository.delete(invitation);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(DestinyMatrixResult.class, resultId)).isNull();
    }
}
