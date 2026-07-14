package de.tum.cit.aet.hephaestus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties.LoginProviderSeed;
import de.tum.cit.aet.hephaestus.core.auth.provider.LoginProvider.ProviderType;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@code apiBasePath} feeds string-concatenated OAuth URLs ({@code {baseUrl}} + path + callback), so a
 * stray missing/duplicated slash silently breaks login. Pin the constructor normalization that makes
 * {@code api}, {@code /api} and {@code /api/} equivalent and collapses blank/{@code /} to root.
 *
 * <p>Also pins the seed-gate contract: a provider slot is only "configured" with BOTH credential halves.
 */
class AuthPropertiesTest extends BaseUnitTest {

    @ParameterizedTest
    @CsvSource(
        value = {
            "/api | /api",
            "api | /api",
            "//api | /api",
            "/api/ | /api",
            "/api/v2/ | /api/v2",
            "'' | ''",
            "/ | ''",
            "'  /api/  ' | /api",
        },
        delimiterString = "|",
        emptyValue = ""
    )
    void apiBasePath_isNormalizedToLeadingSlashNoTrailingSlash(String raw, String expected) {
        assertThat(AuthPropertiesFixture.withApiBasePath(raw).apiBasePath()).isEqualTo(expected);
    }

    /**
     * The seed gate. A half-filled slot (client id set, secret forgotten — or the reverse) used to pass
     * {@code configured()}, seeding an ENABLED provider whose every OAuth exchange dies at the token
     * endpoint. Both halves or nothing. Holds for every provider slot, not just Outline.
     */
    @Nested
    class SeedConfiguredGate {

        private static LoginProviderSeed seed(ProviderType type, String clientId, String clientSecret) {
            return new LoginProviderSeed(type, "https://example.test", clientId, clientSecret, "");
        }

        @ParameterizedTest
        @EnumSource(ProviderType.class)
        @DisplayName("both credential halves present → configured")
        void bothHalves_isConfigured(ProviderType type) {
            LoginProviderSeed both = seed(type, "client-id", "client-secret");

            assertThat(both.configured()).isTrue();
            assertThat(both.partiallyConfigured()).isFalse();
            assertThat(both.missingCredentialField()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(ProviderType.class)
        @DisplayName("client id without a secret is NOT configured — it would seed a provider that cannot exchange")
        void clientIdWithoutSecret_isNotConfigured(ProviderType type) {
            LoginProviderSeed halfFilled = seed(type, "client-id", "  ");

            assertThat(halfFilled.configured()).isFalse();
            assertThat(halfFilled.partiallyConfigured()).isTrue();
            assertThat(halfFilled.missingCredentialField()).isEqualTo("client-secret");
        }

        @ParameterizedTest
        @EnumSource(ProviderType.class)
        @DisplayName("a secret without a client id is NOT configured either")
        void secretWithoutClientId_isNotConfigured(ProviderType type) {
            LoginProviderSeed halfFilled = seed(type, "", "client-secret");

            assertThat(halfFilled.configured()).isFalse();
            assertThat(halfFilled.partiallyConfigured()).isTrue();
            assertThat(halfFilled.missingCredentialField()).isEqualTo("client-id");
        }

        @Test
        @DisplayName("an entirely blank slot is silence, not a misconfiguration — credential-less pods still boot")
        void blankSlot_isNeitherConfiguredNorPartial() {
            LoginProviderSeed blank = seed(ProviderType.OUTLINE, "", "");

            assertThat(blank.configured()).isFalse();
            assertThat(blank.partiallyConfigured()).isFalse();
        }
    }
}
