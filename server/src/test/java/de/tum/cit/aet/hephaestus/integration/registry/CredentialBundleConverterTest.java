package de.tum.cit.aet.hephaestus.integration.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.security.EncryptionException;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.GithubAppCredential;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.OAuthSession;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the AES-256-GCM round-trip behaviour of {@link CredentialBundleConverter}.
 *
 * <p>Why the wider coverage than typical "happy path + null":
 * <ul>
 *   <li>The output format is on-disk and we can't migrate it without a rewrite — the
 *       IV-prepend layout is therefore tested explicitly so accidental drift
 *       (e.g. someone "just" Base64-wrapping the bytes for symmetry with
 *       {@link de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter}) is
 *       caught at unit-test time, not after the column has prod data.</li>
 *   <li>Wrong-key + tampered-ciphertext cases prove the converter is actually
 *       encrypting + authenticating, not a Base64 no-op masquerading as encryption.</li>
 *   <li>Sealed-type polymorphism for {@link CredentialBundle} round-trips ALL three
 *       variants — adding a 4th permits without the matching {@code @JsonSubTypes.Type}
 *       fails serialization at runtime, so we lock the existing three here.</li>
 * </ul>
 */
@DisplayName("CredentialBundleConverter — AES-GCM round-trip")
class CredentialBundleConverterTest extends BaseUnitTest {

    /** Stable 32-char key used for the "real" enabled converter under test. */
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    /** Different 32-char key — proves wrong-key decryption fails. */
    private static final String OTHER_KEY = "ABCDEFabcdef0123ABCDEFabcdef0123";

    private static CredentialBundleConverter enabled() {
        return new CredentialBundleConverter(KEY, "dev");
    }

    @Nested
    @DisplayName("Round-trips")
    class RoundTrips {

        @Test
        @DisplayName("BearerToken with and without expiry round-trips")
        void bearerToken_roundTrips() {
            CredentialBundleConverter converter = enabled();
            BearerToken withExpiry = new BearerToken("glpat-Q1w2E3r4T5y6", Instant.parse("2030-01-01T00:00:00Z"));
            BearerToken withoutExpiry = new BearerToken("xoxb-12345-67890-abcdef", null);

            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(withExpiry)))
                .isEqualTo(withExpiry);
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(withoutExpiry)))
                .isEqualTo(withoutExpiry);
        }

        @Test
        @DisplayName("BearerToken with unicode + control characters round-trips losslessly")
        void bearerToken_specialChars_roundTrips() {
            CredentialBundleConverter converter = enabled();
            // Throw the kitchen sink at it — backslashes, quotes, tabs, emoji, RTL,
            // and a literal interpolation-marker (the kind of thing that bit the agent
            // pipeline historically).
            String weird = "xoxb-\"backslash\\quote\" \tTAB‫RTL‬ 🚀 \\(notSwiftInterp)";
            BearerToken tok = new BearerToken(weird, null);

            BearerToken decoded = (BearerToken) converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(tok));
            assertThat(decoded.token()).isEqualTo(weird);
        }

        @Test
        @DisplayName("GithubAppCredential round-trips")
        void githubAppCredential_roundTrips() {
            CredentialBundleConverter converter = enabled();
            GithubAppCredential bundle = new GithubAppCredential(987654L, "12345");

            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(bundle)))
                .isEqualTo(bundle);
        }

        @Test
        @DisplayName("OAuthSession with and without refresh round-trips")
        void oauthSession_roundTrips() {
            CredentialBundleConverter converter = enabled();
            OAuthSession withRefresh = new OAuthSession(
                "ya29.access-token-here", "refresh-token-xyz", Instant.parse("2030-06-15T12:00:00Z"));
            OAuthSession withoutRefresh = new OAuthSession("opaque-access", null, null);

            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(withRefresh)))
                .isEqualTo(withRefresh);
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(withoutRefresh)))
                .isEqualTo(withoutRefresh);
        }

        @Test
        @DisplayName("Polymorphic discriminator is honoured — encode-as-Bundle, decode-as-Bundle")
        void polymorphism_preservesVariant() {
            CredentialBundleConverter converter = enabled();
            CredentialBundle asInterface = new GithubAppCredential(42L, "appid");

            CredentialBundle decoded = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(asInterface));
            assertThat(decoded).isInstanceOf(GithubAppCredential.class).isEqualTo(asInterface);
        }

        @Test
        @DisplayName("Two encrypts of the same plaintext produce different ciphertexts (random IV)")
        void encryptIsNonDeterministic() {
            CredentialBundleConverter converter = enabled();
            BearerToken bundle = new BearerToken("same-input-twice", null);

            byte[] first = converter.convertToDatabaseColumn(bundle);
            byte[] second = converter.convertToDatabaseColumn(bundle);

            assertThat(first).isNotEqualTo(second);
            // Both decrypt back to the same plaintext though.
            assertThat(converter.convertToEntityAttribute(first)).isEqualTo(bundle);
            assertThat(converter.convertToEntityAttribute(second)).isEqualTo(bundle);
        }
    }

    @Nested
    @DisplayName("Nulls + edge cases")
    class Nulls {

        @Test
        @DisplayName("null in → null out, both directions")
        void nullPassesThrough() {
            CredentialBundleConverter converter = enabled();
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("ALGORITHM_TAG matches what Connection.setCredentials stamps — keeps the contract single-sourced")
        void algorithmTag_isExposedConstant() {
            assertThat(CredentialBundleConverter.ALGORITHM_TAG).isEqualTo("aesgcm-v1");
        }
    }

    @Nested
    @DisplayName("Security properties — encryption is real, not no-op")
    class Security {

        @Test
        @DisplayName("Decrypt with the wrong key throws — proves the bytes are encrypted, not a Jackson dump")
        void wrongKey_throws() {
            CredentialBundleConverter writer = enabled();
            CredentialBundleConverter reader = new CredentialBundleConverter(OTHER_KEY, "dev");
            byte[] ciphertext = writer.convertToDatabaseColumn(new BearerToken("secret", null));

            assertThatThrownBy(() -> reader.convertToEntityAttribute(ciphertext))
                .isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("Decrypt of a flipped-bit ciphertext throws (AES-GCM auth tag check)")
        void tamperedCiphertext_throws() {
            CredentialBundleConverter converter = enabled();
            byte[] ciphertext = converter.convertToDatabaseColumn(new BearerToken("secret", null));
            byte[] tampered = Arrays.copyOf(ciphertext, ciphertext.length);
            // Flip a bit in the encrypted body (skip past the 12-byte IV so we hit ciphertext).
            tampered[tampered.length - 1] ^= 0x01;

            assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("Decrypt of truncated ciphertext throws cleanly (no array-bounds blow-up)")
        void tooShortCiphertext_throws() {
            CredentialBundleConverter converter = enabled();
            byte[] tooShort = new byte[10]; // less than the 12-byte IV

            assertThatThrownBy(() -> converter.convertToEntityAttribute(tooShort))
                .isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("Ciphertext starts with the 12-byte IV, doesn't carry a text-prefix like EncryptedStringConverter")
        void ciphertextLayoutIsRawBytes() {
            CredentialBundleConverter converter = enabled();
            byte[] ciphertext = converter.convertToDatabaseColumn(new BearerToken("x", null));

            // 12-byte IV + at least the auth tag + 1 byte of payload — bound below.
            assertThat(ciphertext.length).isGreaterThan(12 + 16);
            // No "ENC:" text prefix; the column is BYTEA, not TEXT.
            assertThat(ciphertext[0]).matches(b -> true, "any random IV byte allowed");
        }
    }

    @Nested
    @DisplayName("Disabled mode — refuses to silently store plaintext")
    class Disabled {

        @Test
        @DisplayName("No-key converter throws on write (refuses silent plaintext persistence)")
        void disabled_writeThrows() {
            CredentialBundleConverter disabled = new CredentialBundleConverter("", "dev");
            assertThat(disabled.isEnabled()).isFalse();

            assertThatThrownBy(() -> disabled.convertToDatabaseColumn(new BearerToken("x", null)))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("not enabled");
        }

        @Test
        @DisplayName("No-key converter throws on read (refuses to pretend a ciphertext is plaintext JSON)")
        void disabled_readThrows() {
            CredentialBundleConverter disabled = new CredentialBundleConverter("", "dev");
            byte[] anyBytes = new byte[32];

            assertThatThrownBy(() -> disabled.convertToEntityAttribute(anyBytes))
                .isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("No-key converter still treats null as null in both directions")
        void disabled_nullStillPassesThrough() {
            CredentialBundleConverter disabled = new CredentialBundleConverter("", "dev");
            assertThat(disabled.convertToDatabaseColumn(null)).isNull();
            assertThat(disabled.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("Production profile + missing key throws at construction (fail-fast)")
        void prodProfile_missingKey_failsFast() {
            assertThatThrownBy(() -> new CredentialBundleConverter("", "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required in production");
        }

        @Test
        @DisplayName("Wrong-length key throws at construction (32-char/256-bit invariant)")
        void wrongLengthKey_failsFast() {
            assertThatThrownBy(() -> new CredentialBundleConverter("short", "dev"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 32 characters");
        }
    }

    @Nested
    @DisplayName("v2 per-row AAD — closes cross-row substitution CVE")
    class PerRowAad {

        private static final EncryptionContext CTX_A = new EncryptionContext(
            42L, de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind.GITHUB,
            "installation-100", "connection.credentials_encrypted");

        private static final EncryptionContext CTX_B = new EncryptionContext(
            42L, de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind.GITHUB,
            "installation-999", "connection.credentials_encrypted");

        @Test
        @DisplayName("v2 round-trip with the same context returns the original bundle")
        void v2_sameRow_roundTrips() {
            CredentialBundleConverter c = enabled();
            BearerToken bundle = new BearerToken("ghp_secretRowA", null);

            byte[] blob = c.encrypt(bundle, CTX_A);
            assertThat(blob[0]).as("first byte is v2 format marker").isEqualTo(CredentialBundleConverter.FORMAT_VERSION_V2);
            assertThat(c.decrypt(blob, CTX_A)).isEqualTo(bundle);
        }

        @Test
        @DisplayName("v2 blob from row A cannot be decrypted with row B's context — AEADBadTagException")
        void v2_crossRowSubstitution_throwsAead() {
            CredentialBundleConverter c = enabled();
            byte[] blobForA = c.encrypt(new BearerToken("ghp_secretRowA", null), CTX_A);

            // This is the CVE we are closing: attacker swaps row A's ciphertext into row B.
            assertThatThrownBy(() -> c.decrypt(blobForA, CTX_B))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("decryption failed")
                .hasRootCauseInstanceOf(javax.crypto.AEADBadTagException.class);
        }

        @Test
        @DisplayName("v2 with wrong workspaceId is rejected")
        void v2_wrongWorkspaceId_throws() {
            CredentialBundleConverter c = enabled();
            byte[] blob = c.encrypt(new BearerToken("s", null), CTX_A);
            EncryptionContext other = new EncryptionContext(
                99L, CTX_A.kind(), CTX_A.instanceKey(), CTX_A.columnFqn());

            assertThatThrownBy(() -> c.decrypt(blob, other)).isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("v2 with wrong kind is rejected")
        void v2_wrongKind_throws() {
            CredentialBundleConverter c = enabled();
            byte[] blob = c.encrypt(new BearerToken("s", null), CTX_A);
            EncryptionContext other = new EncryptionContext(
                CTX_A.workspaceId(), de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind.GITLAB,
                CTX_A.instanceKey(), CTX_A.columnFqn());

            assertThatThrownBy(() -> c.decrypt(blob, other)).isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("v2 with wrong columnFqn is rejected — guards against a future second encrypted column")
        void v2_wrongColumnFqn_throws() {
            CredentialBundleConverter c = enabled();
            byte[] blob = c.encrypt(new BearerToken("s", null), CTX_A);
            EncryptionContext other = new EncryptionContext(
                CTX_A.workspaceId(), CTX_A.kind(), CTX_A.instanceKey(), "connection.some_other_column");

            assertThatThrownBy(() -> c.decrypt(blob, other)).isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("v2 null instanceKey (pre-bind OAuth slot) round-trips with the same null context")
        void v2_nullInstanceKey_roundTrips() {
            CredentialBundleConverter c = enabled();
            EncryptionContext pending = new EncryptionContext(
                42L, de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind.GITHUB,
                null, "connection.credentials_encrypted");
            BearerToken bundle = new BearerToken("pending-token", null);

            byte[] blob = c.encrypt(bundle, pending);
            assertThat(c.decrypt(blob, pending)).isEqualTo(bundle);
        }

        @Test
        @DisplayName("v2 tampered ciphertext byte → AEADBadTagException")
        void v2_tamperedCiphertext_throws() {
            CredentialBundleConverter c = enabled();
            byte[] blob = c.encrypt(new BearerToken("s", null), CTX_A);
            byte[] tampered = Arrays.copyOf(blob, blob.length);
            tampered[tampered.length - 1] ^= 0x01;

            assertThatThrownBy(() -> c.decrypt(tampered, CTX_A)).isInstanceOf(EncryptionException.class);
        }

        @Test
        @DisplayName("v1 blob is read by the v2-aware decrypt — context arg is ignored (legacy compat)")
        void v1_blob_decryptsThroughV2Api() {
            CredentialBundleConverter c = enabled();
            // Write via the v1 attribute-converter API (matches the legacy backfill path).
            byte[] v1Blob = c.convertToDatabaseColumn(new BearerToken("legacy-token", null));
            assertThat(v1Blob[0]).isEqualTo(CredentialBundleConverter.FORMAT_VERSION_V1);

            // Any context passed in is ignored for v1 — proves the tolerant-read branch.
            assertThat(c.decrypt(v1Blob, CTX_A)).isEqualTo(new BearerToken("legacy-token", null));
            assertThat(c.decrypt(v1Blob, CTX_B)).isEqualTo(new BearerToken("legacy-token", null));
        }

        @Test
        @DisplayName("Tolerant attributeConverter read REJECTS v2 blobs — they require per-row context")
        void v2_blob_attributeConverterReadRejects() {
            CredentialBundleConverter c = enabled();
            byte[] v2Blob = c.encrypt(new BearerToken("s", null), CTX_A);

            assertThatThrownBy(() -> c.convertToEntityAttribute(v2Blob))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("requires per-row EncryptionContext");
        }

        @Test
        @DisplayName("Unknown version byte (e.g. 0x03) → Unsupported, NOT AEADBadTagException")
        void unknownVersion_throwsUnsupported() {
            CredentialBundleConverter c = enabled();
            byte[] blob = new byte[1 + 12 + 17];
            blob[0] = 0x03;

            assertThatThrownBy(() -> c.decrypt(blob, CTX_A))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("Unsupported");
        }

        @Test
        @DisplayName("isLegacyV1 reports true for v1 blobs, false for v2 and null")
        void isLegacyV1_introspection() {
            CredentialBundleConverter c = enabled();
            byte[] v1 = c.convertToDatabaseColumn(new BearerToken("x", null));
            byte[] v2 = c.encrypt(new BearerToken("x", null), CTX_A);

            assertThat(CredentialBundleConverter.isLegacyV1(v1)).isTrue();
            assertThat(CredentialBundleConverter.isLegacyV1(v2)).isFalse();
            assertThat(CredentialBundleConverter.isLegacyV1(null)).isFalse();
            assertThat(CredentialBundleConverter.isLegacyV1(new byte[0])).isFalse();
        }

        @Test
        @DisplayName("EncryptionContext rejects null kind / blank columnFqn at construction")
        void encryptionContext_validatesInputs() {
            assertThatThrownBy(() -> new EncryptionContext(1L, null, "k", "col"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new EncryptionContext(
                    1L, de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind.GITHUB, "k", ""))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("AAD bytes are stable for the same context (deterministic serialisation)")
        void aad_isStable() {
            byte[] first = CTX_A.toAad();
            byte[] second = CTX_A.toAad();
            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("AAD bytes differ across rows (sanity — no field collision)")
        void aad_differsAcrossRows() {
            assertThat(CTX_A.toAad()).isNotEqualTo(CTX_B.toAad());
        }
    }
}
