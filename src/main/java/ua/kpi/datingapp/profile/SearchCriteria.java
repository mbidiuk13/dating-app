package ua.kpi.datingapp.profile;

import java.util.Set;

/**
 * Параметри пошуку анкет.
 *
 * @param tags   нормалізовані ключові слова
 * @param minAge мінімальний вік включно або null
 * @param maxAge максимальний вік включно або null
 */
public record SearchCriteria(Set<String> tags, SearchSort sort, Integer minAge, Integer maxAge) {

    public static final int MIN_ALLOWED_AGE = 18;
    public static final int MAX_ALLOWED_AGE = 120;

    public SearchCriteria {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        sort = sort == null ? SearchSort.RELEVANCE : sort;
        minAge = clamp(minAge);
        maxAge = clamp(maxAge);
        if (minAge != null && maxAge != null && minAge > maxAge) {
            Integer swap = minAge;
            minAge = maxAge;
            maxAge = swap;
        }
    }

    public static SearchCriteria byTags(Set<String> tags) {
        return new SearchCriteria(tags, SearchSort.RELEVANCE, null, null);
    }

    public boolean hasAgeFilter() {
        return minAge != null || maxAge != null;
    }

    private static Integer clamp(Integer age) {
        if (age == null) {
            return null;
        }
        return Math.max(MIN_ALLOWED_AGE, Math.min(MAX_ALLOWED_AGE, age));
    }
}
