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
 * Encrypts {@link CredentialBundle} ↔ raw ciphertext bytes with AES-256-GCM and a
 * per-row AAD derived from {@link EncryptionContext}.
 *
 * <p>The AAD binds the ciphertext to its {@code (workspaceId, kind, instanceKey,
 * columnFqn)} row coordinates, so a blob copied into a different row fails GCM
 * authentication ({@link javax.crypto.AEADBadTagException}) — closes the cross-row
 * substitution CVE flagged in audit pass 3.
 *
 * <p>The wire format is a single version (v2, {@code 0x02}). The {@link
 * AttributeConverter} interface is implemented for symmetry with JPA discovery but
 * the legacy {@code convertToEntityAttribute(byte[])} path is intentionally
 * context-less: it rejects every v2 blob it sees. Runtime reads go through {@link
 * Connection#credentials(CredentialBundleConverter)} which holds the {@link
 * EncryptionContext} for its row.
 */
@Component
@Converter(autoApply = false)
public class CredentialBundleConverter implements AttributeConverter<CredentialBundle, byte[]> {

    private static final Logger log = LoggerFactory.getLogger(CredentialBundleConverter.class);

    /** Tag persisted on {@code connection.credentials_alg} for any blob written by this converter. */
    public static final String ALGORITHM_TAG = "aesgcm-v1";

    /** First byte of every v2-format ciphertext (per-row AAD). v1 was retired with Stage 2. */
    public static final byte FORMAT_VERSION_V2 = 0x02;

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
     * Spring context (plain Hibernate diff, etc.). Encryption is disabled — any write
     * attempt throws so we don't silently persist plaintext credentials.
     */
    public CredentialBundleConverter() {
        this.secretKey = null;
        this.enabled = false;
        log.debug("Instantiated CredentialBundleConverter: enabled=false, reason=no_spring_context");
    }

    @Autowired
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

    /**
     * Context-less {@link AttributeConverter} write path. Unsupported on a v2-only
     * codebase — every runtime write goes through {@link #encrypt(CredentialBundle,
     * EncryptionContext)} which binds the AAD to the row. Calling this throws so any
     * silent fall-back to context-less encryption is caught at unit-test time.
     */
    @Override
    @Nullable
    public byte[] convertToDatabaseColumn(@Nullable CredentialBundle attribute) {
        if (attribute == null) return null;
        throw new EncryptionException(
            "CredentialBundleConverter.convertToDatabaseColumn requires per-row EncryptionContext — "
                + "use encrypt(bundle, ctx)");
    }

    /**
     * Context-less {@link AttributeConverter} read path. Always rejects v2 blobs so the
     * tolerant fallback is forced through the explicit {@link #decrypt(byte[],
     * EncryptionContext)} entrypoint. Returns {@code null} for {@code null} input so JPA
     * mapping of a NULL column stays sane.
     */
    @Override
    @Nullable
    public CredentialBundle convertToEntityAttribute(@Nullable byte[] dbData) {
        if (dbData == null) return null;
        requireEnabled("decrypt");
        byte version = versionByte(dbData);
        if (version == FORMAT_VERSION_V2) {
            throw new EncryptionException(
                "v2 blob requires per-row EncryptionContext — use decrypt(byte[], EncryptionContext)");
        }
        throw unsupportedVersion(version);
    }

    /**
     * v2 write path. Binds the GCM AAD to {@code ctx} so the ciphertext cannot be
     * substituted into a row with a different {@code (workspaceId, kind, instanceKey,
     * columnFqn)} tuple.
     */
    public byte[] encrypt(CredentialBundle bundle, EncryptionContext ctx) {
        if (bundle == null) throw new IllegalArgumentException("bundle must not be null");
        if (ctx == null) throw new IllegalArgumentException("ctx must not be null");
        requireEnabled("persist");
        return encryptInternal(serialize(bundle), ctx.toAad(), FORMAT_VERSION_V2);
    }

    /**
     * v2 decrypt: decrypts with AAD derived from {@code ctx}. Wrong context, tamper,
     * or unsupported version → {@link EncryptionException}.
     */
    public CredentialBundle decrypt(byte[] dbData, EncryptionContext ctx) {
        if (dbData == null) throw new IllegalArgumentException("dbData must not be null");
        if (ctx == null) throw new IllegalArgumentException("ctx must not be null");
        requireEnabled("decrypt");
        byte version = versionByte(dbData);
        if (version != FORMAT_VERSION_V2) {
            throw unsupportedVersion(version);
        }
        return deserialize(decryptInternal(dbData, 1, ctx.toAad()));
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private byte[] encryptInternal(byte[] plaintext, byte[] aad, byte versionByte) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            IV_GENERATOR.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(aad);
            byte[] cipherText = cipher.doFinal(plaintext);

            byte[] combined = new byte[1 + iv.length + cipherText.length];
            combined[0] = versionByte;
            System.arraycopy(iv, 0, combined, 1, iv.length);
            System.arraycopy(cipherText, 0, combined, 1 + iv.length, cipherText.length);
            return combined;
        } catch (Exception e) {
            throw new EncryptionException("Credential encryption failed", e);
        }
    }

    private byte[] decryptInternal(byte[] dbData, int headerLen, byte[] aad) {
        if (dbData.length < headerLen + GCM_IV_LENGTH + 1) {
            throw new EncryptionException(
                "Credential ciphertext too short: " + dbData.length + " bytes (need > "
                    + (headerLen + GCM_IV_LENGTH) + ")");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[dbData.length - headerLen - GCM_IV_LENGTH];
            System.arraycopy(dbData, headerLen, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(dbData, headerLen + GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(cipherText);
        } catch (Exception e) {
            throw new EncryptionException("Credential decryption failed", e);
        }
    }

    private byte versionByte(byte[] dbData) {
        if (dbData.length < 1) {
            throw new EncryptionException("Credential ciphertext too short: 0 bytes");
        }
        return dbData[0];
    }

    private static EncryptionException unsupportedVersion(byte b) {
        return new EncryptionException(
            "Unsupported credential blob version: 0x" + Integer.toHexString(b & 0xFF));
    }

    private void requireEnabled(String op) {
        if (!enabled) {
            throw new EncryptionException(
                "CredentialBundleConverter is not enabled; cannot " + op + " credentials without a configured key");
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
