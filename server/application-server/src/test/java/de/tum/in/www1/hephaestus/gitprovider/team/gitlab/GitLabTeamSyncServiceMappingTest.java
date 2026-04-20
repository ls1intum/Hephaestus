package de.tum.in.www1.hephaestus.gitprovider.team.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("GitLabTeamSyncService.mapAccessLevel")
class GitLabTeamSyncServiceMappingTest {

    @ParameterizedTest
    @ValueSource(strings = { "GUEST", "PLANNER", "REPORTER", "DEVELOPER", "guest", "Developer" })
    @DisplayName("member-level access maps to MEMBER")
    void memberLevels_mapToMember(String level) {
        assertThat(GitLabTeamSyncService.mapAccessLevel(level)).isEqualTo(TeamMembership.Role.MEMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "MAINTAINER", "OWNER", "ADMIN", "maintainer", "owner" })
    @DisplayName("maintainer-level access maps to MAINTAINER")
    void maintainerLevels_mapToMaintainer(String level) {
        assertThat(GitLabTeamSyncService.mapAccessLevel(level)).isEqualTo(TeamMembership.Role.MAINTAINER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "NO_ACCESS", "MINIMAL_ACCESS" })
    @DisplayName("below-guest access levels are skipped")
    void subGuestLevels_areSkipped(String level) {
        assertThat(GitLabTeamSyncService.mapAccessLevel(level)).isNull();
    }
}
