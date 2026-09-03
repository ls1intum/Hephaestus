package de.tum.cit.aet.hephaestus.core.security;

/**
 * Exception thrown when the key a stored credential was encrypted under is not configured on this
 * instance. Separated from a decryption failure because it is a property of the configuration, not
 * of the row: correcting the configuration makes the same ciphertext readable again.
 */
public class MissingCredentialKeyException extends EncryptionException {

    public MissingCredentialKeyException(String message) {
        super(message);
    }
}
