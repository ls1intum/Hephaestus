package de.tum.cit.aet.hephaestus.integration.registry;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import de.tum.cit.aet.hephaestus.core.security.EncryptionException;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * JPA converter for the sealed {@link CredentialBundle} ↔ raw ciphertext bytes.
 * Serialises via Jackson, encrypts with AES-256-GCM
 * ({@code hephaestus.security.encryption-key}, 32-char/256-bit). Layout:
 * {@code [12-byte IV][ciphertext+128-bit GCM tag]} — raw {@code BYTEA}, no {@code ENC:}
 * prefix. Not {@code autoApply}: callers go through
 * {@code Connection.setCredentials(bundle, converter)} so the {@code credentials_alg}
 * column stays in lockstep with the blob.
 */
@Component
@Converter(autoApply = false)
public class CredentialBundleConverter implements AttributeConverter<CredentialBundle, byte[]> {

    private static final Logger log = LoggerFactory.getLogger(CredentialBundleConverter.class);

    /** Tag persisted on {@code connection.credentials_alg} for any blob written by this converter. */
    public static final String ALGORITHM_TAG = "aesgcm-v1";

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    /** Hoisted to avoid re-seeding /dev/random on every encrypt; SecureRandom is thread-safe. */
    private static final SecureRandom IV_GENERATOR = new SecureRandom();

    /** Static so Hibernate's no-arg construction of this @Converter still hits the wired mapper. */
    private static volatile ObjectMapper sharedMapper;

    private final SecretKey secretKey;
    private final boolean enabled;

    /**
     * No-arg constructor for JPA/Hibernate when the converter is materialized outside the
     * Spring context (Liquibase, plain Hibernate diff). Encryption is disabled — any
     * write attempt throws so we don't silently persist plaintext credentials.
     */
    public CredentialBundleConverter() {
        this.secretKey = null;
        this.enabled = false;
        log.debug("Instantiated CredentialBundleConverter: enabled=false, reason=no_spring_context");
    }

    public CredentialBundleConverter(
        @Value("${hephaestus.security.encryption-key:}") String encryptionKey,
        @Value("${spring.profiles.active:}") String activeProfiles
    ) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            if (activeProfiles != null && activeProfiles.contains("prod")) {
                throw new IllegalStateException(
                    "Encryption key is required in production! Set hephaestus.security.encryption-key"
                );
            }
            log.warn(
                "Skipped credential encryption configuration: reason=missing_key, "
                    + "action=set_hephaestus_security_encryption_key_in_production"
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
            log.info("Enabled credential bundle encryption (AES-256-GCM)");
        }
    }

    /**
     * Wires Spring's auto-configured Jackson 3 {@link ObjectMapper} into the static slot
     * so the no-arg JPA constructor (and any fallback path) still gets a polymorphism-
     * capable mapper.
     */
    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        CredentialBundleConverter.sharedMapper = objectMapper;
    }

    /**
     * Whether encryption is operational. Tests + bootstrap diagnostics check this before
     * treating absence-of-output as a real "no credential" signal.
     */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @Nullable
    public byte[] convertToDatabaseColumn(@Nullable CredentialBundle attribute) {
        if (attribute == null) {
            return null;
        }
        if (!enabled) {
            // Refuse silently-plaintext writes — silently persisting unencrypted credentials
            // would create a far worse failure mode than a noisy startup error.
            throw new EncryptionException(
                "CredentialBundleConverter is not enabled; cannot persist credentials without a configured key");
        }
        byte[] plaintext = serialize(attribute);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            IV_GENERATOR.nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plaintext);

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return combined;
        } catch (Exception e) {
            log.error("Failed to encrypt credential bundle", e);
            throw new EncryptionException("Credential encryption failed", e);
        }
    }

    @Override
    @Nullable
    public CredentialBundle convertToEntityAttribute(@Nullable byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        if (!enabled) {
            throw new EncryptionException(
                "CredentialBundleConverter is not enabled; cannot decrypt credentials");
        }
        if (dbData.length < GCM_IV_LENGTH + 1) {
            throw new EncryptionException(
                "Credential ciphertext too short: " + dbData.length + " bytes (need > " + GCM_IV_LENGTH + ")");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[dbData.length - GCM_IV_LENGTH];
            System.arraycopy(dbData, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(dbData, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] plaintext = cipher.doFinal(cipherText);
            return deserialize(plaintext);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to decrypt credential bundle", e);
            throw new EncryptionException("Credential decryption failed", e);
        }
    }

    private static byte[] serialize(CredentialBundle bundle) {
        try {
            return mapper().writeValueAsBytes(bundle);
        } catch (Exception e) {
            throw new EncryptionException("Failed to serialize credential bundle", e);
        }
    }

    private static CredentialBundle deserialize(byte[] json) {
        try {
            return mapper().readValue(json, CredentialBundle.class);
        } catch (Exception e) {
            throw new EncryptionException("Failed to deserialize credential bundle", e);
        }
    }

    private static ObjectMapper mapper() {
        ObjectMapper m = sharedMapper;
        if (m != null) {
            return m;
        }
        // Bootstrap fallback for unit tests / detached Hibernate. JSR-310 module is
        // required: BearerToken.expiresAt + OAuthSession.expiresAt carry Instant.
        synchronized (CredentialBundleConverter.class) {
            if (sharedMapper == null) {
                sharedMapper = JsonMapper.builder().findAndAddModules().build();
            }
            return sharedMapper;
        }
    }
}
