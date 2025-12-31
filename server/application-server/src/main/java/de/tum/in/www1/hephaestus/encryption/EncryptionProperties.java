package de.tum.in.www1.hephaestus.encryption;

import jakarta.annotation.PostConstruct;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for field-level encryption at rest.
 *
 * <p>The encryption key must be provided via the {@code hephaestus.encryption.secret-key}
 * property. This can be set via environment variable {@code HEPHAESTUS_ENCRYPTION_SECRET_KEY}
 * or in application configuration files.</p>
 *
 * <p>The key must be a Base64-encoded 256-bit (32-byte) AES key for AES-256-GCM encryption.</p>
 *
 * <p>To generate a new key:</p>
 * <pre>
 * openssl rand -base64 32
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "hephaestus.encryption")
public class EncryptionProperties {

    private static final int AES_256_KEY_LENGTH_BYTES = 32;

    /**
     * Base64-encoded 256-bit AES key for encrypting sensitive fields.
     * If empty, encryption is disabled and secrets are stored in plaintext.
     */
    private String secretKey = "";

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Returns true if encryption is enabled (a valid key is configured).
     */
    public boolean isEncryptionEnabled() {
        return secretKey != null && !secretKey.isBlank();
    }

    /**
     * Returns the decoded encryption key bytes.
     *
     * @throws IllegalStateException if encryption is not enabled or key is invalid
     */
    public byte[] getDecodedKey() {
        if (!isEncryptionEnabled()) {
            throw new IllegalStateException(
                "Encryption key not configured. Set 'hephaestus.encryption.secret-key' property."
            );
        }
        try {
            return Base64.getDecoder().decode(secretKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Base64-encoded encryption key", e);
        }
    }

    @PostConstruct
    void validateConfiguration() {
        if (isEncryptionEnabled()) {
            byte[] key = getDecodedKey();
            if (key.length != AES_256_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(
                    String.format(
                        "Encryption key must be %d bytes (256 bits) for AES-256-GCM. " +
                            "Actual size: %d bytes. Generate with: openssl rand -base64 32",
                        AES_256_KEY_LENGTH_BYTES,
                        key.length
                    )
                );
            }
        }
    }
}
