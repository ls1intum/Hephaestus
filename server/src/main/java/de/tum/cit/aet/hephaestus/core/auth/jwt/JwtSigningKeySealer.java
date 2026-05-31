package de.tum.cit.aet.hephaestus.core.auth.jwt;

import de.tum.cit.aet.hephaestus.core.security.EncryptionException;
import de.tum.cit.aet.hephaestus.core.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Seals the {@code jwt_signing_key.private_key_pem} column at rest with AES-256-GCM,
 * reusing the system master key bound via {@link SecurityProperties#encryptionKey()} —
 * the same key {@code CredentialBundleConverter} already requires in prod.
 *
 * <h2>Why system-scoped (not tenant-scoped) AAD</h2>
 * Signing keys are system-wide, not per-workspace. The GCM AAD is a single fixed,
 * purpose-binding constant ({@link #AAD_STRING}) rather than a per-row tuple. This still
 * defends against confused-deputy substitution: a blob sealed for this column cannot be
 * decrypted in any context that supplies a different AAD (e.g. the tenant-scoped
 * {@code CredentialBundleConverter} AAD), and vice versa.
 *
 * <h2>Envelope layout</h2>
 * <pre>
 *   [0]        version byte ({@link #FORMAT_VERSION_V1} = 0x01)
 *   [1..13)    12-byte random GCM nonce
 *   [13..]     ciphertext || 16-byte GCM tag
 * </pre>
 *
 * <h2>Enablement / fail-fast</h2>
 * Mirrors {@code CredentialBundleConverter}: enabled iff a valid 32-char key is present.
 * In the {@code prod} profile a missing key throws (prod requires it); in dev/CI/test an
 * absent key disables sealing so a local boot still works (writing raw {@code v0-unsealed}
 * rows). A non-32-char key is always rejected.
 */
@Component
public class JwtSigningKeySealer {

    private static final Logger log = LoggerFactory.getLogger(JwtSigningKeySealer.class);

    /** Tag stamped on {@code jwt_signing_key.encryption_key_id} for blobs sealed by this class. */
    public static final String KEY_ID = "aesgcm-system-v1";

    /** First byte of every sealed blob. */
    public static final byte FORMAT_VERSION_V1 = 0x01;

    /**
     * Fixed, system-scoped GCM AAD. Binds a sealed blob to this column/purpose so it cannot be
     * swapped with a tenant-scoped credential blob (distinct AAD domain → authentication fails).
     */
    static final String AAD_STRING = "system:jwt_signing_key.private_key_pem";

    private static final byte[] AAD = AAD_STRING.getBytes(StandardCharsets.UTF_8);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    /** Hoisted to avoid re-seeding on every seal; SecureRandom is thread-safe. */
    private static final SecureRandom IV_GENERATOR = new SecureRandom();

    private final @Nullable SecretKey secretKey;
    private final boolean enabled;

    /**
     * Spring-wired constructor. The key is bound via {@link SecurityProperties}; the active
     * profile string comes through {@code @Value} so the prod fail-fast check is identical to
     * {@code CredentialBundleConverter}.
     */
    @Autowired
    public JwtSigningKeySealer(
        SecurityProperties securityProperties,
        @Value("${spring.profiles.active:}") String activeProfiles
    ) {
        this(securityProperties.encryptionKey(), activeProfiles);
    }

    /**
     * Canonical constructor (also the unit-test seam): builds the cipher key from raw inputs.
     * Missing key fails fast in prod, disables elsewhere; a non-32-char key is rejected.
     */
    public JwtSigningKeySealer(@Nullable String encryptionKey, @Nullable String activeProfiles) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            if (activeProfiles != null && activeProfiles.contains("prod")) {
                throw new IllegalStateException(
                    "Encryption key is required in production to seal JWT signing keys! " +
                        "Set hephaestus.security.encryption-key"
                );
            }
            log.warn(
                "Skipped JWT signing-key sealing: reason=missing_key, " +
                    "action=set_hephaestus_security_encryption_key_in_production"
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
            log.info("Enabled JWT signing-key sealing at rest (AES-256-GCM, system-scoped AAD)");
        }
    }

    /** Whether sealing is operational (a valid key is configured). */
    public boolean isEnabled() {
        return enabled;
    }

    /** Tag to stamp on {@code encryption_key_id} when sealing is enabled. */
    public String keyId() {
        return KEY_ID;
    }

    /**
     * Seal raw PKCS#8 DER private-key bytes into the versioned AES-256-GCM envelope.
     *
     * @throws EncryptionException if sealing is disabled or the cipher fails
     */
    public byte[] seal(byte[] privateKeyDer) {
        requireEnabled("seal");
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            IV_GENERATOR.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(AAD);
            byte[] cipherText = cipher.doFinal(privateKeyDer);

            byte[] combined = new byte[1 + iv.length + cipherText.length];
            combined[0] = FORMAT_VERSION_V1;
            System.arraycopy(iv, 0, combined, 1, iv.length);
            System.arraycopy(cipherText, 0, combined, 1 + iv.length, cipherText.length);
            return combined;
        } catch (Exception e) {
            throw new EncryptionException("JWT signing-key sealing failed", e);
        }
    }

    /**
     * Reverse {@link #seal(byte[])}: returns the original PKCS#8 DER bytes. A wrong AAD, a
     * tampered blob, an unsupported version byte, or a too-short input all surface as a clear
     * {@link EncryptionException}.
     */
    public byte[] unseal(byte[] sealed) {
        requireEnabled("unseal");
        if (sealed.length < 1) {
            throw new EncryptionException("Sealed JWT key too short: 0 bytes");
        }
        byte version = sealed[0];
        if (version != FORMAT_VERSION_V1) {
            throw new EncryptionException(
                "Unsupported sealed JWT key version: 0x" + Integer.toHexString(version & 0xFF)
            );
        }
        if (sealed.length < 1 + GCM_IV_LENGTH + 1) {
            throw new EncryptionException(
                "Sealed JWT key too short: " + sealed.length + " bytes (need > " + (1 + GCM_IV_LENGTH) + ")"
            );
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[sealed.length - 1 - GCM_IV_LENGTH];
            System.arraycopy(sealed, 1, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(sealed, 1 + GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(AAD);
            return cipher.doFinal(cipherText);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("JWT signing-key unsealing failed", e);
        }
    }

    private void requireEnabled(String op) {
        if (!enabled) {
            throw new EncryptionException(
                "JwtSigningKeySealer is not enabled; cannot " + op + " without a configured key"
            );
        }
    }
}
