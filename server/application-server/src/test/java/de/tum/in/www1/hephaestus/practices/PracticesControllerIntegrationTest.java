package de.tum.in.www1.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Practices controller integration")
class PracticesControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    private final AtomicLong idGenerator = new AtomicLong(100_000);

    private Workspace workspace;
    private User owner;

    @BeforeEach
    void setUpWorkspace() {
        owner = persistUser("practices-owner");
        workspace = createWorkspace("practices-space", "Practices Space", "practices", AccountType.ORG, owner);
    }

    @Nested
    @DisplayName("GET /practices/pullrequest/{pullRequestId}")
    class GetBadPracticesForPullRequest {

        @Test
        @WithAdminUser
        @DisplayName("should return not found when pull request does not exist")
        void shouldReturnNotFoundWhenPullRequestDoesNotExist() {
            ensureAdminMembership(workspace);
            Long nonExistentPullRequestId = 999999L;

            ProblemDetail problem = webTestClient
                .get()
                .uri(
                    "/workspaces/{workspaceSlug}/practices/pullrequest/{pullRequestId}",
                    workspace.getWorkspaceSlug(),
                    nonExistentPullRequestId
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Resource not found");
            assertThat(problem.getDetail()).contains(String.valueOf(nonExistentPullRequestId));
        }

        @Test
        @DisplayName("should return unauthorized when not logged in")
        void shouldReturnUnauthorizedWhenNotLoggedIn() {
            Long pullRequestId = 123L;

            webTestClient
                .get()
                .uri(
                    "/workspaces/{workspaceSlug}/practices/pullrequest/{pullRequestId}",
                    workspace.getWorkspaceSlug(),
                    pullRequestId
                )
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    @Nested
    @DisplayName("POST /practices/pullrequest/{pullRequestId}/detect")
    class DetectBadPracticesForPullRequest {

        @Test
        @DisplayName("should return unauthorized when not logged in")
        void shouldReturnUnauthorizedWhenNotLoggedIn() {
            Long pullRequestId = 123L;

            webTestClient
                .post()
                .uri(
                    "/workspaces/{workspaceSlug}/practices/pullrequest/{pullRequestId}/detect",
                    workspace.getWorkspaceSlug(),
                    pullRequestId
                )
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Workspace filtering")
    class WorkspaceFiltering {

        @Test
        @WithAdminUser
        @DisplayName("should filter pull requests by workspace properly")
        void getWorkspacePullRequests_ShouldFilterByWorkspaceProperly() {
            // Create two workspaces with their own organizations
            User ownerAlpha = persistUser("alpha-owner");
            User ownerBeta = persistUser("beta-owner");

            // Create organizations for each workspace
            Organization orgAlpha = new Organization();
            orgAlpha.setId(idGenerator.incrementAndGet());
            orgAlpha.setProviderId(idGenerator.incrementAndGet());
            orgAlpha.setLogin("alpha-org");
            orgAlpha.setName("Alpha Org");
            orgAlpha.setAvatarUrl("https://example.com/alpha.png");
            orgAlpha.setHtmlUrl("https://github.com/alpha-org");
            orgAlpha.setCreatedAt(Instant.now());
            orgAlpha.setUpdatedAt(Instant.now());
            orgAlpha = organizationRepository.save(orgAlpha);

            Organization orgBeta = new Organization();
            orgBeta.setId(idGenerator.incrementAndGet());
            orgBeta.setProviderId(idGenerator.incrementAndGet());
            orgBeta.setLogin("beta-org");
            orgBeta.setName("Beta Org");
            orgBeta.setAvatarUrl("https://example.com/beta.png");
            orgBeta.setHtmlUrl("https://github.com/beta-org");
            orgBeta.setCreatedAt(Instant.now());
            orgBeta.setUpdatedAt(Instant.now());
            orgBeta = organizationRepository.save(orgBeta);

            // Create workspaces
            Workspace workspaceAlpha = createWorkspace(
                "alpha-space",
                "Alpha",
                "alpha-org",
                AccountType.ORG,
                ownerAlpha
            );
            Workspace workspaceBeta = createWorkspace("beta-space", "Beta", "beta-org", AccountType.ORG, ownerBeta);

            // Link workspaces to their organizations
            workspaceAlpha.setOrganization(orgAlpha);
            workspaceAlpha = workspaceRepository.save(workspaceAlpha);

            workspaceBeta.setOrganization(orgBeta);
            workspaceBeta = workspaceRepository.save(workspaceBeta);

            ensureAdminMembership(workspaceAlpha);
            ensureAdminMembership(workspaceBeta);

            // Create repository for Alpha org
            Repository repoAlpha = new Repository();
            repoAlpha.setId(idGenerator.incrementAndGet());
            repoAlpha.setName("alpha-repo");
            repoAlpha.setNameWithOwner("alpha-org/alpha-repo");
            repoAlpha.setHtmlUrl("https://github.com/alpha-org/alpha-repo");
            repoAlpha.setVisibility(Repository.Visibility.PUBLIC);
            repoAlpha.setOrganization(orgAlpha);
            repoAlpha.setDefaultBranch("main");
            repoAlpha.setCreatedAt(Instant.now());
            repoAlpha.setUpdatedAt(Instant.now());
            repoAlpha = repositoryRepository.save(repoAlpha);

            // Create a pull request in Alpha workspace
            PullRequest prAlpha = new PullRequest();
            prAlpha.setId(idGenerator.incrementAndGet());
            prAlpha.setNumber(1);
            prAlpha.setTitle("Alpha PR");
            prAlpha.setState(Issue.State.OPEN);
            prAlpha.setHtmlUrl("https://github.com/alpha-org/alpha-repo/pull/1");
            prAlpha.setRepository(repoAlpha);
            prAlpha.setCreatedAt(Instant.now());
            prAlpha.setUpdatedAt(Instant.now());
            prAlpha = pullRequestRepository.save(prAlpha);

            // Access the PR through Alpha workspace - should work
            webTestClient
                .get()
                .uri(
                    "/workspaces/{workspaceSlug}/practices/pullrequest/{pullRequestId}",
                    workspaceAlpha.getWorkspaceSlug(),
                    prAlpha.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk();

            // Access the same PR through Beta workspace - should return 404 (not found)
            ProblemDetail problem = webTestClient
                .get()
                .uri(
                    "/workspaces/{workspaceSlug}/practices/pullrequest/{pullRequestId}",
                    workspaceBeta.getWorkspaceSlug(),
                    prAlpha.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

            assertThat(problem).isNotNull();
            assertThat(problem.getTitle()).isEqualTo("Resource not found");
            assertThat(problem.getDetail()).contains(String.valueOf(prAlpha.getId()));
        }
    }
}
