package ua.kpi.datingapp.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.datingapp.invitation.InvitationDTO;
import ua.kpi.datingapp.invitation.InvitationService;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;

/**
 * Бізнес-логіка анкет. Головне правило приватності (US-02, ЛР2 розділ 5):
 * закрита інформація формується на бекенді лише для власника або
 * після взаємного підтвердження запрошення (ACCEPTED).
 */
@Service
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final KeywordRepository keywordRepository;
    private final InvitationService invitationService;

    public ProfileService(ProfileRepository profileRepository,
                          KeywordRepository keywordRepository,
                          InvitationService invitationService) {
        this.profileRepository = profileRepository;
        this.keywordRepository = keywordRepository;
        this.invitationService = invitationService;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileData> findOwnProfileData(Long userId) {
        return profileRepository.findByUserId(userId).map(Profile::toProfileData);
    }

    @Transactional
    public void saveOwnProfile(Long userId, ProfileData data) {
        Profile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ProfileNotFoundException(userId));

        profile.updateOpenData(data.firstName(), data.openDescription(), data.openPhotos());
        profile.updateClosedData(data.closedContactInfo(), data.closedPrivateDetails());
        profile.replaceKeywords(resolveKeywords(data.keywords()));

        profileRepository.save(profile);
    }

    /** Перегляд анкети з перевіркою прав доступу до закритої частини. */
    @Transactional(readOnly = true)
    public ProfileView viewProfile(Long viewerUserId, Long ownerUserId) {
        Profile profile = profileRepository.findByUserId(ownerUserId)
                .orElseThrow(() -> new ProfileNotFoundException(ownerUserId));

        boolean ownProfile = ownerUserId.equals(viewerUserId);
        boolean privateAccess = ownProfile || invitationService.hasMutualAccess(viewerUserId, ownerUserId);
        InvitationDTO invitation = ownProfile
                ? null
                : invitationService.findLatestBetween(viewerUserId, ownerUserId).orElse(null);

        return new ProfileView(
                profile.getPublicInfo(),
                privateAccess ? profile.getPrivateInfo() : null,
                ownProfile,
                invitation);
    }

    /** Пошук за ключовими словами: у результатах лише відкрита інформація (US-03). */
    @Transactional(readOnly = true)
    public List<ProfilePublicDTO> search(Long viewerUserId, Collection<String> rawTags) {
        Set<String> tags = new HashSet<>();
        for (String raw : rawTags) {
            String tag = Keyword.normalize(raw);
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        if (tags.isEmpty()) {
            return List.of();
        }
        return profileRepository.searchByKeywords(tags, viewerUserId).stream()
                .map(Profile::getPublicInfo)
                .toList();
    }

    /** Імена для списків (наприклад, запрошень): userId → firstName. */
    @Transactional(readOnly = true)
    public Map<Long, String> getFirstNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return profileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(profile -> profile.getUser().getId(), Profile::getFirstName));
    }

    private Set<Keyword> resolveKeywords(Collection<String> rawTags) {
        Set<Keyword> result = new HashSet<>();
        if (rawTags == null) {
            return result;
        }
        for (String raw : rawTags) {
            String tag = Keyword.normalize(raw);
            if (tag.isEmpty() || tag.length() > Keyword.MAX_TAG_LENGTH) {
                continue;
            }
            Keyword keyword = keywordRepository.findByTag(tag)
                    .orElseGet(() -> keywordRepository.save(new Keyword(tag)));
            result.add(keyword);
        }
        return result;
    }
}
