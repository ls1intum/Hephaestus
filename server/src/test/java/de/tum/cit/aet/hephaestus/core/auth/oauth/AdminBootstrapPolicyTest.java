package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.AuthProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code hephaestus.auth.bootstrap-admins} matching: by stable subject ({@code provider:1234567})
 * and by git login ({@code provider:@username}, case-insensitive), fail-closed on empty/null, and
 * malformed entries dropped (not booted on, not matched).
 */
class AdminBootstrapPolicyTest extends BaseUnitTest {

    private static AdminBootstrapPolicy policy(String... entries) {
        AuthProperties props = mock(AuthProperties.class);
        when(props.bootstrapAdmins()).thenReturn(entries == null ? null : Arrays.asList(entries));
        return new AdminBootstrapPolicy(props);
    }

    @Test
    void matchesByStableSubject() {
        AdminBootstrapPolicy p = policy("github:1234567", "gitlab-lrz:42");
        assertThat(p.shouldPromote("github", "1234567", "octocat")).isTrue();
        assertThat(p.shouldPromote("gitlab-lrz", "42", null)).isTrue();
    }

    @Test
    void matchesByUsernameCaseInsensitive() {
        AdminBootstrapPolicy p = policy("gitlab-lrz:@m.mustermann", "github:@Octocat");
        assertThat(p.shouldPromote("gitlab-lrz", "999", "m.mustermann")).isTrue();
        assertThat(p.shouldPromote("github", "888", "octocat")).isTrue(); // listed @Octocat, login octocat
        assertThat(p.shouldPromote("github", "888", "OCTOCAT")).isTrue();
    }

    @Test
    void usernameEntryDoesNotMatchASubjectAndViceVersa() {
        AdminBootstrapPolicy p = policy("github:@octocat");
        // The literal "octocat" must not be matched as a subject, and the @ form must not match a
        // different login.
        assertThat(p.shouldPromote("github", "octocat", "someone-else")).isFalse();
        assertThat(p.shouldPromote("github", "123", "mona")).isFalse();
    }

    @Test
    void rejectsNonListedOrCrossProvider() {
        AdminBootstrapPolicy p = policy("github:1234567", "gitlab-lrz:@jdoe");
        assertThat(p.shouldPromote("github", "9999999", "x")).isFalse(); // different subject
        assertThat(p.shouldPromote("gitlab-lrz", "1234567", "x")).isFalse(); // right subject, wrong provider
        assertThat(p.shouldPromote("github", "1", "jdoe")).isFalse(); // username listed only for gitlab-lrz
    }

    @Test
    void emptyOrNullAllowlistNeverPromotes_andNotConfigured() {
        assertThat(policy().isConfigured()).isFalse();
        assertThat(policy().shouldPromote("github", "1", "x")).isFalse();
        assertThat(policy((String[]) null).shouldPromote("github", "1", "x")).isFalse();
    }

    @Test
    void nullRegistrationIsFalse() {
        AdminBootstrapPolicy p = policy("github:@octocat");
        assertThat(p.shouldPromote(null, "1", "octocat")).isFalse();
        assertThat(p.isConfigured()).isTrue();
    }

    @Test
    void trimsWhitespaceAndIgnoresMalformedEntries() {
        AdminBootstrapPolicy p = policy("  github:@octocat  ", "", "noColon", "github:", ":42", "gitlab-lrz:@");
        assertThat(p.shouldPromote("github", "1", "octocat")).isTrue(); // trimmed entry still matches
        assertThat(p.shouldPromote("github", "", null)).isFalse(); // "github:" dropped
        assertThat(p.shouldPromote("", "42", null)).isFalse(); // ":42" dropped
        assertThat(p.shouldPromote("gitlab-lrz", "9", "")).isFalse(); // "gitlab-lrz:@" (blank handle) dropped
    }

    @Test
    void subjectWithColonsKeepsEverythingAfterFirstColon() {
        AdminBootstrapPolicy p = policy("custom:urn:weird:99");
        assertThat(p.shouldPromote("custom", "urn:weird:99", null)).isTrue();
    }
}
