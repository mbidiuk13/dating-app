package ua.kpi.datingapp.profile;

import ua.kpi.datingapp.invitation.InvitationDTO;

/**
 * Те, що бачить користувач на сторінці анкети.
 *
 * @param privateInfo null, якщо доступу до закритої інформації немає
 * @param invitation  останнє запрошення між переглядачем і власником анкети, або null
 */
public record ProfileView(
        ProfilePublicDTO publicInfo,
        ProfilePrivateDTO privateInfo,
        boolean ownProfile,
        InvitationDTO invitation) {

    public boolean hasPrivateAccess() {
        return privateInfo != null;
    }
}
