package de.tum.in.www1.hephaestus.encryption;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption service following OWASP cryptographic storage best practices.
 *
 * <p>Security properties:</p>
 * <ul>
 *   <li>AES-256-GCM provides authenticated encryption with associated data (AEAD)</li>
 *   <li>Each encryption uses a unique 12-byte random IV (nonce)</li>
 *   <li>128-bit authentication tag ensures integrity and authenticity</li>
 *   <li>IV is prepended to ciphertext for storage</li>
 * </ul>
 *
 * <p>Storage format: {@code ENC:v1:<base64(IV || ciphertext || authTag)>}</p>
 *
 * <p>The prefix allows:</p>
 * <ul>
 *   <li>Detection of already-encrypted vs plaintext values (migration support)</li>
 *   <li>Version-aware decryption for future algorithm upgrades</li>
 * </ul>
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html">OWASP Cryptographic Storage</a>
 */
@Component
public class AesGcmEncryptor {

    private static final Logger log = LoggerFactory.getLogger(AesGcmEncryptor.class);

    /**
     * Prefix for encrypted values. Format: ENC:v{version}:{base64-ciphertext}
     */
    private static final String ENCRYPTED_PREFIX = "ENC:v1:";

    /**
     * AES-256-GCM algorithm specification.
     */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    /**
     * GCM IV length in bytes. NIST recommends 12 bytes for GCM.
     */
    private static final int GCM_IV_LENGTH_BYTES = 12;

    /**
     * GCM authentication tag length in bits. 128 bits is the standard.
     */
    private static final int GCM_AUTH_TAG_LENGTH_BITS = 128;

    private final EncryptionProperties properties;
    private final SecureRandom secureRandom;

    public AesGcmEncryptor(EncryptionProperties properties) {
        this.properties = properties;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     *
     * @param plaintext the value to encrypt, or null
     * @return the encrypted value with prefix, or null if input was null
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        if (!properties.isEncryptionEnabled()) {
            log.debug("Encryption disabled, returning plaintext");
            return plaintext;
        }

        if (plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            byte[] key = properties.getDecodedKey();
            byte[] iv = generateIv();
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_AUTH_TAG_LENGTH_BITS, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Combine IV + ciphertext (auth tag is appended by GCM)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            String encoded = Base64.getEncoder().encodeToString(combined);
            return ENCRYPTED_PREFIX + encoded;
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt value", e);
        }
    }

    /**
     * Decrypts an encrypted string back to plaintext.
     *
     * <p>Handles both encrypted values (with prefix) and legacy plaintext values
     * for backward compatibility during migration.</p>
     *
     * @param encrypted the encrypted value with prefix, or null
     * @return the decrypted plaintext, or null if input was null
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }

        if (!properties.isEncryptionEnabled()) {
            log.debug("Encryption disabled, returning value as-is");
            return encrypted;
        }

        if (encrypted.isEmpty()) {
            return encrypted;
        }

        // Handle legacy plaintext values (not encrypted)
        if (!isEncrypted(encrypted)) {
            log.debug("Value is not encrypted (no prefix), returning as plaintext for migration");
            return encrypted;
        }

        try {
            // Strip prefix and decode
            String base64Data = encrypted.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Data);

            if (combined.length < GCM_IV_LENGTH_BYTES) {
                throw new EncryptionException("Invalid encrypted data: too short");
            }

            // Extract IV and ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            byte[] key = properties.getDecodedKey();
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_AUTH_TAG_LENGTH_BITS, iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (EncryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt value", e);
        }
    }

    /**
     * Checks if a value is already encrypted.
     *
     * @param value the value to check
     * @return true if the value has the encryption prefix
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }
}
