package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.activity.ActivityEventRepository;
import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.TeamRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.membership.TeamMembership;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.membership.TeamMembershipRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.StatusAssertions;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Systematic cross-tenant isolation suite. Provisions two workspaces (A and B) that deliberately
 * share the same {@code account_login} ({@code shared-org}) with an <b>overlapping</b> member
 * ({@code mentor}, matching {@link WithMentorUser}), then asserts that reading through A's slug never
 * exposes B's rows across every workspace-scoped read path.
 *
 * <p>Two design choices make this a sharp probe rather than a happy-path smoke test:
 * <ul>
 *   <li><b>Shared {@code account_login}</b> — the org-login string is not a tenant boundary, so any
 *       path that scopes by it instead of {@code workspace_id} leaks here (and only here).</li>
 *   <li><b>Overlapping member</b> — authentication succeeds in both tenants, so a leaked row is a
 *       pure data-scoping bug, not an access-control artifact.</li>
 * </ul>
 *
 * <p>Each detail read carries a positive control (own row through own slug returns {@code 200}) so a
 * {@code 404} assertion cannot pass against a globally broken endpoint. New workspace-scoped read
 * paths add a case here — see {@code docs/contributor/testing.mdx} ("Cross-tenant isolation").
 *
 * <p><b>Known gaps this suite does NOT cover</b> (each needs work beyond a test):
 * <ul>
 *   <li>{@code RepositoryToMonitor.nameWithOwner} is joined by bare string to {@code repository}, which
 *       is keyed {@code (provider_id, name_with_owner)} — a cross-provider name collision leaks PR bodies
 *       through the leaderboard, profile, and mentor-context read paths. Needs a schema migration.</li>
 *   <li>Achievements aggregate {@code activity_event} across all workspaces by design
 *       ({@code user_achievement} is global), which contradicts {@code docs/user/achievements.mdx}. Needs a
 *       product decision, not an assertion.</li>
 *   <li>{@code UserProfileService} resolves the login globally, so a non-member returns {@code 200} with
 *       empty data rather than {@code 404} — an existence oracle.</li>
 * </ul>
 */
class CrossTenantIsolationIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SHARED_LOGIN = "shared-org";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

    @Autowired
    private ActivityEventRepository activityEventRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMembershipRepository teamMembershipRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private User overlapUser;
    private User bobOnlyB; // read by the leaderboard roster test; alice stays a local

    private Workspace workspaceA;
    private Workspace workspaceB;
    private Workspace outsiderWorkspace; // overlapUser is NOT a member here

    private TenantData tenantA;
    private TenantData tenantB;

    /** IDs of the data seeded into one workspace, used to build cross-tenant probe URLs. */
    private record TenantData(String practiceSlug, UUID observationId, UUID feedbackId, UUID threadId) {}

    @BeforeEach
    void provisionTwoTenants() {
        overlapUser = persistUser("mentor");

        User ownerA = persistUser("owner-a");
        workspaceA = createWorkspace("tenant-a", "Tenant A", SHARED_LOGIN, AccountType.ORG, ownerA);
        ensureWorkspaceMembership(workspaceA, overlapUser, WorkspaceRole.MEMBER);
        User aliceOnlyA = persistUser("alice-only-a");
        ensureWorkspaceMembership(workspaceA, aliceOnlyA, WorkspaceRole.MEMBER);

        User ownerB = persistUser("owner-b");
        workspaceB = createWorkspace("tenant-b", "Tenant B", SHARED_LOGIN, AccountType.ORG, ownerB);
        ensureWorkspaceMembership(workspaceB, overlapUser, WorkspaceRole.MEMBER);
        bobOnlyB = persistUser("bob-only-b");
        ensureWorkspaceMembership(workspaceB, bobOnlyB, WorkspaceRole.MEMBER);

        User ownerC = persistUser("owner-c");
        outsiderWorkspace = createWorkspace("tenant-c", "Tenant C", "org-c", AccountType.ORG, ownerC);

        tenantA = seed(workspaceA);
        tenantB = seed(workspaceB);
    }

    /** Seed one practice + observation + feedback + mentor thread, all about/owned by {@link #overlapUser}. */
    private TenantData seed(Workspace ws) {
        String practiceSlug = "practice-" + ws.getWorkspaceSlug();
        Practice practice = new Practice();
        practice.setWorkspace(ws);
        practice.setSlug(practiceSlug);
        practice.setName("Practice of " + ws.getWorkspaceSlug());
        practice.setCriteria("Criteria for " + ws.getWorkspaceSlug());
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice.setActive(true);
        practice = practiceRepository.save(practice);

        AgentJob job = new AgentJob();
        job.setWorkspace(ws);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        job = agentJobRepository.save(job);

        UUID observationId = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            observationId,
            "occ-" + observationId,
            job.getId(),
            practice.getId(),
            null,
            "PULL_REQUEST",
            1L,
            overlapUser.getId(),
            "Finding in " + ws.getWorkspaceSlug(),
            "PRESENT",
            "GOOD",
            "INFO",
            0.9f,
            null,
            "reasoning",
            null,
            Instant.now()
        );

        Feedback feedback = feedbackRepository.save(
            Feedback.builder()
                .agentJobId(job.getId())
                .workspaceId(ws.getId())
                .artifactType(WorkArtifact.PULL_REQUEST)
                .artifactId(42L)
                .recipientUserId(overlapUser.getId())
                .aboutUserId(overlapUser.getId())
                .channel(FeedbackChannel.IN_CONTEXT)
                .position(0)
                .deliveryState(FeedbackDeliveryState.DELIVERED)
                .body("Advice in " + ws.getWorkspaceSlug())
                .source(FeedbackSource.AGENT)
                .createdAt(Instant.now())
                .build()
        );

        ChatThread thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setTitle("Thread in " + ws.getWorkspaceSlug());
        thread.setWorkspace(ws);
        thread.setUser(overlapUser);
        chatThreadRepository.save(thread);

        return new TenantData(practiceSlug, observationId, feedback.getId(), thread.getId());
    }

    /**
     * Links a workspace to a synced {@link Organization} so its provider resolves — this is what real
     * org sync does, and it is what {@code resolveTeamProviderId} reads to scope team queries.
     */
    private void linkSyncedOrg(Workspace ws, IdentityProvider provider) {
        Organization org = new Organization();
        org.setNativeId(800_000L + ws.getId());
        org.setLogin(ws.getAccountLogin());
        org.setHtmlUrl("https://example.com/" + ws.getAccountLogin());
        org.setProvider(provider);
        org = organizationRepository.save(org);
        ws.setOrganization(org);
        workspaceRepository.save(ws);
    }

    private Team seedTeam(String name, long nativeId, String organization, IdentityProvider provider) {
        Team team = new Team();
        team.setNativeId(nativeId);
        team.setName(name);
        team.setSlug(name);
        team.setOrganization(organization);
        team.setHtmlUrl("https://example.com/teams/" + name);
        team.setPrivacy(Team.Privacy.VISIBLE);
        team.setProvider(provider);
        return teamRepository.save(team);
    }

    @Nested
    @DisplayName("Practices catalog")
    class Practices {

        @Test
        @WithMentorUser
        void listReturnsOnlyOwnWorkspacePractices() {
            webTestClient
                .get()
                .uri("/workspaces/{slug}/practices", workspaceA.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].slug")
                .isEqualTo(tenantA.practiceSlug());
        }

        @Test
        @WithMentorUser
        void detailIsScopedToWorkspace() {
            expectDetailStatus("/practices/{key}", tenantA.practiceSlug()).isOk();
            expectDetailStatus("/practices/{key}", tenantB.practiceSlug()).isNotFound();
        }
    }

    @Nested
    @DisplayName("Observations (practice findings)")
    class Observations {

        @Test
        @WithMentorUser
        void listReturnsOnlyOwnWorkspaceFindings() {
            webTestClient
                .get()
                .uri("/workspaces/{slug}/practices/observations", workspaceA.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].title")
                .isEqualTo("Finding in tenant-a");
        }

        @Test
        @WithMentorUser
        void detailIsScopedToWorkspace() {
            expectDetailStatus("/practices/observations/{key}", tenantA.observationId()).isOk();
            expectDetailStatus("/practices/observations/{key}", tenantB.observationId()).isNotFound();
        }
    }

    @Nested
    @DisplayName("Feedback reactions")
    class FeedbackReactions {

        @Test
        @WithMentorUser
        void reactionsAreScopedToWorkspace() {
            // 204 = own feedback found, no reaction yet; 404 = foreign feedback not in this workspace.
            expectDetailStatus("/practices/feedback/{key}/reactions", tenantA.feedbackId()).isNoContent();
            expectDetailStatus("/practices/feedback/{key}/reactions", tenantB.feedbackId()).isNotFound();
        }
    }

    @Nested
    @DisplayName("Mentor threads")
    class Mentor {

        @Test
        @WithMentorUser
        void listReturnsOnlyOwnWorkspaceThreads() {
            webTestClient
                .get()
                .uri("/workspaces/{slug}/mentor/threads", workspaceA.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].id")
                .isEqualTo(tenantA.threadId().toString());
        }

        @Test
        @WithMentorUser
        void detailIsScopedToWorkspace() {
            expectDetailStatus("/mentor/threads/{key}", tenantA.threadId()).isOk();
            expectDetailStatus("/mentor/threads/{key}", tenantB.threadId()).isNotFound();
        }
    }

    @Nested
    @DisplayName("Leaderboard member roster")
    class Leaderboard {

        /**
         * The zero-activity roster is enumerated by workspace membership, not by the shared org-login
         * string. Old code padded via {@code findAllHumanInTeamsOfOrganization("shared-org")}, which
         * returned {@code bob-only-b} (a member of B, reachable through a {@code shared-org} team) into
         * A's leaderboard — the leak this guards. It also dropped {@code alice-only-a} (an A member with
         * no team), so both assertions fail against the old query.
         */
        @Test
        @WithMentorUser
        void rosterIsScopedToWorkspaceMembership() {
            Team sharedTeam = seedTeam("shared-team", 900_100L, SHARED_LOGIN, ensureGitHubProvider());
            teamMembershipRepository.save(new TeamMembership(sharedTeam, bobOnlyB, TeamMembership.Role.MEMBER));

            List<String> logins = leaderboardLogins(workspaceA);

            assertThat(logins).contains("alice-only-a").doesNotContain("bob-only-b");
        }

        /**
         * XP comes from the {@code activity_event} ledger, which is {@code workspace_id}-scoped. The
         * overlapping member has activity in both tenants, so A's score must count only A's events.
         */
        @Test
        @WithMentorUser
        void xpCountsOnlyOwnWorkspaceActivity() {
            seedActivity(workspaceA, overlapUser, 10);
            seedActivity(workspaceB, overlapUser, 99);

            Integer score = leaderboardEntries(workspaceA)
                .stream()
                .filter(e -> e.user().login().equals("mentor"))
                .findFirst()
                .orElseThrow()
                .score();

            assertThat(score).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Teams")
    class Teams {

        /**
         * Team reads are scoped by {@code (organization, provider_id)}. A same-named team on a different
         * provider (whose {@code organization} string collides with the workspace's {@code account_login})
         * must not appear; the workspace's own team must.
         */
        @Test
        @WithMentorUser
        void listIsScopedToWorkspaceProvider() {
            linkSyncedOrg(workspaceA, ensureGitHubProvider());
            seedTeam("own-team", 900_200L, SHARED_LOGIN, ensureGitHubProvider());
            seedTeam("foreign-team", 900_201L, SHARED_LOGIN, ensureGitLabProvider());

            webTestClient
                .get()
                .uri("/workspaces/{slug}/team", workspaceA.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[?(@.name=='own-team')]")
                .exists()
                .jsonPath("$[?(@.name=='foreign-team')]")
                .doesNotExist();
        }

        /**
         * A {@link User} is global, so the workspace roster hydrates the member's team memberships from
         * EVERY tenant they belong to. {@code GET /users} must project only this tenant's teams — the
         * per-workspace hidden-team setting is a display filter, not a tenancy boundary. Needs no provider
         * collision: the overlapping member is simply in a team under each workspace.
         */
        @Test
        @WithMentorUser
        void memberRosterExposesOnlyOwnWorkspaceTeams() {
            linkSyncedOrg(workspaceA, ensureGitHubProvider());
            Team ownTeam = seedTeam("own-team", 900_400L, SHARED_LOGIN, ensureGitHubProvider());
            Team foreignTeam = seedTeam("foreign-team", 900_401L, SHARED_LOGIN, ensureGitLabProvider());
            teamMembershipRepository.save(new TeamMembership(ownTeam, overlapUser, TeamMembership.Role.MEMBER));
            teamMembershipRepository.save(new TeamMembership(foreignTeam, overlapUser, TeamMembership.Role.MEMBER));

            webTestClient
                .get()
                .uri("/workspaces/{slug}/users", workspaceA.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[?(@.login=='mentor')].teams[?(@.name=='own-team')]")
                .exists()
                .jsonPath("$..teams[?(@.name=='foreign-team')]")
                .doesNotExist();
        }

        /**
         * The write path authorizes the same way as the read: a workspace admin cannot mutate a
         * same-named team on a different provider (that would silently write settings for another tenant's
         * team). Own team returns 200; foreign team returns 404.
         */
        @Test
        @WithAdminUser
        void visibilityWriteIsScopedToWorkspaceProvider() {
            ensureAdminMembership(workspaceA);
            linkSyncedOrg(workspaceA, ensureGitHubProvider());
            Team ownTeam = seedTeam("own-team", 900_300L, SHARED_LOGIN, ensureGitHubProvider());
            Team foreignTeam = seedTeam("foreign-team", 900_301L, SHARED_LOGIN, ensureGitLabProvider());

            expectVisibilityWriteStatus(ownTeam.getId()).isOk();
            expectVisibilityWriteStatus(foreignTeam.getId()).isNotFound();
        }
    }

    @Nested
    @DisplayName("Access-control boundaries")
    class AccessControl {

        @Test
        @WithMentorUser
        void nonMemberIsRefusedFromScopedEndpoint() {
            webTestClient
                .get()
                .uri("/workspaces/{slug}/practices/observations", outsiderWorkspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @WithMentorUser
        void workspaceMembershipDoesNotGrantInstanceAdminListing() {
            webTestClient
                .get()
                .uri("/admin/workspaces")
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }
    }

    private StatusAssertions expectDetailStatus(String suffix, Object key) {
        return webTestClient
            .get()
            .uri("/workspaces/{slug}" + suffix, workspaceA.getWorkspaceSlug(), key)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus();
    }

    private StatusAssertions expectVisibilityWriteStatus(Long teamId) {
        return webTestClient
            .post()
            .uri("/workspaces/{slug}/team/{id}/visibility", workspaceA.getWorkspaceSlug(), teamId)
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(true)
            .exchange()
            .expectStatus();
    }

    private void seedActivity(Workspace ws, User actor, double xp) {
        UUID id = UUID.randomUUID();
        activityEventRepository.insertIfAbsent(
            id,
            "evt-" + id,
            ActivityEventType.REVIEW_COMMENTED.name(),
            Instant.now(),
            actor.getId(),
            ws.getId(),
            null,
            "pull_request",
            1L,
            xp
        );
    }

    private List<String> leaderboardLogins(Workspace workspace) {
        return leaderboardEntries(workspace)
            .stream()
            .map(e -> e.user().login())
            .toList();
    }

    private List<LeaderboardEntryDTO> leaderboardEntries(Workspace workspace) {
        List<LeaderboardEntryDTO> entries = webTestClient
            .get()
            .uri(uri ->
                uri
                    .path("/workspaces/{slug}/leaderboard")
                    .queryParam("after", "2020-01-01T00:00:00Z")
                    .queryParam("before", "2100-01-01T00:00:00Z")
                    .queryParam("team", "all")
                    .queryParam("sort", "SCORE")
                    .queryParam("mode", "INDIVIDUAL")
                    .build(workspace.getWorkspaceSlug())
            )
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(LeaderboardEntryDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(entries).isNotNull();
        return entries;
    }
}
