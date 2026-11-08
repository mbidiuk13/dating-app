package ua.kpi.datingapp.profile;

import java.util.List;

/**
 * Закрита інформація анкети. Формується лише за наявності доступу.
 *
 * @param hiddenPhotoUrls фото, які власник показує лише після підтвердження (або порожній список)
 * @param hiddenAge       вік, якщо власник його приховав, інакше null
 */
public record ProfilePrivateDTO(
        String closedContactInfo,
        String closedPrivateDetails,
        List<String> hiddenPhotoUrls,
        Integer hiddenAge) {

    public ProfilePrivateDTO(String closedContactInfo, String closedPrivateDetails) {
        this(closedContactInfo, closedPrivateDetails, List.of(), null);
    }
}
