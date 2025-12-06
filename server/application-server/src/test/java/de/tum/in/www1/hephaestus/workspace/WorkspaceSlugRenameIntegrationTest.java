package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import org.kohsuke.github.GHRepositorySelection;

import de.tum.in.www1.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.RenameWorkspaceSlugRequestDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Workspace slug rename integration")
class WorkspaceSlugRenameIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceSlugHistoryRepository workspaceSlugHistoryRepository;

    @Test
    @WithAdminUser
    void ownerCanRenameSlugAndHistoryIsStored() {
        User owner = persistUser("slug-owner");
        Workspace workspace = createWorkspace("old-slug", "Old", "old", AccountType.ORG, owner);
        ensureOwnerMembership(workspace);

        RenameWorkspaceSlugRequestDTO request = new RenameWorkspaceSlugRequestDTO("new-slug");

        Workspace updated = webTestClient
            .patch()
            .uri("/workspaces/{workspaceSlug}/slug", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(Workspace.class)
            .returnResult()
            .getResponseBody();

        Workspace persisted = workspaceRepository.findById(workspace.getId()).orElseThrow();

        assertThat(updated).isNotNull();
        assertThat(updated.getWorkspaceSlug()).isEqualTo("new-slug");
        assertThat(persisted.getWorkspaceSlug()).isEqualTo("new-slug");

        List<WorkspaceSlugHistory> history = workspaceSlugHistoryRepository.findByWorkspaceOrderByChangedAtDesc(persisted);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getOldSlug()).isEqualTo("old-slug");
        assertThat(history.getFirst().getNewSlug()).isEqualTo("new-slug");
    }

    @Test
    @WithAdminUser
    void slugHistoryIsPrunedToRetentionLimit() {
        User owner = persistUser("slug-prune-owner");
        Workspace workspace = createWorkspace("slug-0", "Slug", "slug", AccountType.ORG, owner);
        ensureOwnerMembership(workspace);

        String currentSlug = workspace.getWorkspaceSlug();
        for (int i = 1; i <= 6; i++) {
            String nextSlug = "slug-" + i;
            RenameWorkspaceSlugRequestDTO request = new RenameWorkspaceSlugRequestDTO(nextSlug);

            webTestClient
                .patch()
                .uri("/workspaces/{workspaceSlug}/slug", currentSlug)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk();

            currentSlug = nextSlug;
        }

        Workspace persisted = workspaceRepository.findById(workspace.getId()).orElseThrow();
        List<WorkspaceSlugHistory> history = workspaceSlugHistoryRepository.findByWorkspaceOrderByChangedAtDesc(persisted);

        assertThat(history).hasSize(5);
        assertThat(history.getFirst().getNewSlug()).isEqualTo("slug-6");
        assertThat(history.getLast().getOldSlug()).isEqualTo("slug-1");
    }

    @Test
    @WithAdminUser
    void renamingToSlugInAnotherWorkspaceHistoryReturnsConflict() {
        User ownerA = persistUser("owner-a");
        User ownerB = persistUser("owner-b");

        Workspace workspaceA = createWorkspace("alpha", "Alpha", "alpha", AccountType.ORG, ownerA);
        Workspace workspaceB = createWorkspace("beta", "Beta", "beta", AccountType.ORG, ownerB);

        ensureOwnerMembership(workspaceA);
        ensureOwnerMembership(workspaceB);

        // Create history entry in workspace B using service to ensure redirect records
        workspaceService.renameSlug(workspaceB.getId(), "beta-renamed");

        RenameWorkspaceSlugRequestDTO request = new RenameWorkspaceSlugRequestDTO("beta");

        webTestClient
            .patch()
            .uri("/workspaces/{workspaceSlug}/slug", workspaceA.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT)
            .expectBody(ProblemDetail.class)
            .value(problem -> assertThat(problem.getTitle()).containsIgnoringCase("conflict"));
    }

    @Test
    @WithAdminUser
    void creatingWorkspaceCollidingWithHistoryIsRejected() {
        User ownerA = persistUser("owner-a-history");
        User ownerB = persistUser("owner-b-history");

        Workspace workspaceA = createWorkspace("history-alpha", "Alpha", "alpha", AccountType.ORG, ownerA);
        ensureOwnerMembership(workspaceA);

        // Rename to create history entry for the old slug
        workspaceService.renameSlug(workspaceA.getId(), "history-alpha-renamed");

        CreateWorkspaceRequestDTO request = new CreateWorkspaceRequestDTO(
            "history-alpha",
            "Beta",
            "beta-account",
            AccountType.ORG,
            ownerB.getId()
        );

        webTestClient
            .post()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @WithAdminUser
    void installationCreationCollidingWithHistoryFailsFast() {
        User owner = persistUser("install-owner");
        // Ensure a user exists with the installation account login so owner sync succeeds
        persistUser("install-alpha");
        Workspace workspace = createWorkspace("install-alpha", "Alpha", "alpha", AccountType.ORG, owner);
        ensureOwnerMembership(workspace);

        workspaceService.renameSlug(workspace.getId(), "install-alpha-renamed");

        Workspace created = workspaceService.ensureForInstallation(999L, "install-alpha", GHRepositorySelection.ALL);

        assertThat(created).as("workspace should be created with fallback slug").isNotNull();
        assertThat(created.getWorkspaceSlug()).isNotEqualTo("install-alpha");
        assertThat(created.getWorkspaceSlug()).startsWith("install-alpha".substring(0, 3));
        assertThat(created.getGitProviderMode()).isEqualTo(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
    }

    @Test
    @WithAdminUser
    void installationCollisionDoesNotHijackExistingWorkspace() {
        User existingOwner = persistUser("existing-owner");
        Workspace existing = createWorkspace("collision", "Collision", "collision", AccountType.ORG, existingOwner);
        ensureOwnerMembership(existing);

        persistUser("install-owner-collision");
        persistUser("collision");

        Workspace linked = workspaceService.ensureForInstallation(1111L, "collision", GHRepositorySelection.ALL);

        assertThat(linked).isNotNull();
        assertThat(linked.getId()).isEqualTo(existing.getId());
        assertThat(linked.getWorkspaceSlug()).isEqualTo("collision");
        assertThat(workspaceSlugHistoryRepository.findByWorkspaceOrderByChangedAtDesc(existing)).isEmpty();
    }

    @Test
    @WithAdminUser
    void renameAllowsReusingExpiredHistorySlug() {
        User owner = persistUser("expired-owner");
        Workspace workspace = createWorkspace("ttl-old", "Old", "old", AccountType.ORG, owner);
        ensureOwnerMembership(workspace);

        workspaceService.renameSlug(workspace.getId(), "ttl-new");

        WorkspaceSlugHistory history = workspaceSlugHistoryRepository
            .findFirstByOldSlugOrderByChangedAtDesc("ttl-old")
            .orElseThrow();
        history.setRedirectExpiresAt(Instant.now().minus(2, ChronoUnit.DAYS));
        workspaceSlugHistoryRepository.save(history);

        Workspace renamed = workspaceService.renameSlug(workspace.getId(), "ttl-old");

        assertThat(renamed.getWorkspaceSlug()).isEqualTo("ttl-old");
    }
}