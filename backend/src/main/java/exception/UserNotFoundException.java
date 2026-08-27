package exception;

public class UserNotFoundException extends BankException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
