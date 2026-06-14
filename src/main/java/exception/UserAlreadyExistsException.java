package exception;

public class UserAlreadyExistsException extends BankException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
