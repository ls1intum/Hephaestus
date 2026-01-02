package de.tum.in.www1.hephaestus.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter that transparently encrypts String fields when persisting
 * and decrypts them when loading.
 *
 * <p>Usage:</p>
 * <pre>
 * &#64;Column(name = "secret_token", columnDefinition = "TEXT")
 * &#64;Convert(converter = EncryptedStringConverter.class)
 * private String secretToken;
 * </pre>
 *
 * <p>Security features:</p>
 * <ul>
 *   <li>AES-256-GCM authenticated encryption</li>
 *   <li>Unique random IV per encryption operation</li>
 *   <li>Backward compatible with plaintext values for migration</li>
 *   <li>Null-safe: null values are stored as null</li>
 * </ul>
 *
 * <p>Configuration:</p>
 * <p>Set {@code hephaestus.encryption.secret-key} to a Base64-encoded 256-bit key.
 * If not set, encryption is disabled and values are stored in plaintext.</p>
 *
 * <p>Note: This converter is instantiated by Hibernate, not Spring. It uses
 * {@link EncryptionContext} to access the Spring-managed encryptor.</p>
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        AesGcmEncryptor encryptor = EncryptionContext.getEncryptor();
        if (encryptor == null) {
            // Encryption not configured or Spring context not yet loaded
            // Return as-is (graceful degradation during startup/schema validation)
            return attribute;
        }
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        AesGcmEncryptor encryptor = EncryptionContext.getEncryptor();
        if (encryptor == null) {
            // Encryption not configured or Spring context not yet loaded
            // Return as-is (graceful degradation during startup/schema validation)
            return dbData;
        }
        return encryptor.decrypt(dbData);
    }
}
