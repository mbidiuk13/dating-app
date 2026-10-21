package ua.kpi.datingapp.profile;

/** Закрита інформація анкети. Формується лише за наявності доступу. */
public record ProfilePrivateDTO(
        String closedContactInfo,
        String closedPrivateDetails) {
}
