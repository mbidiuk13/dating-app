package ua.kpi.datingapp.invitation;

import ua.kpi.datingapp.compatibility.DestinyMatrixResult;

import java.time.LocalDateTime;

/**
 * Запрошення з погляду конкретного користувача.
 *
 * @param counterpartUserId друга сторона запрошення
 * @param incoming          true — запрошення отримане, false — надіслане
 */
public record InvitationDTO(
        Long id,
        Long counterpartUserId,
        boolean incoming,
        InvitationStatus status,
        LocalDateTime sentAt,
        LocalDateTime statusChangedAt,
        Integer compatibilityPercentage,
        String compatibilityDescription) {

    static InvitationDTO from(Invitation invitation, Long viewerUserId) {
        boolean incoming = invitation.getReceiver().getId().equals(viewerUserId);
        Long counterpart = incoming ? invitation.getSender().getId() : invitation.getReceiver().getId();
        DestinyMatrixResult result = invitation.getDestinyMatrixResult();
        return new InvitationDTO(
                invitation.getId(),
                counterpart,
                incoming,
                invitation.getStatus(),
                invitation.getSentAt(),
                invitation.getStatusChangedAt(),
                result == null ? null : result.getCompatibilityPercentage(),
                result == null ? null : result.getCompatibilityDescription());
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == InvitationStatus.ACCEPTED;
    }

    /** Чи можна надіслати нове запрошення після цього (відхилене або скасоване). */
    public boolean isClosed() {
        return status == InvitationStatus.REJECTED || status == InvitationStatus.CANCELLED;
    }
}
