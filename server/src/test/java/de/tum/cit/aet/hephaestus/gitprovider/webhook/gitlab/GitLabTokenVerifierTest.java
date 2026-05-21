package de.tum.cit.aet.hephaestus.gitprovider.webhook.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class GitLabTokenVerifierTest extends BaseUnitTest {

    private static final String SECRET = "gitlab-secret";

    @Test
    void verifiesMatchingToken() {
        assertThat(GitLabTokenVerifier.verify(SECRET, SECRET)).isTrue();
    }

    @Test
    void rejectsNonMatchingTokenOfSameLength() {
        assertThat(GitLabTokenVerifier.verify("gitlab-WRONG!", SECRET)).isFalse();
    }

    @Test
    void rejectsDifferentLengthToken() {
        assertThat(GitLabTokenVerifier.verify("short", "much-longer-secret")).isFalse();
    }

    @Test
    void rejectsEmptyOrNullToken() {
        assertThat(GitLabTokenVerifier.verify(null, SECRET)).isFalse();
        assertThat(GitLabTokenVerifier.verify("", SECRET)).isFalse();
    }

    @Test
    void rejectsWhitespacePaddedToken() {
        assertThat(GitLabTokenVerifier.verify(" " + SECRET, SECRET)).isFalse();
        assertThat(GitLabTokenVerifier.verify(SECRET + " ", SECRET)).isFalse();
    }
}
