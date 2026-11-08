package ua.kpi.datingapp.profile;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Сортування та фільтр пошуку (ЛР1: «сортування/фільтрація результатів пошуку»). */
class ProfileSearchSortTest {

    private static final SearchMatch YOUNG_ONE_TAG = match(1L, "Ira", 2005, false, 1);
    private static final SearchMatch OLD_TWO_TAGS = match(2L, "Oleh", 1980, false, 2);
    private static final SearchMatch MIDDLE_ONE_TAG = match(3L, "Anna", 1995, false, 1);
    private static final SearchMatch HIDDEN_AGE = match(4L, "Bohdan", 2007, true, 1);

    private static final List<SearchMatch> ALL = List.of(YOUNG_ONE_TAG, OLD_TWO_TAGS, MIDDLE_ONE_TAG, HIDDEN_AGE);

    @Test
    void relevanceShowsMoreCommonInterestsFirst() {
        assertThat(sorted(SearchSort.RELEVANCE)).containsExactly(OLD_TWO_TAGS, MIDDLE_ONE_TAG, HIDDEN_AGE, YOUNG_ONE_TAG);
    }

    @Test
    void youngestFirstPutsHiddenAgeLastSoOrderDoesNotRevealIt() {
        assertThat(sorted(SearchSort.YOUNGEST)).containsExactly(YOUNG_ONE_TAG, MIDDLE_ONE_TAG, OLD_TWO_TAGS, HIDDEN_AGE);
    }

    @Test
    void oldestFirstAlsoPutsHiddenAgeLast() {
        assertThat(sorted(SearchSort.OLDEST)).containsExactly(OLD_TWO_TAGS, MIDDLE_ONE_TAG, YOUNG_ONE_TAG, HIDDEN_AGE);
    }

    @Test
    void unknownSortParameterFallsBackToRelevance() {
        assertThat(SearchSort.fromParam("random")).isEqualTo(SearchSort.RELEVANCE);
        assertThat(SearchSort.fromParam("youngest")).isEqualTo(SearchSort.YOUNGEST);
        assertThat(SearchSort.fromParam(null)).isEqualTo(SearchSort.RELEVANCE);
    }

    @Test
    void ageFilterIsClampedAndSwappedWhenNeeded() {
        SearchCriteria criteria = new SearchCriteria(Set.of("кава"), null, 60, 5);

        assertThat(criteria.minAge()).isEqualTo(18);
        assertThat(criteria.maxAge()).isEqualTo(60);
        assertThat(criteria.sort()).isEqualTo(SearchSort.RELEVANCE);
        assertThat(criteria.hasAgeFilter()).isTrue();
        assertThat(SearchCriteria.byTags(Set.of("кава")).hasAgeFilter()).isFalse();
    }

    private static List<SearchMatch> sorted(SearchSort sort) {
        return ALL.stream().sorted(ProfileService.comparatorFor(sort)).toList();
    }

    private static SearchMatch match(Long id, String name, int birthYear, boolean agePrivate, long sharedKeywords) {
        return new SearchMatch(id, name, LocalDate.of(birthYear, 6, 1), agePrivate, sharedKeywords);
    }
}
