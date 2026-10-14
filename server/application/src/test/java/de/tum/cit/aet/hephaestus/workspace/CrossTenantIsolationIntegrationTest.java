package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.activity.ActivityEventRepository;
import de.tum.cit.aet.hephaestus.activity.ActivityEventType;
import de.tum.cit.aet.hephaestus.activity.overview.dto.ActivityPeopleDTO;
import de.tum.cit.aet.hephaestus.activity.overview.dto.ActivityPersonDTO;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.Organization;
import de.tum.cit.aet.hephaestus.integration.scm.domain.organization.OrganizationRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.TeamRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.membership.TeamMembership;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.membership.TeamMembershipRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PracticePageFeedbackBody;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.Membership.WorkspaceRole;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.StatusAssertions;
import org.springframework.test.web.reactive.server.WebTestClient;

// Overlapping members and identical account logins distinguish tenancy from authentication and organization filters.
class CrossTenantIsolationIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    private PracticeModelTestFixtures modelFixtures;

    private static final String SHARED_LOGIN = "shared-org";

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ResultRepository practiceResultRepository;

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

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository monitorRepository;

    private static final AtomicLong repositoryIds = new AtomicLong(9700000);

    private User overlapUser;
    private User bobOnlyB;

    private Workspace workspaceA;
    private Workspace workspaceB;
    private Workspace outsiderWorkspace;

    private TenantData tenantA;
    private TenantData tenantB;

    private record TenantData(String practiceSlug, UUID practiceResultId, UUID feedbackId, UUID threadId) {}

    @BeforeEach
    void provisionTwoTenants() {
        overlapUser = persistUser("mentor");

        User ownerA = persistUser("owner-a");
        workspaceA = createWorkspace("tenant-a", "Tenant A", SHARED_LOGIN, AccountType.ORG, ownerA);
        ensureMembership(workspaceA, overlapUser, WorkspaceRole.ADMIN);
        User aliceOnlyA = persistUser("alice-only-a");
        ensureMembership(workspaceA, aliceOnlyA, WorkspaceRole.MEMBER);

        User ownerB = persistUser("owner-b");
        workspaceB = createWorkspace("tenant-b", "Tenant B", SHARED_LOGIN, AccountType.ORG, ownerB);
        ensureMembership(workspaceB, overlapUser, WorkspaceRole.ADMIN);
        bobOnlyB = persistUser("bob-only-b");
        ensureMembership(workspaceB, bobOnlyB, WorkspaceRole.MEMBER);

        User ownerC = persistUser("owner-c");
        outsiderWorkspace = createWorkspace("tenant-c", "Tenant C", "org-c", AccountType.ORG, ownerC);

        tenantA = seed(workspaceA);
        tenantB = seed(workspaceB);
    }

    private TenantData seed(Workspace ws) {
        String slug = "practice-" + ws.getWorkspaceSlug();
        var work = modelFixtures.work(ws, slug, "scm.pull_request", 1L);
        var result = modelFixtures.result(work, accountId(overlapUser), "Result in " + ws.getWorkspaceSlug());
        var feedback = modelFixtures.feedback(
                result, FeedbackDestination.ON_WORK, FeedbackState.DELIVERED, "Advice in " + ws.getWorkspaceSlug());
        var thread = new ChatThread();
        thread.setId(UUID.randomUUID());
        thread.setTitle("Thread in " + ws.getWorkspaceSlug());
        thread.setWorkspace(ws);
        thread.setAccountId(accountId(overlapUser));
        chatThreadRepository.save(thread);
        return new TenantData(slug, result.getId(), feedback.getId(), thread.getId());
    }

    private void linkSyncedOrg(Workspace ws, IdentityProvider provider) {
        Organization org = new Organization();
        org.setNativeId(800_000L + ws.getId());
        org.setLogin(ws.getAccountLogin());
        org.setHtmlUrl("https://example.com/" + ws.getAccountLogin());
        org.setProvider(provider);
        org = organizationRepository.save(org);
        ws.setOrganizationId(org.getId());
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
            expectDetailStatus("/practices/{key}", tenantA.practiceSlug())
                    .isOk()
                    .expectBody(Void.class);
            expectDetailStatus("/practices/{key}", tenantB.practiceSlug())
                    .isNotFound()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Problems")
    class Results {

        @Test
        @WithMentorUser
        void listReturnsOnlyOwnWorkspaceResults() {
            webTestClient
                    .get()
                    .uri("/workspaces/{slug}/practices/results", workspaceA.getWorkspaceSlug())
                    .headers(TestAuthUtils.withCurrentUser())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.content.length()")
                    .isEqualTo(1)
                    .jsonPath("$.content[0].summary")
                    .isEqualTo("Result in tenant-a");
        }

        @Test
        @WithMentorUser
        void detailIsScopedToWorkspace() {
            expectDetailStatus("/practices/results/{key}", tenantA.practiceResultId())
                    .isOk()
                    .expectBody(Void.class);
            expectDetailStatus("/practices/results/{key}", tenantB.practiceResultId())
                    .isNotFound()
                    .expectBody(Void.class);
        }

        @Test
        @WithMentorUser
        void adviceOnTheDetailReadComesOnlyFromThisWorkspace() {
            UUID localFeedback = bindFeedback(
                    tenantA.practiceResultId(), "Feedback in tenant A", Instant.parse("2026-01-01T00:00:00Z"));
            UUID foreignFeedback = bindFeedback(
                    tenantB.practiceResultId(), "Feedback in tenant B", Instant.parse("2026-02-01T00:00:00Z"));

            webTestClient
                    .get()
                    .uri(
                            "/workspaces/{slug}/practices/results/{key}",
                            workspaceA.getWorkspaceSlug(),
                            tenantA.practiceResultId())
                    .headers(TestAuthUtils.withCurrentUser())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.feedback[*].id")
                    .value(ids -> assertThat(ids)
                            .asInstanceOf(InstanceOfAssertFactories.LIST)
                            .contains(localFeedback.toString())
                            .doesNotContain(foreignFeedback.toString()));
        }
    }

    private UUID bindFeedback(UUID resultId, String body, Instant createdAt) {
        var result = practiceResultRepository.findById(resultId).orElseThrow();
        return modelFixtures
                .feedback(result, FeedbackDestination.ON_WORK, FeedbackState.DELIVERED, body, createdAt)
                .getId();
    }

    @Nested
    @DisplayName("Feedback reactions")
    class FeedbackFeedbackResponses {

        @Test
        @WithMentorUser
        void reactionsAreScopedToWorkspace() {
            expectDetailStatus("/practices/feedback/{key}/response", tenantA.feedbackId())
                    .isNoContent();
            expectDetailStatus("/practices/feedback/{key}/response", tenantB.feedbackId())
                    .isNotFound()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("In-app feedback (the developer's own practice pages)")
    class PracticePageFeedback {

        @Test
        @WithMentorUser
        void readsOnlyTheMessagesPreparedInThisWorkspace() {
            seedPracticePageFeedback(workspaceA, "A way of working measured in tenant A");
            seedPracticePageFeedback(workspaceB, "A way of working measured in tenant B");

            webTestClient
                    .get()
                    .uri("/workspaces/{slug}/practices/feedback/practice-page", workspaceA.getWorkspaceSlug())
                    .headers(TestAuthUtils.withCurrentUser())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.length()")
                    .isEqualTo(1)
                    .jsonPath("$[0].headline")
                    .isEqualTo("A way of working measured in tenant A");
        }

        @Test
        @WithMentorUser
        void neverRetiresACardQueuedInAnotherTenant() {
            Feedback inA = seedPracticePageFeedback(workspaceA, "A way of working measured in tenant A");
            Feedback inB = seedPracticePageFeedback(workspaceB, "A way of working measured in tenant B");
            assertThat(feedbackRepository.findByIdAndWorkspaceId(inA.getId(), workspaceA.getId()))
                    .isPresent();
            assertThat(feedbackRepository.findByIdAndWorkspaceId(inB.getId(), workspaceA.getId()))
                    .isEmpty();
            assertThat(feedbackRepository.findByIdAndWorkspaceId(inB.getId(), workspaceB.getId()))
                    .isPresent();
        }

        @Test
        @WithMentorUser
        void namesOnlyThisTenantsPracticeForAQueuedCard() {
            Feedback inA = seedPracticePageFeedback(workspaceA, "A way of working measured in tenant A");
            Feedback inB = seedPracticePageFeedback(workspaceB, "A way of working measured in tenant B");

            assertThat(feedbackRepository.findByWorkspaceIdAndReviewIdOrderByCreatedAtAscIdAsc(
                            workspaceA.getId(), inA.getReviewId()))
                    .extracting(Feedback::getId)
                    .containsExactly(inA.getId())
                    .doesNotContain(inB.getId());
        }
    }

    private Feedback seedPracticePageFeedback(Workspace ws, String headline) {
        var work = modelFixtures.work(
                ws,
                "practice-page-"
                        + ws.getWorkspaceSlug()
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8),
                "scm.pull_request",
                2L);
        var result = modelFixtures.result(work, accountId(overlapUser), "Evidence in " + ws.getWorkspaceSlug());
        return modelFixtures.feedback(
                result,
                FeedbackDestination.PRACTICE_PAGE,
                FeedbackState.READY,
                PracticePageFeedbackBody.render(headline, "The feedback.", "The way of working to try."));
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
            expectDetailStatus("/mentor/threads/{key}", tenantA.threadId())
                    .isOk()
                    .expectBody(Void.class);
            expectDetailStatus("/mentor/threads/{key}", tenantB.threadId())
                    .isNotFound()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Workspace activity roster")
    class WorkspaceActivity {

        @BeforeEach
        void connectActivitySources() {
            connectActivitySource(workspaceA, ensureGitHubProvider());
            connectActivitySource(workspaceB, ensureGitHubProvider());
        }

        @Test
        @WithMentorUser
        void shouldListOnlyWorkspaceContributorsWhenOrganizationsOverlap() {
            Team sharedTeam = seedTeam("shared-team", 900_100L, SHARED_LOGIN, ensureGitHubProvider());
            teamMembershipRepository.save(new TeamMembership(sharedTeam, bobOnlyB, TeamMembership.Role.MEMBER));

            seedActivity(
                    workspaceA,
                    userRepository
                            .findByLoginAndProviderId(
                                    "alice-only-a",
                                    Objects.requireNonNull(
                                            ensureGitHubProvider().getId()))
                            .orElseThrow(),
                    1);
            seedActivity(workspaceB, bobOnlyB, 1);
            List<String> logins = memberActivity(workspaceA).stream()
                    .map(member -> member.person().login())
                    .toList();

            assertThat(logins).contains("alice-only-a").doesNotContain("bob-only-b");
        }

        @Test
        @WithMentorUser
        void countsOnlyOwnWorkspaceActivity() {
            seedActivity(workspaceA, overlapUser, 1);
            seedActivity(workspaceB, overlapUser, 3);

            ActivityPersonDTO mentor = memberActivity(workspaceA).stream()
                    .filter(member -> member.person().login().equals("mentor"))
                    .findFirst()
                    .orElseThrow();

            assertThat(mentor.counts().issuesOpened()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Member activity")
    class MemberActivity {

        @Test
        @WithMentorUser
        void shouldReturnNotFoundWhenOpenWorkMemberOnlyBelongsToAnotherWorkspace() {
            expectDetailStatus("/activity/members/{key}/open-work", "mentor")
                    .isOk()
                    .expectBody(Void.class);
            expectDetailStatus("/activity/members/{key}/open-work", bobOnlyB.getLogin())
                    .isNotFound()
                    .expectBody(Void.class);
        }

        @Test
        @WithMentorUser
        void shouldReturnNotFoundWhenWorkMemberOnlyBelongsToAnotherWorkspace() {
            expectDetailStatus("/activity/work?login={key}", "mentor").isOk().expectBody(Void.class);
            expectDetailStatus("/activity/work?login={key}", bobOnlyB.getLogin())
                    .isNotFound()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Teams")
    class Teams {

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

        @Test
        @WithAdminUser
        void visibilityWriteIsScopedToWorkspaceProvider() {
            ensureAdminMembership(workspaceA);
            linkSyncedOrg(workspaceA, ensureGitHubProvider());
            Team ownTeam = seedTeam("own-team", 900_300L, SHARED_LOGIN, ensureGitHubProvider());
            Team foreignTeam = seedTeam("foreign-team", 900_301L, SHARED_LOGIN, ensureGitLabProvider());

            expectVisibilityWriteStatus(ownTeam.getId()).isOk().expectBody(Void.class);
            expectVisibilityWriteStatus(foreignTeam.getId()).isNotFound().expectBody(Void.class);
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
                    .uri("/workspaces/{slug}/practices/results", outsiderWorkspace.getWorkspaceSlug())
                    .headers(TestAuthUtils.withCurrentUser())
                    .exchange()
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);
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
                    .isForbidden()
                    .expectBody(Void.class);
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

    private void seedActivity(Workspace ws, User actor, int count) {
        var repository = new Repository();
        long nativeId = repositoryIds.incrementAndGet();
        repository.setNativeId(nativeId);
        repository.setProvider(ensureGitHubProvider());
        repository.setName("activity-" + nativeId);
        repository.setNameWithOwner(SHARED_LOGIN + "/activity-" + nativeId);
        repository.setHtmlUrl("https://github.com/" + repository.getNameWithOwner());
        repository = repositoryRepository.save(repository);
        var monitor = new RepositoryToMonitor();
        monitor.setWorkspace(ws);
        monitor.setNameWithOwner(repository.getNameWithOwner());
        monitorRepository.save(monitor);
        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            activityEventRepository.insertIfAbsent(
                    id,
                    "evt-" + id,
                    ActivityEventType.ISSUE_CREATED.name(),
                    Instant.now().minusSeconds(60),
                    actor.getId(),
                    ws.getId(),
                    repository.getId(),
                    "issue",
                    (long) i);
        }
    }

    private List<ActivityPersonDTO> memberActivity(Workspace workspace) {
        var response = webTestClient
                .get()
                .uri("/workspaces/{slug}/activity/people", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ActivityPeopleDTO.class)
                .returnResult()
                .getResponseBody();
        return Objects.requireNonNull(response).people();
    }
}
