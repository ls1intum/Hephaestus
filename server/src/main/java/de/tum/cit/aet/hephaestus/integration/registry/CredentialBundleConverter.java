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
 * Encrypts {@link CredentialBundle} ↔ raw ciphertext bytes with AES-256-GCM. Two
 * formats coexist during the v1→v2 rollout:
 *
 * <ul>
 *   <li><b>v1</b> ({@code FORMAT_VERSION_V1}): static AAD ({@link #contextAad()}).
 *       Read-only after this commit — only the Liquibase backfill writes v1.</li>
 *   <li><b>v2</b> ({@code FORMAT_VERSION_V2}): per-row AAD derived from
 *       {@link EncryptionContext}. New writes always v2; reader switches on the
 *       first byte. Cross-row substitution → {@code AEADBadTagException}.</li>
 * </ul>
 *
 * <p>The {@link AttributeConverter} interface methods write v1 — they're only used by
 * the legacy Liquibase backfill ({@code WorkspaceConnectionBackfillChange}) which
 * predates per-row context. {@link Connection#setCredentials(CredentialBundle)} drives
 * the runtime path via {@link #encrypt(CredentialBundle, EncryptionContext)} /
 * {@link #decrypt(byte[], EncryptionContext)} so every new write is v2 and every old v1
 * row migrates lazily on next save.
 */
@Component
@Converter(autoApply = false)
public class CredentialBundleConverter implements AttributeConverter<CredentialBundle, byte[]> {

    private static final Logger log = LoggerFactory.getLogger(CredentialBundleConverter.class);

    /** Tag persisted on {@code connection.credentials_alg} for any blob written by this converter. */
    public static final String ALGORITHM_TAG = "aesgcm-v1";

    /** First byte of v1-format ciphertexts. Read-only after Wave 3 — backfill only. */
    public static final byte FORMAT_VERSION_V1 = 0x01;

    /** First byte of v2-format ciphertexts (per-row AAD). All new writes. */
    public static final byte FORMAT_VERSION_V2 = 0x02;

    /** v1 AAD — fixed string, no row binding. Kept ONLY for tolerant decrypt of legacy rows. */
    private static final byte[] CONTEXT_AAD_V1 = "hephaestus-credential-bundle-v1".getBytes(StandardCharsets.UTF_8);

    /** Exposed for the Liquibase backfill so it produces v1-format blobs identical to legacy writes. */
    public static byte[] contextAad() {
        return CONTEXT_AAD_V1.clone();
    }

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

    /**
     * v1 write path. Used ONLY by the legacy Liquibase backfill — runtime writes go
     * through {@link #encrypt(CredentialBundle, EncryptionContext)} and produce v2.
     */
    @Override
    @Nullable
    public byte[] convertToDatabaseColumn(@Nullable CredentialBundle attribute) {
        if (attribute == null) return null;
        requireEnabled("persist");
        return encryptInternal(serialize(attribute), CONTEXT_AAD_V1, FORMAT_VERSION_V1);
    }

    /**
     * Tolerant read: decrypts v1 (static AAD) and v2 (per-row AAD). Used by the
     * legacy backfill (v1-only state) and by callers that lack {@link EncryptionContext}
     * (none, post-Wave 3, except tests). Production reads go through
     * {@link #decrypt(byte[], EncryptionContext)} which rejects v2 without context.
     */
    @Override
    @Nullable
    public CredentialBundle convertToEntityAttribute(@Nullable byte[] dbData) {
        if (dbData == null) return null;
        requireEnabled("decrypt");
        return switch (versionByte(dbData)) {
            case FORMAT_VERSION_V1 -> deserialize(decryptInternal(dbData, 1, CONTEXT_AAD_V1));
            case FORMAT_VERSION_V2 -> throw new EncryptionException(
                "v2 blob requires per-row EncryptionContext — use decrypt(byte[], EncryptionContext)");
            default -> throw unsupportedVersion(dbData[0]);
        };
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
     * Tolerant v1/v2 decrypt:
     * <ul>
     *   <li>v1: decrypts with the static legacy AAD (context arg ignored — v1 carries no binding).</li>
     *   <li>v2: decrypts with AAD derived from {@code ctx}. Wrong context →
     *       {@link javax.crypto.AEADBadTagException} (wrapped as {@link EncryptionException}).</li>
     * </ul>
     */
    public CredentialBundle decrypt(byte[] dbData, EncryptionContext ctx) {
        if (dbData == null) throw new IllegalArgumentException("dbData must not be null");
        if (ctx == null) throw new IllegalArgumentException("ctx must not be null");
        requireEnabled("decrypt");
        return switch (versionByte(dbData)) {
            case FORMAT_VERSION_V1 -> deserialize(decryptInternal(dbData, 1, CONTEXT_AAD_V1));
            case FORMAT_VERSION_V2 -> deserialize(decryptInternal(dbData, 1, ctx.toAad()));
            default -> throw unsupportedVersion(dbData[0]);
        };
    }

    /** True when the blob is in legacy v1 format and would benefit from re-encrypt on next save. */
    public static boolean isLegacyV1(@Nullable byte[] dbData) {
        return dbData != null && dbData.length >= 1 && dbData[0] == FORMAT_VERSION_V1;
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
