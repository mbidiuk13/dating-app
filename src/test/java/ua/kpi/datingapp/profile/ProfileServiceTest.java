package ua.kpi.datingapp.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ua.kpi.datingapp.audit.AuditLog;
import ua.kpi.datingapp.invitation.InvitationService;
import ua.kpi.datingapp.user.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Юніт-тести бізнес-логіки анкет без бази даних: репозиторії, сервіс запрошень
 * і журнал аудиту замінені моками. Перевіряють правило приватності (US-02),
 * пошук (US-03) і збереження анкети.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService: приватність, пошук і збереження анкети")
class ProfileServiceTest {

    private static final Long ANNA_ID = 1L;
    private static final Long BOHDAN_ID = 2L;

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private KeywordRepository keywordRepository;
    @Mock
    private InvitationService invitationService;
    @Mock
    private AuditLog auditLog;

    @InjectMocks
    private ProfileService profileService;

    private Profile annaProfile;

    @BeforeEach
    void setUp() {
        annaProfile = profile(10L, userWithId(ANNA_ID), "Анна");
        annaProfile.updateClosedData("@anna_tg", "Особисте");
    }

    // ---------- Перегляд анкети ----------

    @Test
    @DisplayName("Власник бачить свою закриту частину; перегляд власної анкети не журналюється")
    void ownProfileShowsPrivatePartWithoutAudit() {
        when(profileRepository.findByUserId(ANNA_ID)).thenReturn(Optional.of(annaProfile));

        ProfileView view = profileService.viewProfile(ANNA_ID, ANNA_ID);

        assertThat(view.ownProfile()).isTrue();
        assertThat(view.hasPrivateAccess()).isTrue();
        assertThat(view.invitation()).isNull();
        verifyNoInteractions(auditLog, invitationService);
    }

    @Test
    @DisplayName("Без підтвердженого запрошення закрита частина не передається, перегляд журналюється")
    void strangerWithoutAcceptedInvitationGetsNoPrivatePart() {
        when(profileRepository.findByUserId(ANNA_ID)).thenReturn(Optional.of(annaProfile));
        when(invitationService.hasMutualAccess(BOHDAN_ID, ANNA_ID)).thenReturn(false);
        when(invitationService.findLatestBetween(BOHDAN_ID, ANNA_ID)).thenReturn(Optional.empty());

        ProfileView view = profileService.viewProfile(BOHDAN_ID, ANNA_ID);

        assertThat(view.hasPrivateAccess()).isFalse();
        assertThat(view.privateInfo()).isNull();
        verify(auditLog).profileViewed(BOHDAN_ID, ANNA_ID, false);
    }

    @Test
    @DisplayName("Після взаємного підтвердження закрита частина передається, доступ журналюється")
    void acceptedInvitationOpensPrivatePart() {
        when(profileRepository.findByUserId(ANNA_ID)).thenReturn(Optional.of(annaProfile));
        when(invitationService.hasMutualAccess(BOHDAN_ID, ANNA_ID)).thenReturn(true);
        when(invitationService.findLatestBetween(BOHDAN_ID, ANNA_ID)).thenReturn(Optional.empty());

        ProfileView view = profileService.viewProfile(BOHDAN_ID, ANNA_ID);

        assertThat(view.privateInfo().closedContactInfo()).isEqualTo("@anna_tg");
        verify(auditLog).profileViewed(BOHDAN_ID, ANNA_ID, true);
    }

    @Test
    @DisplayName("Анкета, якої немає, дає ProfileNotFoundException (сторінка 404)")
    void missingProfileThrowsNotFound() {
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.viewProfile(BOHDAN_ID, 99L))
                .isInstanceOf(ProfileNotFoundException.class);
        verifyNoInteractions(auditLog);
    }

    // ---------- Збереження анкети ----------

    @Test
    @DisplayName("Наявні ключові слова перевикористовуються, нові — створюються в довіднику")
    void saveReusesExistingKeywordsAndCreatesNewOnes() {
        when(profileRepository.findByUserId(ANNA_ID)).thenReturn(Optional.of(annaProfile));
        when(keywordRepository.findByTag("кава")).thenReturn(Optional.of(new Keyword("кава")));
        when(keywordRepository.findByTag("джаз")).thenReturn(Optional.empty());
        when(keywordRepository.save(any(Keyword.class))).thenAnswer(invocation -> invocation.getArgument(0));

        profileService.saveOwnProfile(ANNA_ID, new ProfileData("Анна", "Опис", null, "@anna_tg", null,
                Set.of(" Кава ", "джаз"), false, true));

        verify(keywordRepository).save(argThat(keyword -> keyword.getTag().equals("джаз")));
        verify(keywordRepository, never()).save(argThat(keyword -> keyword.getTag().equals("кава")));
        verify(profileRepository).save(annaProfile);
        assertThat(annaProfile.getPublicInfo().keywords()).containsExactly("джаз", "кава");
        assertThat(annaProfile.isAgePrivate()).isTrue();
    }

    @Test
    @DisplayName("Збереження неіснуючої анкети нічого не записує")
    void saveForMissingProfileWritesNothing() {
        when(profileRepository.findByUserId(99L)).thenReturn(Optional.empty());
        ProfileData data = new ProfileData("Хтось", "Опис", null, null, null, Set.of("кава"));

        assertThatThrownBy(() -> profileService.saveOwnProfile(99L, data))
                .isInstanceOf(ProfileNotFoundException.class);
        verify(profileRepository, never()).save(any());
        verifyNoInteractions(keywordRepository);
    }

    // ---------- Пошук ----------

    @Test
    @DisplayName("Запит без жодного коректного тегу не звертається до бази даних")
    void searchWithoutValidTagsDoesNotQueryDatabase() {
        SearchResults results = profileService.search(BOHDAN_ID,
                new SearchCriteria(Set.of("   ", "x".repeat(60)), null, null, null));

        assertThat(results.hits()).isEmpty();
        verifyNoInteractions(profileRepository);
    }

    @Test
    @DisplayName("Фільтр «вік від 20 до 30» перетворюється на правильний діапазон дат народження")
    void ageFilterIsTranslatedIntoBirthDateRange() {
        LocalDate today = LocalDate.now();

        SearchResults results = profileService.search(BOHDAN_ID,
                new SearchCriteria(Set.of("Кава"), SearchSort.RELEVANCE, 20, 30));

        assertThat(results.hits()).isEmpty();
        verify(profileRepository).findMatches(
                eq(Set.of("кава")),
                eq(BOHDAN_ID),
                eq(today.minusYears(31).plusDays(1)),
                eq(today.minusYears(20)),
                eq(true));
        verify(profileRepository, never()).findAllWithUserByIdIn(any());
    }

    @Test
    @DisplayName("Показується не більше 50 анкет, найрелевантніші першими")
    void searchShowsAtMostFiftyMostRelevantProfiles() {
        List<SearchMatch> matches = new ArrayList<>();
        for (long id = 1; id <= 60; id++) {
            matches.add(new SearchMatch(id, "Користувач" + id, LocalDate.of(2000, 1, 1), false, id));
        }
        when(profileRepository.findMatches(anyCollection(), anyLong(), any(), any(), anyBoolean()))
                .thenReturn(matches);
        when(profileRepository.findAllWithUserByIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(id -> profile(id, userWithId(100 + id), "Користувач" + id))
                    .toList();
        });

        SearchResults results = profileService.search(BOHDAN_ID, SearchCriteria.byTags(Set.of("кава")));

        assertThat(results.hits()).hasSize(ProfileService.MAX_RESULTS);
        assertThat(results.totalFound()).isEqualTo(60);
        assertThat(results.isLimited()).isTrue();
        assertThat(results.hits().get(0).sharedKeywords()).isEqualTo(60);
        assertThat(results.hits().get(0).profile().ownerId()).isEqualTo(160L);
    }

    @Test
    @DisplayName("Імена для порожнього списку запрошень не запитуються з бази")
    void firstNamesOfEmptyListDoNotQueryDatabase() {
        assertThat(profileService.getFirstNames(List.of())).isEmpty();
        verifyNoInteractions(profileRepository);
    }

    private static Profile profile(Long id, User owner, String firstName) {
        Profile profile = new Profile(owner, firstName, LocalDate.of(2000, 5, 10));
        ReflectionTestUtils.setField(profile, "id", id);
        return profile;
    }

    private static User userWithId(Long id) {
        User user = new User("user" + id + "@test.ua", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
