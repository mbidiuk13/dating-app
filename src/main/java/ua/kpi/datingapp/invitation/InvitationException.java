package ua.kpi.datingapp.invitation;

/** Порушення бізнес-правил обміну запрошеннями. Повідомлення показується користувачу. */
public class InvitationException extends RuntimeException {

    public InvitationException(String message) {
        super(message);
    }
}
