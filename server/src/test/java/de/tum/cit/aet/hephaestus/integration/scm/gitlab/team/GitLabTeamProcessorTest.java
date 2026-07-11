package de.tum.cit.aet.hephaestus.integration.scm.gitlab.team;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class GitLabTeamProcessorTest {

    @Test
    void rootSlug_usesLastSegment() {
        assertThat(GitLabTeamProcessor.rootSlug("ase/course-org/IOS26/Introcourse")).isEqualTo("Introcourse");
        assertThat(GitLabTeamProcessor.rootSlug("introcourse")).isEqualTo("introcourse");
    }

    @Test
    @DisplayName("rootSlug tolerates null and blank input")
    void rootSlug_handlesEdgeCases() {
        assertThat(GitLabTeamProcessor.rootSlug(null)).isEmpty();
        assertThat(GitLabTeamProcessor.rootSlug("")).isEmpty();
    }

    @Test
    void computeRelativePath_stripsRootPrefix() {
        assertThat(GitLabTeamProcessor.computeRelativePath("ase/introcourse/alpha", "ase/introcourse")).isEqualTo(
            "alpha"
        );
        assertThat(
            GitLabTeamProcessor.computeRelativePath("ase/introcourse/group1/alpha", "ase/introcourse")
        ).isEqualTo("group1/alpha");
    }
}
