package ua.kpi.datingapp.component;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import ua.kpi.datingapp.profile.Keyword;
import ua.kpi.datingapp.profile.Profile;
import ua.kpi.datingapp.profile.ProfileRepository;
import ua.kpi.datingapp.profile.SearchMatch;
import ua.kpi.datingapp.user.User;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Компонентні тести шару доступу до даних: справжні JPQL-запити ProfileRepository
 * виконуються на вбудованій БД H2 (режим PostgreSQL). Контролери й сервіси не піднімаються.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("ProfileRepository + БД: запити пошуку анкет")
class ProfileRepositoryComponentTest {

    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private ProfileRepository profileRepository;

    private final Map<String, Keyword> keywords = new HashMap<>();
    private Long viewerId;

    @BeforeEach
    void createViewer() {
        viewerId = persistProfile("viewer@test.ua", "Глядач", 25, false, true, "кава").getUser().getId();
    }

    @Test
    @DisplayName("Пошук рахує спільні ключові слова й не показує анкету того, хто шукає")
    void findMatchesCountsSharedKeywordsAndExcludesViewer() {
        persistProfile("anna@test.ua", "Анна", 25, false, true, "кава", "джаз");
        persistProfile("oleh@test.ua", "Олег", 30, false, true, "кава");
        persistProfile("ira@test.ua", "Іра", 22, false, true, "спорт");
        entityManager.flush();

        Map<String, Long> shared = sharedByName(
                profileRepository.findMatches(Set.of("кава", "джаз"), viewerId, EARLIEST, TODAY, false));

        assertThat(shared).containsOnly(Map.entry("Анна", 2L), Map.entry("Олег", 1L));
    }

    @Test
    @DisplayName("Анкети деактивованих користувачів у пошук не потрапляють")
    void deactivatedUsersAreNotFound() {
        persistProfile("anna@test.ua", "Анна", 25, false, true, "кава");
        persistProfile("blocked@test.ua", "Заблокований", 25, false, false, "кава");
        entityManager.flush();

        assertThat(sharedByName(profileRepository.findMatches(Set.of("кава"), viewerId, EARLIEST, TODAY, false)))
                .containsOnlyKeys("Анна");
    }

    @Test
    @DisplayName("З фільтром за віком анкети з прихованим віком не беруть участі")
    void ageFilterSkipsProfilesWithHiddenAge() {
        persistProfile("olena@test.ua", "Олена", 26, false, true, "кава");
        persistProfile("hidden@test.ua", "Прихована", 26, true, true, "кава");
        entityManager.flush();

        assertThat(sharedByName(profileRepository.findMatches(Set.of("кава"), viewerId, EARLIEST, TODAY, true)))
                .containsOnlyKeys("Олена");
        assertThat(sharedByName(profileRepository.findMatches(Set.of("кава"), viewerId, EARLIEST, TODAY, false)))
                .containsOnlyKeys("Олена", "Прихована");
    }

    @Test
    @DisplayName("Діапазон дат народження застосовується включно з межами")
    void birthDateRangeIsInclusive() {
        persistProfile("young@test.ua", "Молода", 20, false, true, "кава");
        persistProfile("old@test.ua", "Старший", 45, false, true, "кава");
        entityManager.flush();

        LocalDate bornFrom = TODAY.minusYears(20);
        assertThat(sharedByName(profileRepository.findMatches(Set.of("кава"), viewerId, bornFrom, TODAY, true)))
                .containsOnlyKeys("Молода");
    }

    @Test
    @DisplayName("Анкети для показу завантажуються разом із власниками одним запитом")
    void findAllWithUserLoadsOwners() {
        Long annaProfileId = persistProfile("anna@test.ua", "Анна", 25, false, true, "кава").getId();
        Long olehProfileId = persistProfile("oleh@test.ua", "Олег", 30, false, true, "кава").getId();
        entityManager.flush();
        entityManager.clear();

        List<Profile> profiles = profileRepository.findAllWithUserByIdIn(List.of(annaProfileId, olehProfileId));

        assertThat(profiles).hasSize(2);
        assertThat(profiles).allSatisfy(profile -> assertThat(Hibernate.isInitialized(profile.getUser())).isTrue());
    }

    private Profile persistProfile(String email, String name, int age, boolean agePrivate, boolean active, String... tags) {
        User user = new User(email, "hash");
        if (!active) {
            user.deactivate();
        }
        entityManager.persist(user);

        Profile profile = new Profile(user, name, TODAY.minusYears(age));
        profile.updateVisibility(false, agePrivate);
        Set<Keyword> profileKeywords = new HashSet<>();
        for (String tag : tags) {
            profileKeywords.add(keywords.computeIfAbsent(tag, t -> entityManager.persist(new Keyword(t))));
        }
        profile.replaceKeywords(profileKeywords);
        return entityManager.persist(profile);
    }

    private static Map<String, Long> sharedByName(List<SearchMatch> matches) {
        return matches.stream().collect(Collectors.toMap(SearchMatch::firstName, SearchMatch::sharedKeywords));
    }
}
