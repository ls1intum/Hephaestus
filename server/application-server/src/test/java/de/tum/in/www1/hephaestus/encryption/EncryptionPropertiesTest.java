package de.tum.in.www1.hephaestus.encryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EncryptionProperties}.
 */
@Tag("unit")
class EncryptionPropertiesTest {

    // Valid 256-bit (32-byte) AES key, generated with: openssl rand -base64 32
    private static final String VALID_KEY_BASE64 = "E2h/FDBzvopGPcQp518qIt6EFdcPZ6J2tKJTwx0EYAg=";
    private static final int EXPECTED_KEY_LENGTH = 32;

    @Nested
    @DisplayName("Key validation")
    class KeyValidation {

        @Test
        @DisplayName("should accept valid 256-bit Base64 key")
        void validKeyAccepted() {
            EncryptionProperties props = new EncryptionProperties();
            props.setSecretKey(VALID_KEY_BASE64);

            assertThat(props.isEncryptionEnabled()).isTrue();
            assertThat(props.getDecodedKey()).hasSize(EXPECTED_KEY_LENGTH);
        }

        @Test
        @DisplayName("should reject key with wrong length")
        void wrongKeyLengthRejected() {
            EncryptionProperties props = new EncryptionProperties();
            // 16 bytes instead of 32
            props.setSecretKey("c2hvcnQta2V5LTE2Ynl0ZXM=");

            assertThatThrownBy(props::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
        }

        @Test
        @DisplayName("should reject invalid Base64")
        void invalidBase64Rejected() {
            EncryptionProperties props = new EncryptionProperties();
            props.setSecretKey("not-valid-base64!!!");

            assertThatThrownBy(props::getDecodedKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid Base64");
        }
    }

    @Nested
    @DisplayName("Encryption enabled flag")
    class EncryptionEnabledFlag {

        @Test
        @DisplayName("should be disabled when key is null")
        void disabledWhenNull() {
            EncryptionProperties props = new EncryptionProperties();
            props.setSecretKey(null);

            assertThat(props.isEncryptionEnabled()).isFalse();
        }

        @Test
        @DisplayName("should be disabled when key is empty")
        void disabledWhenEmpty() {
            EncryptionProperties props = new EncryptionProperties();
            props.setSecretKey("");

            assertThat(props.isEncryptionEnabled()).isFalse();
        }

        @Test
        @DisplayName("should be disabled when key is whitespace only")
        void disabledWhenWhitespace() {
            EncryptionProperties props = new EncryptionProperties();
            props.setSecretKey("   ");

            assertThat(props.isEncryptionEnabled()).isFalse();
        }

        @Test
        @DisplayName("should throw when getting key while disabled")
        void throwsWhenDisabledAndGetKey() {
            EncryptionProperties props = new EncryptionProperties();
            props.setSecretKey("");

            assertThatThrownBy(props::getDecodedKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Encryption key not configured");
        }
    }
}
