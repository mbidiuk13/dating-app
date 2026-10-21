package ua.kpi.datingapp.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Адаптер між нашою сутністю User та Spring Security: під час входу
 * завантажує користувача з PostgreSQL за email.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(UserService.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Невірний email або пароль"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .roles("USER")
                .build();
    }
}
