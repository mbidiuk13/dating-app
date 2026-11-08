package ua.kpi.datingapp.profile;

/** Знайдена анкета (лише відкрита інформація) та кількість спільних ключових слів із запитом. */
public record SearchHit(ProfilePublicDTO profile, int sharedKeywords) {
}
