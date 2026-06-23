package io.github.commonpay.util;

public class PayException extends RuntimeException {
    public PayException(String message) {
        super(message);
    }

    public PayException(String message, Throwable cause) {
        super(message, cause);
    }
}
