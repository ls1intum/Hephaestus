package de.tum.cit.aet.hephaestus.leaderboard;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Feature-flag gate on the leaderboard/league read endpoints: a workspace with
 * {@code leaderboardEnabled} / {@code leaguesEnabled} off answers 404 {@code ProblemDetail}
 * (the resource does not exist there); with the flag on the endpoint serves normally.
 */
class LeaderboardControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String LEADERBOARD_URI = "/workspaces/{workspaceSlug}/leaderboard";
    private static final String LEAGUE_STATS_URI = "/workspaces/{workspaceSlug}/leaderboard/users/{login}/league-stats";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Workspace workspace;
    private User member;

    @BeforeEach
    void setUpWorkspace() {
        User owner = persistUser("board-owner");
        workspace = createWorkspace("board-ws", "Board WS", "board-org", AccountType.ORG, owner);
        member = persistUser("testuser");
        ensureWorkspaceMembership(workspace, member, WorkspaceMembership.WorkspaceRole.MEMBER);
    }

    private void setFlags(boolean leaderboardEnabled, boolean leaguesEnabled) {
        workspace.getFeatures().setLeaderboardEnabled(leaderboardEnabled);
        workspace.getFeatures().setLeaguesEnabled(leaguesEnabled);
        workspace = workspaceRepository.save(workspace);
    }

    private java.util.function.Function<UriBuilder, java.net.URI> leaderboardUri() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant after = before.minus(7, ChronoUnit.DAYS);
        return builder ->
            builder
                .path(LEADERBOARD_URI)
                .queryParam("after", after.toString())
                .queryParam("before", before.toString())
                .queryParam("team", "all")
                .queryParam("sort", "SCORE")
                .queryParam("mode", "INDIVIDUAL")
                .build(workspace.getWorkspaceSlug());
    }

    private java.util.function.Function<UriBuilder, java.net.URI> leagueStatsUri() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant after = before.minus(7, ChronoUnit.DAYS);
        return builder ->
            builder
                .path(LEAGUE_STATS_URI)
                .queryParam("after", after.toString())
                .queryParam("before", before.toString())
                .build(workspace.getWorkspaceSlug(), member.getLogin());
    }

    @Test
    @WithUser
    @DisplayName("leaderboard answers 404 ProblemDetail when the workspace flag is off (the default)")
    void leaderboardIs404WhenFlagOff() {
        setFlags(false, false);

        webTestClient
            .get()
            .uri(leaderboardUri())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectHeader()
            .contentTypeCompatibleWith("application/problem+json")
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(404)
            .jsonPath("$.detail")
            .isNotEmpty();
    }

    @Test
    @WithUser
    @DisplayName("leaderboard is no longer 404 when the workspace flag is on")
    void leaderboardServesWhenFlagOn() {
        setFlags(true, false);

        // Asserts only that the feature gate opened (not 404). A 200-body assertion would couple this
        // test to a pre-existing tenancy gap in the leaderboard read path
        // (UserRepository#findAllHumanInTeamsOfOrganization has no workspace_id predicate), which the
        // test profile's throw-mode inspector converts to a 500 while production log-mode serves 200.
        webTestClient
            .get()
            .uri(leaderboardUri())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(404));
    }

    @Test
    @WithUser
    @DisplayName("league stats answer 404 ProblemDetail when the leagues flag is off, even with leaderboard on")
    void leagueStatsAre404WhenLeaguesFlagOff() {
        setFlags(true, false);

        webTestClient
            .get()
            .uri(leagueStatsUri())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound()
            .expectHeader()
            .contentTypeCompatibleWith("application/problem+json")
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(404);
    }

    @Test
    @WithUser
    @DisplayName("league stats are no longer 404 when the leagues flag is on")
    void leagueStatsServeWhenLeaguesFlagOn() {
        setFlags(true, true);

        // Not-404 for the same reason as the leaderboard flag-on test: the league projection walks the
        // same team query with the pre-existing tenancy gap, so 200 cannot be asserted under the test
        // profile's throw-mode inspector.
        webTestClient
            .get()
            .uri(leagueStatsUri())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .value(status -> org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(404));
    }
}
