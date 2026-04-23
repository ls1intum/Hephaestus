package de.tum.in.www1.hephaestus.gitprovider.team.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("GitLabTeamProcessor")
class GitLabTeamProcessorTest {

    @Test
    @DisplayName("rootSlug uses the last path segment so it never collides with descendant slugs")
    void rootSlug_usesLastSegment() {
        assertThat(GitLabTeamProcessor.rootSlug("ase/ipraktikum/IOS26/Introcourse")).isEqualTo("Introcourse");
        assertThat(GitLabTeamProcessor.rootSlug("introcourse")).isEqualTo("introcourse");
    }

    @Test
    @DisplayName("rootSlug tolerates null and blank input")
    void rootSlug_handlesEdgeCases() {
        assertThat(GitLabTeamProcessor.rootSlug(null)).isEmpty();
        assertThat(GitLabTeamProcessor.rootSlug("")).isEmpty();
    }

    @Test
    @DisplayName("computeRelativePath strips the root prefix for descendants")
    void computeRelativePath_stripsRootPrefix() {
        assertThat(GitLabTeamProcessor.computeRelativePath("ase/introcourse/alpha", "ase/introcourse")).isEqualTo(
            "alpha"
        );
        assertThat(
            GitLabTeamProcessor.computeRelativePath("ase/introcourse/group1/alpha", "ase/introcourse")
        ).isEqualTo("group1/alpha");
    }
}
