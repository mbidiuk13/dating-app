package ua.kpi.datingapp.profile;

import java.util.Locale;

/** Порядок результатів пошуку (ЛР1, низький пріоритет: сортування/фільтрація). */
public enum SearchSort {

    /** Спочатку анкети з найбільшою кількістю спільних ключових слів. */
    RELEVANCE,
    /** Спочатку наймолодші. */
    YOUNGEST,
    /** Спочатку найстарші. */
    OLDEST;

    /** Значення з адреси (?sort=youngest); невідоме або порожнє — RELEVANCE. */
    public static SearchSort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return RELEVANCE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RELEVANCE;
        }
    }

    public String param() {
        return name().toLowerCase(Locale.ROOT);
    }
}
