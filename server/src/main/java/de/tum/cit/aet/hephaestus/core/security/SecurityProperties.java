package de.tum.cit.aet.hephaestus.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

/**
 * Typed binding for {@code hephaestus.security.*}.
 *
 * <p>Holds the AES-256 key used by {@link EncryptedStringConverter} and
 * {@code CredentialBundleConverter} to encrypt sensitive columns at rest. The key is optional at
 * the binding layer (a dev boot without a key runs with encryption disabled); the converters
 * enforce the real policy — required in production, validated to 32 chars — so the fail-fast /
 * dev-warn semantics live where the key is actually used.
 *
 * <p><b>Note.</b> The Liquibase {@code WorkspaceConnectionBackfillChange} reads the same key via
 * {@code System.getProperty("hephaestus.security.encryption-key")} because customChanges run
 * outside the Spring context (no bound {@code @ConfigurationProperties} available); that path is
 * intentionally left untouched.
 *
 * @param encryptionKey 32-character (256-bit) AES key, or {@code null}/blank when unset
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.security")
public record SecurityProperties(@Nullable String encryptionKey) {}
