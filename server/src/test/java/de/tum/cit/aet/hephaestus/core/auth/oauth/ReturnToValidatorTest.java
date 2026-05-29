package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Open-redirect / log-injection regression suite for {@link ReturnToValidator}. Every abuse case
 * must fall back to {@code "/"}; safe relative paths must pass through unchanged.
 */
class ReturnToValidatorTest extends BaseUnitTest {

    @ParameterizedTest
    @ValueSource(
        strings = {
            "//evil.com", // protocol-relative → external
            "///evil.com",
            "/\\evil.com", // backslash the browser may treat as protocol-relative
            "https://evil.com", // absolute URL
            "http://evil.com",
            "HTTPS://evil.com",
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "file:///etc/passwd",
            "evil.com", // no leading slash
            "\\\\evil.com", // UNC-style
        }
    )
    void rejectsUnsafeReturnTo(String unsafe) {
        assertThat(ReturnToValidator.safeOrFallback(unsafe)).isEqualTo("/");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "/dashboard", "/workspaces/acme/settings", "/path?q=1&b=2", "/path#frag" })
    void acceptsSafeRelativePaths(String safe) {
        assertThat(ReturnToValidator.safeOrFallback(safe)).isEqualTo(safe);
    }

    @Test
    void rejectsControlCharacters() {
        // CRLF (header/log injection), tab, NUL, DEL anywhere -> fallback. Unicode escapes keep the
        // control bytes as plain ASCII source.
        assertThat(ReturnToValidator.safeOrFallback("/path\r\nSet-Cookie: x=y")).isEqualTo("/");
        assertThat(ReturnToValidator.safeOrFallback("/pa\tth")).isEqualTo("/");
        assertThat(ReturnToValidator.safeOrFallback("/pa\u0000th")).isEqualTo("/");
        assertThat(ReturnToValidator.safeOrFallback("/pa\u007Fth")).isEqualTo("/");
    }

    @Test
    void nullAndBlankFallBackToRoot() {
        assertThat(ReturnToValidator.safeOrFallback(null)).isEqualTo("/");
        assertThat(ReturnToValidator.safeOrFallback("")).isEqualTo("/");
        assertThat(ReturnToValidator.safeOrFallback("   ")).isEqualTo("/");
    }
}
