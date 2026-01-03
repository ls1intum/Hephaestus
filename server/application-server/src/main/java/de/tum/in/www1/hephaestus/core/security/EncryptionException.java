package de.tum.in.www1.hephaestus.core.security;

/**
 * Exception thrown when encryption or decryption of sensitive data fails.
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
