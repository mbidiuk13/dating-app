package ua.kpi.datingapp.profile;

import java.util.Set;

/** Дані для створення або редагування власної анкети (вхід сервісу, без прив'язки до веб-форми). */
public record ProfileData(
        String firstName,
        String openDescription,
        String openPhotos,
        String closedContactInfo,
        String closedPrivateDetails,
        Set<String> keywords) {
}
