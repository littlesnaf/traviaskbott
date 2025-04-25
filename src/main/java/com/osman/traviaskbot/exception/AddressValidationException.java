// src/main/java/com/osman/traviaskbot/exception/AddressValidationException.java
package com.osman.traviaskbot.exception;

public class AddressValidationException extends RuntimeException {

    public AddressValidationException(String message) {
        super(message);
    }

    public AddressValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
