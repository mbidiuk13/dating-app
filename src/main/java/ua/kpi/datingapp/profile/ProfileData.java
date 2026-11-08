package ua.kpi.datingapp.profile;

import java.util.Set;

/**
 * Дані для створення або редагування власної анкети (вхід сервісу, без прив'язки до веб-форми).
 *
 * @param photosPrivate показувати фото лише після взаємного підтвердження запрошення
 * @param agePrivate    показувати вік лише після взаємного підтвердження запрошення
 */
public record ProfileData(
        String firstName,
        String openDescription,
        String openPhotos,
        String closedContactInfo,
        String closedPrivateDetails,
        Set<String> keywords,
        boolean photosPrivate,
        boolean agePrivate) {

    /** Анкета без прихованих фото та віку. */
    public ProfileData(String firstName, String openDescription, String openPhotos,
                       String closedContactInfo, String closedPrivateDetails, Set<String> keywords) {
        this(firstName, openDescription, openPhotos, closedContactInfo, closedPrivateDetails, keywords, false, false);
    }
}
