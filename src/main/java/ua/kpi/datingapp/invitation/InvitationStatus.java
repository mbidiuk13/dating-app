package ua.kpi.datingapp.invitation;

/**
 * Стани запрошення (ЛР2, діаграма станів).
 * PENDING — початковий; ACCEPTED, REJECTED, CANCELLED — термінальні.
 */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED;

    /** Дозволені лише переходи зі стану PENDING. */
    public boolean canTransitionTo(InvitationStatus target) {
        return this == PENDING && target != PENDING;
    }
}
