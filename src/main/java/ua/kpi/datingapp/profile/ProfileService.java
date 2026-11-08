package ua.kpi.datingapp.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.datingapp.audit.AuditLog;
import ua.kpi.datingapp.invitation.InvitationDTO;
import ua.kpi.datingapp.invitation.InvitationService;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Бізнес-логіка анкет. Головне правило приватності (US-02, ЛР2 розділ 5):
 * закрита інформація формується на бекенді лише для власника або
 * після взаємного підтвердження запрошення (ACCEPTED).
 */
@Service
public class ProfileService {

    /** Скільки анкет показується на сторінці пошуку (НФВ продуктивності: сторінка до 2 с). */
    public static final int MAX_RESULTS = 50;

    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private final ProfileRepository profileRepository;
    private final KeywordRepository keywordRepository;
    private final InvitationService invitationService;
    private final AuditLog auditLog;

    public ProfileService(ProfileRepository profileRepository,
                          KeywordRepository keywordRepository,
                          InvitationService invitationService,
                          AuditLog auditLog) {
        this.profileRepository = profileRepository;
        this.keywordRepository = keywordRepository;
        this.invitationService = invitationService;
        this.auditLog = auditLog;
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
        profile.updateVisibility(data.photosPrivate(), data.agePrivate());
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
        InvitationDTO invitation = null;
        if (!ownProfile) {
            // НФВ безпеки: кожен перегляд чужої анкети фіксується в журналі аудиту
            auditLog.profileViewed(viewerUserId, ownerUserId, privateAccess);
            invitation = invitationService.findLatestBetween(viewerUserId, ownerUserId).orElse(null);
        }

        return new ProfileView(
                profile.getPublicInfo(),
                privateAccess ? profile.getPrivateInfo() : null,
                ownProfile,
                invitation);
    }

    /** Пошук за ключовими словами без сортування й фільтрів (US-03). */
    @Transactional(readOnly = true)
    public List<ProfilePublicDTO> search(Long viewerUserId, Collection<String> rawTags) {
        return search(viewerUserId, SearchCriteria.byTags(normalizeTags(rawTags))).hits().stream()
                .map(SearchHit::profile)
                .toList();
    }

    /**
     * Пошук за ключовими словами з сортуванням і фільтром за віком.
     * У результатах лише відкрита інформація (US-03, правила приватності ЛР1).
     * Анкети з прихованим віком не потрапляють у фільтр за віком і стоять у кінці
     * при сортуванні за віком, щоб порядок не розкривав їхній вік.
     */
    @Transactional(readOnly = true)
    public SearchResults search(Long viewerUserId, SearchCriteria criteria) {
        Set<String> tags = normalizeTags(criteria.tags());
        if (tags.isEmpty()) {
            return SearchResults.empty();
        }

        LocalDate today = LocalDate.now();
        // вік ≤ maxAge ⇔ народився пізніше, ніж (maxAge + 1) років тому
        LocalDate bornFrom = criteria.maxAge() == null
                ? EARLIEST_BIRTH_DATE
                : today.minusYears(criteria.maxAge() + 1L).plusDays(1);
        // вік ≥ minAge ⇔ народився не пізніше, ніж minAge років тому
        LocalDate bornTo = criteria.minAge() == null
                ? today
                : today.minusYears(criteria.minAge());

        List<SearchMatch> matches = profileRepository.findMatches(
                tags, viewerUserId, bornFrom, bornTo, criteria.hasAgeFilter());

        List<SearchMatch> shown = matches.stream()
                .sorted(comparatorFor(criteria.sort()))
                .limit(MAX_RESULTS)
                .toList();
        if (shown.isEmpty()) {
            return SearchResults.empty();
        }

        Map<Long, Profile> profiles = profileRepository
                .findAllWithUserByIdIn(shown.stream().map(SearchMatch::profileId).toList())
                .stream()
                .collect(Collectors.toMap(Profile::getId, Function.identity()));

        List<SearchHit> hits = shown.stream()
                .filter(match -> profiles.containsKey(match.profileId()))
                .map(match -> new SearchHit(
                        profiles.get(match.profileId()).getPublicInfo(),
                        match.sharedKeywords().intValue()))
                .toList();

        return new SearchResults(hits, matches.size());
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

    static Comparator<SearchMatch> comparatorFor(SearchSort sort) {
        Comparator<SearchMatch> byRelevance = Comparator
                .comparing(SearchMatch::sharedKeywords, Comparator.reverseOrder())
                .thenComparing(SearchMatch::firstName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SearchMatch::profileId);

        Comparator<SearchMatch> hiddenAgeLast = Comparator.comparing(
                match -> Boolean.TRUE.equals(match.agePrivate()));

        return switch (sort) {
            case RELEVANCE -> byRelevance;
            // молодший = пізніша дата народження
            case YOUNGEST -> hiddenAgeLast
                    .thenComparing(visibleBirthDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(byRelevance);
            case OLDEST -> hiddenAgeLast
                    .thenComparing(visibleBirthDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(byRelevance);
        };
    }

    /** Дата народження лише для анкет з відкритим віком; для прихованих — null. */
    private static Function<SearchMatch, LocalDate> visibleBirthDate() {
        return match -> Boolean.TRUE.equals(match.agePrivate()) ? null : match.birthDate();
    }

    private static Set<String> normalizeTags(Collection<String> rawTags) {
        Set<String> tags = new HashSet<>();
        if (rawTags == null) {
            return tags;
        }
        for (String raw : rawTags) {
            String tag = Keyword.normalize(raw);
            if (!tag.isEmpty() && tag.length() <= Keyword.MAX_TAG_LENGTH) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private Set<Keyword> resolveKeywords(Collection<String> rawTags) {
        Set<Keyword> result = new HashSet<>();
        for (String tag : normalizeTags(rawTags)) {
            Keyword keyword = keywordRepository.findByTag(tag)
                    .orElseGet(() -> keywordRepository.save(new Keyword(tag)));
            result.add(keyword);
        }
        return result;
    }
}
