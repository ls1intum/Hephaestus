package de.tum.cit.aet.hephaestus.integration.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.security.EncryptionException;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.CredentialBundle;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.GithubAppCredential;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.OAuthSession;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CredentialBundleConverterTest extends BaseUnitTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_KEY = "ABCDEFabcdef0123ABCDEFabcdef0123";

    private static final EncryptionContext CTX_A = new EncryptionContext(
        42L,
        IntegrationKind.GITHUB,
        "installation-100",
        "connection.credentials_encrypted"
    );
    private static final EncryptionContext CTX_B = new EncryptionContext(
        42L,
        IntegrationKind.GITHUB,
        "installation-999",
        "connection.credentials_encrypted"
    );

    private static CredentialBundleConverter enabled() {
        return new CredentialBundleConverter(KEY, "dev");
    }

    @Nested
    class RoundTrips {

        @Test
        void bearerToken_roundTrips() {
            CredentialBundleConverter c = enabled();
            BearerToken withExpiry = new BearerToken("glpat-Q1w2E3r4T5y6", Instant.parse("2030-01-01T00:00:00Z"));
            BearerToken withoutExpiry = new BearerToken("xoxb-12345-67890-abcdef", null);

            assertThat(c.decrypt(c.encrypt(withExpiry, CTX_A), CTX_A)).isEqualTo(withExpiry);
            assertThat(c.decrypt(c.encrypt(withoutExpiry, CTX_A), CTX_A)).isEqualTo(withoutExpiry);
        }

        @Test
        void bearerToken_specialChars_roundTrips() {
            CredentialBundleConverter c = enabled();
            String weird = "xoxb-\"backslash\\quote\" \tTAB‫RTL‬ 🚀 \\(notSwiftInterp)";
            BearerToken tok = new BearerToken(weird, null);

            BearerToken decoded = (BearerToken) c.decrypt(c.encrypt(tok, CTX_A), CTX_A);
            assertThat(decoded.token()).isEqualTo(weird);
        }

        @Test
        void githubAppCredential_roundTrips() {
            CredentialBundleConverter c = enabled();
            GithubAppCredential bundle = new GithubAppCredential(987654L, "12345");
            assertThat(c.decrypt(c.encrypt(bundle, CTX_A), CTX_A)).isEqualTo(bundle);
        }

        @Test
        void oauthSession_roundTrips() {
            CredentialBundleConverter c = enabled();
            OAuthSession withRefresh = new OAuthSession(
                "ya29.access-token-here",
                "refresh-token-xyz",
                Instant.parse("2030-06-15T12:00:00Z")
            );
            OAuthSession withoutRefresh = new OAuthSession("opaque-access", null, null);

            assertThat(c.decrypt(c.encrypt(withRefresh, CTX_A), CTX_A)).isEqualTo(withRefresh);
            assertThat(c.decrypt(c.encrypt(withoutRefresh, CTX_A), CTX_A)).isEqualTo(withoutRefresh);
        }

        @Test
        void polymorphism_preservesVariant() {
            CredentialBundleConverter c = enabled();
            CredentialBundle asInterface = new GithubAppCredential(42L, "appid");

            CredentialBundle decoded = c.decrypt(c.encrypt(asInterface, CTX_A), CTX_A);
            assertThat(decoded).isInstanceOf(GithubAppCredential.class).isEqualTo(asInterface);
        }

        @Test
        void encryptIsNonDeterministic() {
            CredentialBundleConverter c = enabled();
            BearerToken bundle = new BearerToken("same-input-twice", null);

            byte[] first = c.encrypt(bundle, CTX_A);
            byte[] second = c.encrypt(bundle, CTX_A);

            assertThat(first).isNotEqualTo(second);
            assertThat(c.decrypt(first, CTX_A)).isEqualTo(bundle);
            assertThat(c.decrypt(second, CTX_A)).isEqualTo(bundle);
        }

        @Test
        void nullInstanceKey_roundTrips() {
            CredentialBundleConverter c = enabled();
            EncryptionContext pending = new EncryptionContext(
                42L,
                IntegrationKind.GITHUB,
                null,
                "connection.credentials_encrypted"
            );
            BearerToken bundle = new BearerToken("pending-token", null);

            byte[] blob = c.encrypt(bundle, pending);
            assertThat(c.decrypt(blob, pending)).isEqualTo(bundle);
        }
    }

    @Nested
    class Format {

        @Test
        void encryptedBlob_isV2Tagged_andHasAuthTag() {
            CredentialBundleConverter c = enabled();
            byte[] blob = c.encrypt(new BearerToken("x", null), CTX_A);
            // 1-byte version + 12-byte IV + 16-byte GCM tag at minimum.
            assertThat(blob[0]).isEqualTo(CredentialBundleConverter.FORMAT_VERSION_V2);
            assertThat(blob.length).isGreaterThan(1 + 12 + 16);
        }

        @Test
        void unknownVersion_throwsUnsupported() {
            CredentialBundleConverter c = enabled();
            byte[] blob = new byte[1 + 12 + 17];
            blob[0] = 0x03;

            assertThatThrownBy(() -> c.decrypt(blob, CTX_A))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("Unsupported");
        }

        @Test
        void v1_blob_rejected() {
            CredentialBundleConverter c = enabled();
            byte[] v1Shaped = new byte[1 + 12 + 17];
            v1Shaped[0] = 0x01;

            assertThatThrownBy(() -> c.decrypt(v1Shaped, CTX_A))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("Unsupported");
        }
    }

    @Nested
    class Security {

        @Test
        void wrongKey_throws() {
            CredentialBundleConverter writer = enabled();
            CredentialBundleConverter reader = new CredentialBundleConverter(OTHER_KEY, "dev");
            byte[] ciphertext = writer.encrypt(new BearerToken("secret", null), CTX_A);

            assertThatThrownBy(() -> reader.decrypt(ciphertext, CTX_A)).isInstanceOf(EncryptionException.class);
        }

        @Test
        void tamperedCiphertext_throws() {
            CredentialBundleConverter c = enabled();
            byte[] ciphertext = c.encrypt(new BearerToken("secret", null), CTX_A);
            byte[] tampered = Arrays.copyOf(ciphertext, ciphertext.length);
            tampered[tampered.length - 1] ^= 0x01;

            assertThatThrownBy(() -> c.decrypt(tampered, CTX_A)).isInstanceOf(EncryptionException.class);
        }

        @Test
        void tooShortCiphertext_throws() {
            CredentialBundleConverter c = enabled();
            byte[] tooShort = new byte[10];

            assertThatThrownBy(() -> c.decrypt(tooShort, CTX_A)).isInstanceOf(EncryptionException.class);
        }

        /**
         * AAD binds the ciphertext to {@code (workspaceId, kind, instanceKey, columnFqn)}.
         * Substituting any one of those four into the decrypt context must fail.
         */
        @Test
        void crossRowSubstitution_throwsAead() {
            CredentialBundleConverter c = enabled();
            byte[] blobForA = c.encrypt(new BearerToken("ghp_secretRowA", null), CTX_A);

            // wrong instanceKey
            assertThatThrownBy(() -> c.decrypt(blobForA, CTX_B))
                .isInstanceOf(EncryptionException.class)
                .hasRootCauseInstanceOf(javax.crypto.AEADBadTagException.class);
            // wrong workspaceId
            assertThatThrownBy(() ->
                c.decrypt(blobForA, new EncryptionContext(99L, CTX_A.kind(), CTX_A.instanceKey(), CTX_A.columnFqn()))
            ).isInstanceOf(EncryptionException.class);
            // wrong kind
            assertThatThrownBy(() ->
                c.decrypt(
                    blobForA,
                    new EncryptionContext(
                        CTX_A.workspaceId(),
                        IntegrationKind.GITLAB,
                        CTX_A.instanceKey(),
                        CTX_A.columnFqn()
                    )
                )
            ).isInstanceOf(EncryptionException.class);
            // wrong columnFqn
            assertThatThrownBy(() ->
                c.decrypt(
                    blobForA,
                    new EncryptionContext(
                        CTX_A.workspaceId(),
                        CTX_A.kind(),
                        CTX_A.instanceKey(),
                        "connection.some_other_column"
                    )
                )
            ).isInstanceOf(EncryptionException.class);
        }
    }

    @Nested
    class AttributeConverterAPI {

        @Test
        void nullPassesThrough() {
            CredentialBundleConverter c = enabled();
            assertThat(c.convertToDatabaseColumn(null)).isNull();
            assertThat(c.convertToEntityAttribute(null)).isNull();
        }

        @Test
        void contextLessWrite_throws() {
            CredentialBundleConverter c = enabled();
            assertThatThrownBy(() -> c.convertToDatabaseColumn(new BearerToken("x", null)))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("requires per-row EncryptionContext");
        }

        @Test
        void contextLessRead_rejectsV2Blobs() {
            CredentialBundleConverter c = enabled();
            byte[] v2Blob = c.encrypt(new BearerToken("s", null), CTX_A);

            assertThatThrownBy(() -> c.convertToEntityAttribute(v2Blob))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("requires per-row EncryptionContext");
        }
    }

    @Nested
    class Disabled {

        @Test
        void disabled_writeThrows() {
            CredentialBundleConverter disabled = new CredentialBundleConverter("", "dev");
            assertThat(disabled.isEnabled()).isFalse();

            assertThatThrownBy(() -> disabled.encrypt(new BearerToken("x", null), CTX_A))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("not enabled");
        }

        @Test
        void disabled_readThrows() {
            CredentialBundleConverter disabled = new CredentialBundleConverter("", "dev");
            byte[] anyBytes = new byte[32];
            anyBytes[0] = CredentialBundleConverter.FORMAT_VERSION_V2;

            assertThatThrownBy(() -> disabled.decrypt(anyBytes, CTX_A)).isInstanceOf(EncryptionException.class);
        }

        @Test
        void disabled_nullStillPassesThrough() {
            CredentialBundleConverter disabled = new CredentialBundleConverter("", "dev");
            assertThat(disabled.convertToDatabaseColumn(null)).isNull();
            assertThat(disabled.convertToEntityAttribute(null)).isNull();
        }

        @Test
        void prodProfile_missingKey_failsFast() {
            assertThatThrownBy(() -> new CredentialBundleConverter("", "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required in production");
        }

        @Test
        void wrongLengthKey_failsFast() {
            assertThatThrownBy(() -> new CredentialBundleConverter("short", "dev"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 32 characters");
        }
    }
}
