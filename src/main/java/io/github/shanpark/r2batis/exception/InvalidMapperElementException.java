package io.github.shanpark.r2batis.exception;

public class InvalidMapperElementException extends RuntimeException {

    public InvalidMapperElementException(Throwable cause) {
        super(cause);
    }

    public InvalidMapperElementException(String message) {
        super(message);
    }

    public InvalidMapperElementException(String message, Throwable cause) {
        super(message, cause);
    }
}
