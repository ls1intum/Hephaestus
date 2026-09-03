package de.tum.cit.aet.hephaestus.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * The Compose files forward the whole {@code hephaestus.security} block, so a self-hosted install
 * that configured none of it still starts the container with those variables set to the empty
 * string. Binding therefore has to agree with the operator that they configured nothing: a fresh
 * install must boot, and the rotation pair must still fail loudly when only one half is real.
 *
 * <p>These bind through a real {@link SystemEnvironmentPropertySource}, because the defect this
 * covers only exists in the environment: a blank {@code String} binds as {@code ""} while the
 * {@code Integer} beside it binds as {@code null}, which is what made "both or neither" reject
 * neither.
 */
class SecurityPropertiesEnvBindingTest extends BaseUnitTest {

    @Test
    void shouldBindNoRotationWhenTheForwardedRotationKeysAreBlank() {
        SecurityProperties properties = bindWith(Map.of(
                "HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY",
                "0123456789abcdef0123456789abcdef",
                "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY",
                "",
                "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY_VERSION",
                ""));

        assertThat(properties.priorCredentialEncryptionKey())
                .as("a forwarded-but-empty key is unset, not a half-finished rotation")
                .isNull();
        assertThat(properties.priorCredentialEncryptionKeyVersion()).isNull();
    }

    @Test
    void shouldBindNoKeysWhenEveryForwardedValueIsBlank() {
        SecurityProperties properties = bindWith(Map.of(
                "HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY",
                "",
                "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY",
                "",
                "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY_VERSION",
                ""));

        assertThat(properties.credentialEncryptionKey()).isNull();
        assertThat(properties.priorCredentialEncryptionKey()).isNull();
    }

    @Test
    void shouldRejectAPriorKeyWhoseVersionIsBlank() {
        assertThatThrownBy(() -> bindWith(Map.of(
                        "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY",
                        "fedcba9876543210fedcba9876543210",
                        "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY_VERSION",
                        "")))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be configured together");
    }

    @Test
    void shouldRejectAPriorKeyVersionWhoseKeyIsBlank() {
        assertThatThrownBy(() -> bindWith(Map.of(
                        "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY",
                        "",
                        "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY_VERSION",
                        "2")))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be configured together");
    }

    @Test
    void shouldRejectRotationEnabledWhenTheKeysItRotatesAreBlank() {
        assertThatThrownBy(() -> bindWith(Map.of(
                        "HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY",
                        "",
                        "HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY",
                        "",
                        "HEPHAESTUS_SECURITY_CREDENTIAL_ROTATION_ENABLED",
                        "true")))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credential rotation requires both active and prior encryption keys");
    }

    private static SecurityProperties bindWith(Map<String, Object> environmentVariables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));

        return Binder.get(environment).bindOrCreate("hephaestus.security", SecurityProperties.class);
    }
}
