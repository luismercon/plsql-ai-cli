package pt.isec.mei.plsql_ai_cli.exception;

public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}

