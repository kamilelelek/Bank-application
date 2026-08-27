package exception;

public class UnauthorizedAccountAccessException extends BankException {

    public UnauthorizedAccountAccessException(String message) {
        super(message);
    }
}
