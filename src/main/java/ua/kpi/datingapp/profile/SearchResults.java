package ua.kpi.datingapp.profile;

import java.util.List;

/**
 * Результат пошуку.
 *
 * @param hits       показані анкети (не більше ProfileService.MAX_RESULTS)
 * @param totalFound скільки анкет відповідає запиту загалом
 */
public record SearchResults(List<SearchHit> hits, int totalFound) {

    public static SearchResults empty() {
        return new SearchResults(List.of(), 0);
    }

    public boolean isLimited() {
        return totalFound > hits.size();
    }
}
