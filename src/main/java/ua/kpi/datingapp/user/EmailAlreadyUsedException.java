package ua.kpi.datingapp.user;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Користувач з email " + email + " уже зареєстрований");
    }
}
