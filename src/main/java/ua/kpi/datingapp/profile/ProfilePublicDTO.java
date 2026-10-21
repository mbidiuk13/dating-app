package ua.kpi.datingapp.profile;

import java.util.List;
import java.util.Set;

/** Відкрита інформація анкети. Закритих полів тут немає навмисно. */
public record ProfilePublicDTO(
        Long ownerId,
        String firstName,
        int age,
        String openDescription,
        List<String> photoUrls,
        Set<String> keywords) {
}
