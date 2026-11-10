package ua.kpi.datingapp.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Юніт-тести адаптера між сутністю User і Spring Security (вхід у систему, US-01). */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppUserDetailsService: завантаження користувача під час входу")
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService service;

    @Test
    @DisplayName("Користувач знаходиться за email без урахування регістру й отримує роль USER")
    void loadsUserByNormalizedEmail() {
        when(userRepository.findByEmail("anna@test.ua")).thenReturn(Optional.of(new User("anna@test.ua", "bcrypt-hash")));

        UserDetails details = service.loadUserByUsername(" Anna@Test.ua ");

        assertThat(details.getUsername()).isEqualTo("anna@test.ua");
        assertThat(details.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Деактивований користувач не може увійти")
    void deactivatedUserIsDisabled() {
        User user = new User("anna@test.ua", "bcrypt-hash");
        user.deactivate();
        when(userRepository.findByEmail("anna@test.ua")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("anna@test.ua").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Невідомий email дає загальне повідомлення, що не розкриває, чи існує обліковий запис")
    void unknownEmailGivesGenericMessage() {
        when(userRepository.findByEmail("nobody@test.ua")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@test.ua"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Невірний email або пароль");
    }
}
