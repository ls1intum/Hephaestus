package de.tum.cit.aet.hephaestus.core.security;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Encryption key configuration. */
@Validated
@ConfigurationProperties(prefix = "hephaestus.security")
public record SecurityProperties(
        @Nullable String encryptionKey,
        @Nullable String credentialEncryptionKey,
        @DefaultValue("1") int credentialEncryptionKeyVersion,
        @Nullable String priorCredentialEncryptionKey,
        @Nullable Integer priorCredentialEncryptionKeyVersion,
        @DefaultValue("false") boolean credentialRotationEnabled,
        @DefaultValue("100") int credentialRotationBatchSize) {

    public SecurityProperties {
        if (credentialEncryptionKeyVersion < 1) {
            throw new IllegalArgumentException(
                    "hephaestus.security.credential-encryption-key-version must be positive");
        }
        if ((priorCredentialEncryptionKey == null) != (priorCredentialEncryptionKeyVersion == null)) {
            throw new IllegalArgumentException(
                    "hephaestus.security.prior-credential-encryption-key and prior-credential-encryption-key-version must be configured together");
        }
        if (priorCredentialEncryptionKeyVersion != null
                && priorCredentialEncryptionKeyVersion == credentialEncryptionKeyVersion) {
            throw new IllegalArgumentException("Active and prior encryption key versions must differ");
        }
        if (credentialRotationBatchSize < 1 || credentialRotationBatchSize > 10_000) {
            throw new IllegalArgumentException(
                    "hephaestus.security.credential-rotation-batch-size must be between 1 and 10000");
        }
        if (credentialRotationEnabled
                && (credentialEncryptionKey == null
                        || credentialEncryptionKey.isBlank()
                        || priorCredentialEncryptionKey == null
                        || priorCredentialEncryptionKey.isBlank())) {
            throw new IllegalArgumentException("Credential rotation requires both active and prior encryption keys");
        }
    }
}
