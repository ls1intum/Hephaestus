package de.tum.in.www1.hephaestus.encryption;

/**
 * Runtime exception thrown when encryption or decryption operations fail.
 *
 * <p>This exception is intentionally a RuntimeException because:
 * <ul>
 *   <li>Encryption failures typically indicate configuration issues (missing key)</li>
 *   <li>They should not be silently ignored</li>
 *   <li>JPA AttributeConverters cannot throw checked exceptions</li>
 * </ul>
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
