package de.tum.cit.aet.hephaestus.core.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerUrlValidatorTest extends BaseUnitTest {

    @Nested
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
    class NonCanonicalNumericHostBypass {

        // InetAddress.getByName resolves these to real private/loopback/metadata addresses, but their
        // textual form != the canonical getHostAddress() — the classic SSRF deny-list bypass.

        @Test
        void rejectsDecimalIntegerLoopback() {
            // 2130706433 == 127.0.0.1
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://2130706433"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-canonical numeric host");
        }

        @Test
        void rejectsDecimalIntegerCloudMetadata() {
            // 2852039166 == 169.254.169.254 (cloud metadata)
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://2852039166"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-canonical numeric host");
        }

        @Test
        void rejectsDecimalIntegerPrivate() {
            // 3232235521 == 192.168.0.1
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://3232235521"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-canonical numeric host");
        }

        @Test
        void rejectsHexEncodedIp() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://0x7f000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-canonical numeric host");
        }

        @Test
        void rejectsOctalEncodedIp() {
            assertThatThrownBy(() -> ServerUrlValidator.validate("https://0177.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-canonical numeric host");
        }

        @Test
        void stillAcceptsCanonicalPublicIpv4() {
            assertThatCode(() -> ServerUrlValidator.validate("https://8.8.8.8")).doesNotThrowAnyException();
        }

        @Test
        void stillAcceptsPublicIpv6() {
            assertThatCode(() -> ServerUrlValidator.validate("https://[2001:db8::1]")).doesNotThrowAnyException();
        }
    }

    @Nested
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
