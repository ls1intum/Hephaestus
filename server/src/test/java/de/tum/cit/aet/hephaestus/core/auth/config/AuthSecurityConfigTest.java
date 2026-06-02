package de.tum.cit.aet.hephaestus.core.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Pins the fail-closed contract of {@link AuthSecurityConfig#resolveStateCookieKey}: in production a
 * blank state-cookie key is fatal (an ephemeral per-pod key would silently abandon in-flight logins
 * on every restart and differ per replica), while dev/CI tolerate it with a generated key. Mirrors
 * {@code JwtSigningKeySealer}'s prod fail-fast. Fails if either guard is removed.
 */
class AuthSecurityConfigTest extends BaseUnitTest {

    private static AuthProperties propsWithKey(String key) {
        AuthProperties properties = mock(AuthProperties.class);
        when(properties.stateCookieKey()).thenReturn(key);
        return properties;
    }

    private static String base64Key(int bytes) {
        return Base64.getEncoder().encodeToString(new byte[bytes]);
    }

    @Test
    void blankKeyInProdFailsClosed() {
        assertThatThrownBy(() -> AuthSecurityConfig.resolveStateCookieKey(propsWithKey(""), true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("required in production");
    }

    @Test
    void blankKeyOutsideProdGeneratesEphemeral32ByteKey() {
        byte[] key = AuthSecurityConfig.resolveStateCookieKey(propsWithKey(""), false);

        assertThat(key).hasSize(32);
    }

    @Test
    void configuredKeyIsDecodedAndUsedEvenInProd() {
        byte[] key = AuthSecurityConfig.resolveStateCookieKey(propsWithKey(base64Key(32)), true);

        assertThat(key).hasSize(32);
    }

    @Test
    void configuredKeyOfWrongLengthIsRejected() {
        assertThatThrownBy(() -> AuthSecurityConfig.resolveStateCookieKey(propsWithKey(base64Key(16)), false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 bytes");
    }
}
