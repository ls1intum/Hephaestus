package de.tum.in.www1.hephaestus.core.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ServerUrlValidator")
class ServerUrlValidatorTest extends BaseUnitTest {

    @Nested
    @DisplayName("scheme validation")
    class SchemeValidation {

        @Test
        void rejectsHttpUrl() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("http://gitlab.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        }

        @Test
        void rejectsFileUrl() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        }

        @Test
        void rejectsFtpUrl() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("ftp://gitlab.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        }

        @Test
        void rejectsGopherUrl() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("gopher://gitlab.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        }
    }

    @Nested
    @DisplayName("hostname validation")
    class HostnameValidation {

        @Test
        void rejectsLocalhost() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://localhost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
        }

        @Test
        void rejectsLoopbackIp() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
        }

        @Test
        void rejectsIpv6Loopback() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://[::1]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
        }

        @Test
        void rejectsZeroAddress() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://0.0.0.0")).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        void rejectsCloudMetadataIp() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://169.254.169.254"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback or reserved");
        }

        @Test
        void rejectsGoogleMetadataHostname() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://metadata.google.internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
        }

        @Test
        void rejectsPrivateIpRange() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://10.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site-local");
        }

        @Test
        void rejectsLinkLocalIpRange() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://169.254.1.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("link-local");
        }
    }

    @Nested
    @DisplayName("TLD validation")
    class TldValidation {

        @Test
        void rejectsLocalTld() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://gitlab.local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private TLD");
        }

        @Test
        void rejectsInternalTld() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://gitlab.internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private TLD");
        }

        @Test
        void rejectsLocalhostTld() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://myapp.localhost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private TLD");
        }
    }

    @Nested
    @DisplayName("URL structure validation")
    class StructureValidation {

        @Test
        void rejectsUrlWithUserinfo() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://evil@gitlab.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentials");
        }

        @Test
        void rejectsUrlWithPath() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://gitlab.example.com/redirect"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
        }

        @Test
        void rejectsBlankUrl() {
            assertThatThrownBy(() -> ServerUrlValidator.validate(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        }

        @Test
        void rejectsNullUrl() {
            assertThatThrownBy(() -> ServerUrlValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        }

        @Test
        void rejectsMalformedUrl() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("not a url at all")).isInstanceOf(
                IllegalArgumentException.class
            );
        }
    }

    @Nested
    @DisplayName("valid URLs")
    class ValidUrls {

        @Test
        void acceptsGitLabCom() {
            assertThatCode(() -> ServerUrlValidator.validate("https://gitlab.com")).doesNotThrowAnyException();
        }

        @Test
        void acceptsSelfHostedGitLab() {
            assertThatCode(() -> ServerUrlValidator.validate("https://gitlab.example.com")).doesNotThrowAnyException();
        }

        @Test
        void acceptsSubdomainGitLab() {
            assertThatCode(() -> ServerUrlValidator.validate("https://git.mycompany.org")).doesNotThrowAnyException();
        }

        @Test
        void acceptsUrlWithTrailingSlash() {
            // Trailing slash makes path = "/", which is allowed
            assertThatCode(() -> ServerUrlValidator.validate("https://gitlab.example.com/")).doesNotThrowAnyException();
        }

        @Test
        void acceptsUrlWithPort() {
            assertThatCode(() ->
                ServerUrlValidator.validate("https://gitlab.example.com:8443")
            ).doesNotThrowAnyException();
        }
    }
}
