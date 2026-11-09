package ua.kpi.datingapp.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.datingapp.audit.AuditLog;
import ua.kpi.datingapp.invitation.InvitationService;
import ua.kpi.datingapp.profile.Profile;
import ua.kpi.datingapp.profile.ProfileRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Юніт-тести облікових записів (US-01, видалення анкети) без бази даних. */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService: реєстрація та видалення облікового запису")
class UserServiceTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(2001, 4, 12);

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private InvitationService invitationService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLog auditLog;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Реєстрація: email нормалізується, пароль хешується, анкета створюється разом з користувачем")
    void registerNormalizesEmailHashesPasswordAndCreatesProfile() {
        when(userRepository.existsByEmail("anna@test.ua")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.register("  Anna@Test.UA ", "password123", "  Анна ", BIRTH_DATE);

        assertThat(created.getEmail()).isEqualTo("anna@test.ua");
        assertThat(created.getPasswordHash()).isEqualTo("bcrypt-hash").doesNotContain("password123");

        ArgumentCaptor<Profile> profile = ArgumentCaptor.forClass(Profile.class);
        verify(profileRepository).save(profile.capture());
        assertThat(profile.getValue().getUser()).isSameAs(created);
        assertThat(profile.getValue().getFirstName()).isEqualTo("Анна");
        assertThat(profile.getValue().getBirthDate()).isEqualTo(BIRTH_DATE);
    }

    @Test
    @DisplayName("Повторна реєстрація з тим самим email відхиляється, нічого не зберігається")
    void duplicateEmailIsRejected() {
        when(userRepository.existsByEmail("anna@test.ua")).thenReturn(true);

        assertThatThrownBy(() -> userService.register("ANNA@test.ua", "password123", "Анна", BIRTH_DATE))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, profileRepository);
    }

    @Test
    @DisplayName("Видалення: спершу запрошення, потім анкета, потім користувач, потім запис у журнал")
    void deleteAccountRemovesEverythingInOrder() {
        User anna = userWithId(1L);
        Profile profile = new Profile(anna, "Анна", BIRTH_DATE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(anna));
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        userService.deleteAccount(1L);

        InOrder order = inOrder(invitationService, profileRepository, userRepository, auditLog);
        order.verify(invitationService).deleteAllInvolving(1L);
        order.verify(profileRepository).delete(profile);
        order.verify(userRepository).delete(anna);
        order.verify(auditLog).accountDeleted(1L);
    }

    @Test
    @DisplayName("Видалення неіснуючого користувача нічого не змінює")
    void deleteUnknownAccountChangesNothing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAccount(99L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(invitationService, profileRepository, auditLog);
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Пошук ідентифікатора не залежить від регістру та пробілів у email")
    void getIdByEmailIgnoresCaseAndSpaces() {
        when(userRepository.findByEmail("anna@test.ua")).thenReturn(Optional.of(userWithId(5L)));

        assertThat(userService.getIdByEmail(" ANNA@test.ua ")).isEqualTo(5L);
    }

    private static User userWithId(Long id) {
        User user = new User("user" + id + "@test.ua", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
