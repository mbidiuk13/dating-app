package ua.kpi.datingapp.profile;

import java.util.List;
import java.util.Set;

/**
 * Відкрита інформація анкети. Закритих полів тут немає навмисно.
 *
 * @param age       null, якщо власник приховав вік
 * @param photoUrls порожній список, якщо власник приховав фото
 */
public record ProfilePublicDTO(
        Long ownerId,
        String firstName,
        Integer age,
        String openDescription,
        List<String> photoUrls,
        Set<String> keywords) {

    public boolean hasAge() {
        return age != null;
    }
}
