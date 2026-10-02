package com.smartfridge.exception;

/** Firmware binary could not be stored or read (HTTP 500). */
public class FirmwareStorageException extends RuntimeException {

    public FirmwareStorageException(String message) {
        super(message);
    }

    public FirmwareStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
