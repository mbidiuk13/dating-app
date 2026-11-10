package ua.kpi.datingapp.profile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Юніт-тести параметрів пошуку (сортування та фільтр за віком). */
@DisplayName("SearchCriteria і SearchResults: параметри та результат пошуку")
class SearchCriteriaTest {

    @Test
    @DisplayName("Без тегів критерій має порожній набір, а не null")
    void nullTagsBecomeEmptySet() {
        SearchCriteria criteria = new SearchCriteria(null, SearchSort.OLDEST, null, null);

        assertThat(criteria.tags()).isEmpty();
        assertThat(criteria.hasAgeFilter()).isFalse();
    }

    @Test
    @DisplayName("Набір тегів копіюється: зміна вихідного набору не впливає на пошук")
    void tagsAreCopied() {
        Set<String> tags = new HashSet<>(Set.of("кава"));
        SearchCriteria criteria = SearchCriteria.byTags(tags);

        tags.add("джаз");

        assertThat(criteria.tags()).containsExactly("кава");
    }

    @Test
    @DisplayName("Межі віку в допустимому діапазоні не змінюються; лише верхня межа — теж фільтр")
    void validAgeLimitsAreKept() {
        assertThat(new SearchCriteria(Set.of("кава"), null, 25, 30))
                .extracting(SearchCriteria::minAge, SearchCriteria::maxAge)
                .containsExactly(25, 30);

        SearchCriteria onlyMax = new SearchCriteria(Set.of("кава"), SearchSort.YOUNGEST, null, 200);
        assertThat(onlyMax.minAge()).isNull();
        assertThat(onlyMax.maxAge()).isEqualTo(SearchCriteria.MAX_ALLOWED_AGE);
        assertThat(onlyMax.hasAgeFilter()).isTrue();
    }

    @Test
    @DisplayName("Кожен варіант сортування однозначно передається через адресу сторінки")
    void sortParameterRoundTrip() {
        for (SearchSort sort : SearchSort.values()) {
            assertThat(SearchSort.fromParam(sort.param())).isEqualTo(sort);
        }
        assertThat(SearchSort.fromParam(" OLDEST ")).isEqualTo(SearchSort.OLDEST);
        assertThat(SearchSort.fromParam("")).isEqualTo(SearchSort.RELEVANCE);
    }

    @Test
    @DisplayName("Результат позначається обмеженим, коли знайдено більше, ніж показано")
    void resultsKnowWhenTheyAreLimited() {
        SearchHit hit = new SearchHit(
                new ProfilePublicDTO(1L, "Анна", 25, "Опис", List.of(), Set.of("кава")), 1);

        assertThat(new SearchResults(List.of(hit), 3).isLimited()).isTrue();
        assertThat(new SearchResults(List.of(hit), 1).isLimited()).isFalse();
        assertThat(SearchResults.empty().isLimited()).isFalse();
    }
}
