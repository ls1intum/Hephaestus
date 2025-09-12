package de.tum.in.www1.hephaestus.gitprovider.teamV2.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.teamV2.TeamV2Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("GitHub Membership MessageHandler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubMembershipMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubMembershipMessageHandler membershipHandler;

    @Autowired
    private TeamV2Repository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setup() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("membership added -> user linked to team")
    void membershipAdded_linksUserToTeam(@GitHubPayload("membership.added") GHEventPayload.Membership payload) {
        // Arrange
        long teamId = payload.getTeam().getId();
        long userId = payload.getMember().getId();

        // Act
        membershipHandler.handleEvent(payload);

        // Assert user exists and is linked to team
        var team = teamRepository.findById(teamId).orElseThrow();
        var user = userRepository.findById(userId).orElseThrow();
        assertThat(team.getMemberships()).anySatisfy(m -> {
            assertThat(m.getUser().getId()).isEqualTo(user.getId());
        });
    }

    @Test
    @DisplayName("membership removed -> user unlinked from team")
    void membershipRemoved_unlinksUserFromTeam(
        @GitHubPayload("membership.added") GHEventPayload.Membership added,
        @GitHubPayload("membership.removed") GHEventPayload.Membership removed
    ) {
        // Arrange: ensure membership exists
        membershipHandler.handleEvent(added);
        long teamId = removed.getTeam().getId();
        long userId = removed.getMember().getId();

        // Act
        membershipHandler.handleEvent(removed);

        // Assert
        var team = teamRepository.findById(teamId).orElseThrow();
        assertThat(team.getMemberships()).noneSatisfy(m -> assertThat(m.getUser().getId()).isEqualTo(userId));
    }
}
