package de.tum.in.www1.hephaestus.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AesGcmEncryptor}.
 *
 * <p>These tests verify the cryptographic correctness and security properties
 * of the AES-256-GCM encryption implementation following OWASP best practices.</p>
 */
@Tag("unit")
class AesGcmEncryptorTest {

    // Valid 256-bit (32-byte) AES key, generated with: openssl rand -base64 32
    private static final String VALID_KEY_BASE64 = "E2h/FDBzvopGPcQp518qIt6EFdcPZ6J2tKJTwx0EYAg=";

    private EncryptionProperties properties;
    private AesGcmEncryptor encryptor;

    @BeforeEach
    void setUp() {
        properties = new EncryptionProperties();
        properties.setSecretKey(VALID_KEY_BASE64);
        encryptor = new AesGcmEncryptor(properties);
    }

    @Nested
    @DisplayName("Encryption roundtrip")
    class EncryptionRoundtrip {

        @Test
        @DisplayName("should encrypt and decrypt text correctly")
        void encryptDecryptRoundtrip() {
            String plaintext = "ghp_supersecrettoken123456789";

            String encrypted = encryptor.encrypt(plaintext);
            String decrypted = encryptor.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle multi-byte Unicode characters")
        void encryptDecryptUnicode() {
            String plaintext = "Token with emoji and unicode: 42";

            String encrypted = encryptor.encrypt(plaintext);
            String decrypted = encryptor.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("should handle very long secrets")
        void encryptDecryptLongSecret() {
            String plaintext = "x".repeat(10000);

            String encrypted = encryptor.encrypt(plaintext);
            String decrypted = encryptor.decrypt(encrypted);

            assertThat(decrypted).isEqualTo(plaintext);
        }
    }

    @Nested
    @DisplayName("IV uniqueness (semantic security)")
    class IvUniqueness {

        @Test
        @DisplayName("should produce different ciphertext for same plaintext")
        void samePlaintextDifferentCiphertext() {
            String plaintext = "identical-secret-token";

            String encrypted1 = encryptor.encrypt(plaintext);
            String encrypted2 = encryptor.encrypt(plaintext);

            // Different IVs mean different ciphertext
            assertThat(encrypted1).isNotEqualTo(encrypted2);

            // But both decrypt to same plaintext
            assertThat(encryptor.decrypt(encrypted1)).isEqualTo(plaintext);
            assertThat(encryptor.decrypt(encrypted2)).isEqualTo(plaintext);
        }
    }

    @Nested
    @DisplayName("Null and empty handling")
    class NullAndEmptyHandling {

        @Test
        @DisplayName("should return null for null input on encrypt")
        void encryptNullReturnsNull() {
            assertThat(encryptor.encrypt(null)).isNull();
        }

        @Test
        @DisplayName("should return null for null input on decrypt")
        void decryptNullReturnsNull() {
            assertThat(encryptor.decrypt(null)).isNull();
        }

        @Test
        @DisplayName("should return empty string for empty input on encrypt")
        void encryptEmptyReturnsEmpty() {
            assertThat(encryptor.encrypt("")).isEmpty();
        }

        @Test
        @DisplayName("should return empty string for empty input on decrypt")
        void decryptEmptyReturnsEmpty() {
            assertThat(encryptor.decrypt("")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Encrypted value format")
    class EncryptedValueFormat {

        @Test
        @DisplayName("should prefix encrypted values with version marker")
        void encryptedValueHasPrefix() {
            String encrypted = encryptor.encrypt("some-secret");

            assertThat(encrypted).startsWith("ENC:v1:");
        }

        @Test
        @DisplayName("should contain valid Base64 after prefix")
        void encryptedValueContainsValidBase64() {
            String encrypted = encryptor.encrypt("some-secret");
            String base64Part = encrypted.substring("ENC:v1:".length());

            // Should not throw
            byte[] decoded = Base64.getDecoder().decode(base64Part);
            // IV (12) + ciphertext (>=1) + auth tag (16) = at least 29 bytes
            assertThat(decoded.length).isGreaterThanOrEqualTo(29);
        }

        @Test
        @DisplayName("should identify encrypted values correctly")
        void isEncryptedDetectsPrefix() {
            String encrypted = encryptor.encrypt("secret");
            String plaintext = "not-encrypted-value";

            assertThat(encryptor.isEncrypted(encrypted)).isTrue();
            assertThat(encryptor.isEncrypted(plaintext)).isFalse();
            assertThat(encryptor.isEncrypted(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Backward compatibility (migration support)")
    class BackwardCompatibility {

        @Test
        @DisplayName("should return plaintext values as-is (migration path)")
        void decryptPlaintextReturnsAsIs() {
            String legacyPlaintext = "ghp_oldPlaintextToken123";

            String decrypted = encryptor.decrypt(legacyPlaintext);

            assertThat(decrypted).isEqualTo(legacyPlaintext);
        }
    }

    @Nested
    @DisplayName("Tamper detection (GCM auth tag)")
    class TamperDetection {

        @Test
        @DisplayName("should fail to decrypt tampered ciphertext")
        void tamperedCiphertextThrows() {
            String encrypted = encryptor.encrypt("original-secret");

            // Tamper with the Base64 portion (flip a character)
            String tampered = encrypted.substring(0, encrypted.length() - 5) + "XXXXX";

            assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("should fail on truncated ciphertext")
        void truncatedCiphertextThrows() {
            String tampered = "ENC:v1:AAAA"; // Too short

            assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(EncryptionException.class);
        }
    }

    @Nested
    @DisplayName("Encryption disabled")
    class EncryptionDisabled {

        @Test
        @DisplayName("should pass through values when key is empty")
        void passthroughWhenDisabled() {
            EncryptionProperties disabledProps = new EncryptionProperties();
            disabledProps.setSecretKey("");
            AesGcmEncryptor disabledEncryptor = new AesGcmEncryptor(disabledProps);

            String plaintext = "visible-in-db";

            assertThat(disabledEncryptor.encrypt(plaintext)).isEqualTo(plaintext);
            assertThat(disabledEncryptor.decrypt(plaintext)).isEqualTo(plaintext);
        }
    }
}
