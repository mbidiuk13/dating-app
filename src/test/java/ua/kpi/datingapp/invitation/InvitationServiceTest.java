package ua.kpi.datingapp.invitation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.datingapp.compatibility.DestinyMatrixResult;
import ua.kpi.datingapp.compatibility.DestinyMatrixService;
import ua.kpi.datingapp.profile.Profile;
import ua.kpi.datingapp.profile.ProfileRepository;
import ua.kpi.datingapp.user.User;
import ua.kpi.datingapp.user.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Модульні тести сценарію UC-1 без бази даних: репозиторії замінені моками. */
@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    private static final Long ALICE_ID = 1L;
    private static final Long BOB_ID = 2L;

    @Mock
    private InvitationRepository invitationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private DestinyMatrixService destinyMatrixService;

    @InjectMocks
    private InvitationService invitationService;

    private static final LocalDate ALICE_BIRTH = LocalDate.of(2004, 3, 1);
    private static final LocalDate BOB_BIRTH = LocalDate.of(2003, 7, 15);

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = userWithId(ALICE_ID, "alice@test.ua");
        bob = userWithId(BOB_ID, "bob@test.ua");
    }

    @Test
    void sendCreatesPendingInvitation() {
        when(invitationRepository.findActiveBetween(ALICE_ID, BOB_ID)).thenReturn(List.of());
        when(userRepository.findById(ALICE_ID)).thenReturn(Optional.of(alice));
        when(userRepository.findById(BOB_ID)).thenReturn(Optional.of(bob));
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(call -> call.getArgument(0));

        InvitationDTO dto = invitationService.sendInvitation(ALICE_ID, BOB_ID);

        assertThat(dto.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(dto.incoming()).isFalse();
        assertThat(dto.counterpartUserId()).isEqualTo(BOB_ID);
    }

    @Test
    void cannotInviteYourself() {
        assertThatThrownBy(() -> invitationService.sendInvitation(ALICE_ID, ALICE_ID))
                .isInstanceOf(InvitationException.class);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void cannotSendDuplicateWhileInvitationIsActive() {
        when(invitationRepository.findActiveBetween(ALICE_ID, BOB_ID))
                .thenReturn(List.of(new Invitation(alice, bob)));

        assertThatThrownBy(() -> invitationService.sendInvitation(ALICE_ID, BOB_ID))
                .isInstanceOf(InvitationException.class)
                .hasMessageContaining("активне");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void acceptCalculatesCompatibilityAndOpensAccess() {
        Invitation invitation = new Invitation(alice, bob);
        DestinyMatrixResult result = new DestinyMatrixResult(77, "Висока сумісність");
        when(invitationRepository.findById(10L)).thenReturn(Optional.of(invitation));
        when(profileRepository.findByUserId(ALICE_ID)).thenReturn(Optional.of(new Profile(alice, "Аліса", ALICE_BIRTH)));
        when(profileRepository.findByUserId(BOB_ID)).thenReturn(Optional.of(new Profile(bob, "Богдан", BOB_BIRTH)));
        when(destinyMatrixService.calculateCompatibility(ALICE_BIRTH, BOB_BIRTH)).thenReturn(result);
        when(invitationRepository.save(invitation)).thenReturn(invitation);

        InvitationDTO dto = invitationService.acceptInvitation(10L, BOB_ID);

        assertThat(dto.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(dto.compatibilityPercentage()).isEqualTo(77);
        assertThat(dto.counterpartUserId()).isEqualTo(ALICE_ID);
    }

    @Test
    void onlyReceiverCanAccept() {
        Invitation invitation = new Invitation(alice, bob);
        when(invitationRepository.findById(10L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.acceptInvitation(10L, ALICE_ID))
                .isInstanceOf(InvitationException.class);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void onlySenderCanCancel() {
        Invitation invitation = new Invitation(alice, bob);
        when(invitationRepository.findById(10L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.cancelInvitation(10L, BOB_ID))
                .isInstanceOf(InvitationException.class);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    private static User userWithId(Long id, String email) {
        User user = new User(email, "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
