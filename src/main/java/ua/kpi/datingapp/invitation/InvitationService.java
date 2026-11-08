package ua.kpi.datingapp.invitation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.datingapp.compatibility.DestinyMatrixResult;
import ua.kpi.datingapp.compatibility.DestinyMatrixService;
import ua.kpi.datingapp.profile.Profile;
import ua.kpi.datingapp.profile.ProfileRepository;
import ua.kpi.datingapp.user.User;
import ua.kpi.datingapp.user.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Обмін запрошеннями — сценарій UC-1 (ЛР1) і діаграма послідовностей (ЛР2).
 * Залежить від інтерфейсу DestinyMatrixService, а не від конкретного алгоритму (DIP).
 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final DestinyMatrixService destinyMatrixService;

    public InvitationService(InvitationRepository invitationRepository,
                             UserRepository userRepository,
                             ProfileRepository profileRepository,
                             DestinyMatrixService destinyMatrixService) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.destinyMatrixService = destinyMatrixService;
    }

    /** Кроки 3–10: перевірка дублікатів і створення запрошення зі статусом PENDING. */
    @Transactional
    public InvitationDTO sendInvitation(Long senderId, Long receiverId) {
        if (senderId.equals(receiverId)) {
            throw new InvitationException("Не можна надіслати запрошення самому собі");
        }
        if (!invitationRepository.findActiveBetween(senderId, receiverId).isEmpty()) {
            throw new InvitationException("Між вами вже є активне запрошення");
        }
        User sender = findUser(senderId);
        User receiver = findUser(receiverId);

        Invitation saved = invitationRepository.save(new Invitation(sender, receiver));
        return InvitationDTO.from(saved, senderId);
    }

    /**
     * Кроки 14–24: PENDING → ACCEPTED, розрахунок матриці долі та збереження
     * результату в одній транзакції (НФВ надійності: атомарність).
     */
    @Transactional
    public InvitationDTO acceptInvitation(Long invitationId, Long currentUserId) {
        Invitation invitation = findInvitation(invitationId);
        requireReceiver(invitation, currentUserId);
        if (!invitation.isPending()) {
            throw new InvitationException("Запрошення вже оброблено");
        }

        // Крок 19: дати народження обох користувачів беруться з їхніх анкет
        DestinyMatrixResult result = destinyMatrixService.calculateCompatibility(
                birthDateOf(invitation.getSender()),
                birthDateOf(invitation.getReceiver()));
        invitation.accept(result);

        return InvitationDTO.from(invitationRepository.save(invitation), currentUserId);
    }

    @Transactional
    public InvitationDTO rejectInvitation(Long invitationId, Long currentUserId) {
        Invitation invitation = findInvitation(invitationId);
        requireReceiver(invitation, currentUserId);
        invitation.reject();
        return InvitationDTO.from(invitationRepository.save(invitation), currentUserId);
    }

    @Transactional
    public InvitationDTO cancelInvitation(Long invitationId, Long currentUserId) {
        Invitation invitation = findInvitation(invitationId);
        if (!invitation.getSender().getId().equals(currentUserId)) {
            throw new InvitationException("Скасувати запрошення може лише відправник");
        }
        invitation.cancel();
        return InvitationDTO.from(invitationRepository.save(invitation), currentUserId);
    }

    @Transactional(readOnly = true)
    public List<InvitationDTO> getSent(Long userId) {
        return invitationRepository.findBySenderIdOrderBySentAtDesc(userId).stream()
                .map(invitation -> InvitationDTO.from(invitation, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationDTO> getReceived(Long userId) {
        return invitationRepository.findByReceiverIdOrderBySentAtDesc(userId).stream()
                .map(invitation -> InvitationDTO.from(invitation, userId))
                .toList();
    }

    /** Взаємний доступ до закритої інформації є лише після ACCEPTED. */
    @Transactional(readOnly = true)
    public boolean hasMutualAccess(Long userA, Long userB) {
        return !invitationRepository
                .findBetweenWithStatuses(userA, userB, List.of(InvitationStatus.ACCEPTED))
                .isEmpty();
    }

    @Transactional(readOnly = true)
    public Optional<InvitationDTO> findLatestBetween(Long viewerUserId, Long otherUserId) {
        return invitationRepository.findLatestBetween(viewerUserId, otherUserId)
                .map(invitation -> InvitationDTO.from(invitation, viewerUserId));
    }

    /**
     * Видаляє всі запрошення користувача разом з результатами сумісності
     * (каскад Invitation → DestinyMatrixResult). Викликається під час видалення облікового запису.
     */
    @Transactional
    public void deleteAllInvolving(Long userId) {
        invitationRepository.deleteAll(invitationRepository.findAllInvolving(userId));
    }

    private void requireReceiver(Invitation invitation, Long currentUserId) {
        if (!invitation.getReceiver().getId().equals(currentUserId)) {
            throw new InvitationException("Відповісти на запрошення може лише його отримувач");
        }
    }

    private LocalDate birthDateOf(User user) {
        return profileRepository.findByUserId(user.getId())
                .map(Profile::getBirthDate)
                .orElseThrow(() -> new InvitationException("Анкету користувача не знайдено"));
    }

    private Invitation findInvitation(Long invitationId) {
        return invitationRepository.findById(invitationId)
                .orElseThrow(() -> new InvitationException("Запрошення не знайдено"));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvitationException("Користувача не знайдено"));
    }
}
