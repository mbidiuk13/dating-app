package ua.kpi.datingapp.profile;

import java.time.LocalDate;

/**
 * Рядок результату SQL-запиту пошуку: анкета та кількість її тегів, що збіглися із запитом.
 * Вибираються лише ці поля, без повних анкет, тому запит швидкий навіть на 10 000 анкет.
 */
public record SearchMatch(Long profileId, String firstName, LocalDate birthDate,
                          Boolean agePrivate, Long sharedKeywords) {
}
