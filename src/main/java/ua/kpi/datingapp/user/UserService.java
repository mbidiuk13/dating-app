package ua.kpi.datingapp.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.datingapp.audit.AuditLog;
import ua.kpi.datingapp.invitation.InvitationService;
import ua.kpi.datingapp.profile.Profile;
import ua.kpi.datingapp.profile.ProfileRepository;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Бізнес-логіка облікових записів. Залежить від абстракції PasswordEncoder,
 * а не від конкретного алгоритму (принцип DIP): BCrypt підключається в SecurityConfig.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final InvitationService invitationService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLog auditLog;

    public UserService(UserRepository userRepository,
                       ProfileRepository profileRepository,
                       InvitationService invitationService,
                       PasswordEncoder passwordEncoder,
                       AuditLog auditLog) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.invitationService = invitationService;
        this.passwordEncoder = passwordEncoder;
        this.auditLog = auditLog;
    }

    /**
     * Реєстрація (US-01): обліковий запис і анкета створюються в одній транзакції,
     * бо за діаграмою класів анкета є частиною користувача (композиція).
     */
    @Transactional
    public User register(String email, String rawPassword, String firstName, LocalDate birthDate) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }
        User user = userRepository.save(new User(normalizedEmail, passwordEncoder.encode(rawPassword)));
        profileRepository.save(new Profile(user, firstName.trim(), birthDate));
        return user;
    }

    /**
     * Видалення анкети (ЛР1, функція «Ведення анкети»). Анкета є частиною облікового запису
     * (композиція), тому видаляються обидва, а також запрошення користувача з результатами
     * сумісності. Ключові слова залишаються в довіднику. Усе — в одній транзакції:
     * якщо будь-який крок не вдасться, не видалиться нічого (НФВ надійності).
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Користувача #" + userId + " не знайдено"));

        invitationService.deleteAllInvolving(userId);
        profileRepository.findByUserId(userId).ifPresent(profileRepository::delete);
        userRepository.delete(user);
        auditLog.accountDeleted(userId);
    }

    @Transactional(readOnly = true)
    public Long getIdByEmail(String email) {
        return userRepository.findByEmail(normalizeEmail(email))
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Користувача " + email + " не знайдено"));
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
