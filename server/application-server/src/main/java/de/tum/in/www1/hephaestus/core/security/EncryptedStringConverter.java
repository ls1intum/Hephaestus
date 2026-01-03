package de.tum.in.www1.hephaestus.core.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JPA AttributeConverter for encrypting sensitive string fields at rest using AES-256-GCM.
 *
 * <p>Usage in entities:
 * <pre>
 * &#64;Convert(converter = EncryptedStringConverter.class)
 * &#64;Column(name = "slack_token", columnDefinition = "TEXT")
 * private String slackToken;
 * </pre>
 *
 * <p>Configuration:
 * Set the environment variable or property {@code hephaestus.security.encryption-key}
 * to a 32-character (256-bit) secret key.
 *
 * <p>Security properties:
 * <ul>
 *   <li>AES-256-GCM provides authenticated encryption</li>
 *   <li>12-byte random IV prepended to ciphertext</li>
 *   <li>128-bit authentication tag for integrity</li>
 * </ul>
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(EncryptedStringConverter.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String PREFIX = "ENC:";

    private final SecretKey secretKey;
    private final boolean enabled;

    /**
     * No-arg constructor required by JPA/Hibernate when running outside Spring context
     * (e.g., Liquibase schema diff). Encryption is disabled in this mode.
     */
    public EncryptedStringConverter() {
        this.secretKey = null;
        this.enabled = false;
        logger.debug("EncryptedStringConverter instantiated without Spring context - encryption disabled");
    }

    public EncryptedStringConverter(
        @Value("${hephaestus.security.encryption-key:}") String encryptionKey,
        @Value("${spring.profiles.active:}") String activeProfiles
    ) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            if (activeProfiles != null && activeProfiles.contains("prod")) {
                throw new IllegalStateException(
                    "Encryption key is required in production! Set hephaestus.security.encryption-key"
                );
            }
            logger.warn(
                "Encryption key not configured - sensitive data will NOT be encrypted at rest. " +
                    "Set hephaestus.security.encryption-key in production!"
            );
            this.secretKey = null;
            this.enabled = false;
        } else if (encryptionKey.length() != 32) {
            throw new IllegalArgumentException(
                "Encryption key must be exactly 32 characters (256 bits). Got: " + encryptionKey.length()
            );
        } else {
            this.secretKey = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            this.enabled = true;
            logger.info("Encryption enabled for sensitive database fields");
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || !enabled) {
            return attribute;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            logger.error("Failed to encrypt value", e);
            throw new EncryptionException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || !enabled) {
            return dbData;
        }

        // Handle unencrypted legacy data
        if (!dbData.startsWith(PREFIX)) {
            logger.debug("Found unencrypted value in database - returning as-is");
            return dbData;
        }

        try {
            String encoded = dbData.substring(PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to decrypt value", e);
            throw new EncryptionException("Decryption failed", e);
        }
    }
}
