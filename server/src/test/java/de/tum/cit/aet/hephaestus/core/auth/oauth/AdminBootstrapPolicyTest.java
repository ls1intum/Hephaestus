package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code hephaestus.auth.bootstrap-admins} allowlist matching: exact {@code provider:subject},
 * fail-closed on empty/null, and malformed entries dropped (not booted on, not matched).
 */
class AdminBootstrapPolicyTest extends BaseUnitTest {

    private static AdminBootstrapPolicy policy(String... entries) {
        AuthProperties props = mock(AuthProperties.class);
        when(props.bootstrapAdmins()).thenReturn(entries == null ? null : Arrays.asList(entries));
        return new AdminBootstrapPolicy(props);
    }

    @Test
    void matchesExactProviderSubject() {
        AdminBootstrapPolicy p = policy("github:1234567", "gitlab-lrz:42");
        assertThat(p.shouldPromote("github", "1234567")).isTrue();
        assertThat(p.shouldPromote("gitlab-lrz", "42")).isTrue();
    }

    @Test
    void rejectsNonListedOrCrossProvider() {
        AdminBootstrapPolicy p = policy("github:1234567");
        assertThat(p.shouldPromote("github", "9999999")).isFalse(); // different subject
        assertThat(p.shouldPromote("gitlab-lrz", "1234567")).isFalse(); // same subject, wrong provider
        assertThat(p.shouldPromote("github", "12345")).isFalse(); // not a prefix match
    }

    @Test
    void emptyOrNullAllowlistNeverPromotes() {
        assertThat(policy().shouldPromote("github", "1")).isFalse();
        assertThat(policy((String[]) null).shouldPromote("github", "1")).isFalse();
    }

    @Test
    void nullInputsAreFalse() {
        AdminBootstrapPolicy p = policy("github:1");
        assertThat(p.shouldPromote(null, "1")).isFalse();
        assertThat(p.shouldPromote("github", null)).isFalse();
    }

    @Test
    void trimsWhitespaceAndIgnoresMalformedEntries() {
        // Malformed: blank, no colon, missing subject, missing provider — all dropped, none crash.
        AdminBootstrapPolicy p = policy("  github:1234567  ", "", "noColon", "github:", ":42", "   ");
        assertThat(p.shouldPromote("github", "1234567")).isTrue(); // trimmed entry still matches
        assertThat(p.shouldPromote("github", "")).isFalse(); // "github:" dropped
        assertThat(p.shouldPromote("", "42")).isFalse(); // ":42" dropped
    }

    @Test
    void subjectWithColonsKeepsEverythingAfterFirstColon() {
        // First-colon split: a subject containing ':' is preserved verbatim.
        AdminBootstrapPolicy p = policy("custom:urn:weird:99");
        assertThat(p.shouldPromote("custom", "urn:weird:99")).isTrue();
    }

    @Test
    void parsesAList() {
        List<String> raw = List.of("github:1", "github:2", "gitlab-lrz:3");
        AdminBootstrapPolicy p = policy(raw.toArray(new String[0]));
        assertThat(p.shouldPromote("github", "1")).isTrue();
        assertThat(p.shouldPromote("github", "2")).isTrue();
        assertThat(p.shouldPromote("gitlab-lrz", "3")).isTrue();
    }
}
