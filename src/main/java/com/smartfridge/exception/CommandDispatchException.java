package com.smartfridge.exception;

/** Command could not be delivered to the broker (HTTP 503). */
public class CommandDispatchException extends RuntimeException {

    public CommandDispatchException(String message) {
        super(message);
    }

    public CommandDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
