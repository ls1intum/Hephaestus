package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the GitHub email-verification policy: only the {@code primary && verified} address is trusted.
 */
class GitHubEmailOAuth2UserServiceTest extends BaseUnitTest {

    @Test
    void primaryVerifiedEmail_isSelected() {
        List<Map<String, Object>> emails = List.of(
            Map.of("email", "secondary@x.de", "primary", false, "verified", true),
            Map.of("email", "p@x.de", "primary", true, "verified", true)
        );

        assertThat(GitHubEmailOAuth2UserService.selectPrimaryVerifiedEmail(emails)).contains("p@x.de");
    }

    @Test
    void primaryButUnverified_isNotSelected() {
        List<Map<String, Object>> emails = List.of(Map.of("email", "p@x.de", "primary", true, "verified", false));

        assertThat(GitHubEmailOAuth2UserService.selectPrimaryVerifiedEmail(emails)).isEmpty();
    }

    @Test
    void verifiedButNotPrimary_isNotSelected() {
        List<Map<String, Object>> emails = List.of(Map.of("email", "v@x.de", "primary", false, "verified", true));

        assertThat(GitHubEmailOAuth2UserService.selectPrimaryVerifiedEmail(emails)).isEmpty();
    }

    @Test
    void nullOrEmpty_isEmpty() {
        assertThat(GitHubEmailOAuth2UserService.selectPrimaryVerifiedEmail(null)).isEmpty();
        assertThat(GitHubEmailOAuth2UserService.selectPrimaryVerifiedEmail(List.of())).isEmpty();
    }
}
