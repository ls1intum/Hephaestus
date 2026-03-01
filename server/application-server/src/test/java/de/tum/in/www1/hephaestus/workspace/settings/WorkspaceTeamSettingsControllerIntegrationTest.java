package de.tum.in.www1.hephaestus.workspace.settings;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.settings.dto.UpdateRepositorySettingsRequestDTO;
import de.tum.in.www1.hephaestus.workspace.settings.dto.UpdateTeamSettingsRequestDTO;
import de.tum.in.www1.hephaestus.workspace.settings.dto.WorkspaceTeamRepositorySettingsDTO;
import de.tum.in.www1.hephaestus.workspace.settings.dto.WorkspaceTeamSettingsDTO;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for {@link WorkspaceTeamSettingsController}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Team visibility settings (hidden)</li>
 *   <li>Repository contribution visibility settings</li>
 *   <li>Label filter management</li>
 *   <li>Security requirements (admin-only mutations)</li>
 * </ul>
 */
@AutoConfigureWebTestClient
@DisplayName("Workspace Team Settings Controller")
@Tag("integration")
class WorkspaceTeamSettingsControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private WorkspaceTeamSettingsRepository teamSettingsRepository;

    @Autowired
    private WorkspaceTeamRepositorySettingsRepository repositorySettingsRepository;

    @Autowired
    private WorkspaceTeamLabelFilterRepository labelFilterRepository;

    private final AtomicLong entityIdGenerator = new AtomicLong(100_000);

    private Workspace workspace;
    private Team team;
    private Repository repository;
    private Label label;
    private User owner;

    @BeforeEach
    void setUp() {
        // Arrange: Create base test data
        owner = persistUser("settings-owner-" + System.nanoTime());
        workspace = createWorkspace(
            "settings-test-" + System.nanoTime(),
            "Settings Test",
            "settings-org",
            AccountType.ORG,
            owner
        );
        team = createTeam("test-team", "settings-org");
        repository = createRepository("settings-org/test-repo");
        label = createLabel("feature", "0366d6", repository);
    }

    // ========================================================================
    // Team Visibility Settings Tests
    // ========================================================================

    @Nested
    @DisplayName("Team Visibility Settings")
    class TeamVisibilitySettingsTests {

        @Test
        @WithAdminUser
        @DisplayName("GET /teams/{teamId}/settings - as admin returns settings")
        void getTeamSettings_asAdmin_shouldReturnSettings() {
            // Arrange
            ensureAdminMembership(workspace);

            // Act & Assert
            WorkspaceTeamSettingsDTO result = webTestClient
                .get()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), team.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceTeamSettingsDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.workspaceId()).isEqualTo(workspace.getId());
            assertThat(result.teamId()).isEqualTo(team.getId());
            assertThat(result.hidden()).isFalse();
        }

        @Test
        @DisplayName("GET /teams/{teamId}/settings - as non-member returns 403")
        void getTeamSettings_asNonMember_shouldReturn403() {
            // Act & Assert
            webTestClient
                .get()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), team.getId())
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithAdminUser
        @DisplayName("PATCH /teams/{teamId}/settings - as admin updates and returns 200")
        void updateTeamHidden_asAdmin_shouldUpdateAndReturn200() {
            // Arrange
            ensureAdminMembership(workspace);
            var request = new UpdateTeamSettingsRequestDTO(true);

            // Act
            WorkspaceTeamSettingsDTO result = webTestClient
                .patch()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), team.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceTeamSettingsDTO.class)
                .returnResult()
                .getResponseBody();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.hidden()).isTrue();

            // Verify database state
            var savedSettings = teamSettingsRepository.findByWorkspaceIdAndTeamId(workspace.getId(), team.getId());
            assertThat(savedSettings).isPresent();
            assertThat(savedSettings.get().isHidden()).isTrue();
        }

        @Test
        @WithMentorUser
        @DisplayName("PATCH /teams/{teamId}/settings - as non-admin returns 403")
        void updateTeamHidden_asNonAdmin_shouldReturn403() {
            // Arrange
            persistUser("mentor");
            User memberUser = persistUser("member-" + System.nanoTime());
            ensureWorkspaceMembership(
                workspace,
                memberUser,
                de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole.MEMBER
            );
            var request = new UpdateTeamSettingsRequestDTO(true);

            // Act & Assert
            webTestClient
                .patch()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), team.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @WithAdminUser
        @DisplayName("PATCH /teams/{teamId}/settings - invalid team returns 404")
        void updateTeamHidden_invalidTeam_shouldReturn404() {
            // Arrange
            ensureAdminMembership(workspace);
            var request = new UpdateTeamSettingsRequestDTO(true);
            Long nonExistentTeamId = 999999L;

            // Act & Assert
            webTestClient
                .patch()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), nonExistentTeamId)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isNotFound();
        }
    }

    // ========================================================================
    // Repository Visibility Settings Tests
    // ========================================================================

    @Nested
    @DisplayName("Repository Visibility Settings")
    class RepositoryVisibilitySettingsTests {

        @Test
        @WithAdminUser
        @DisplayName("GET /teams/{teamId}/settings/repositories/{repoId} - as admin returns settings")
        void getRepositorySettings_asAdmin_shouldReturnSettings() {
            // Arrange
            ensureAdminMembership(workspace);

            // Act & Assert
            WorkspaceTeamRepositorySettingsDTO result = webTestClient
                .get()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/repositories/{repoId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    repository.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceTeamRepositorySettingsDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.workspaceId()).isEqualTo(workspace.getId());
            assertThat(result.teamId()).isEqualTo(team.getId());
            assertThat(result.repositoryId()).isEqualTo(repository.getId());
            assertThat(result.hiddenFromContributions()).isFalse();
        }

        @Test
        @DisplayName("GET /teams/{teamId}/settings/repositories/{repoId} - as non-member returns 401")
        void getRepositorySettings_asNonMember_shouldReturn403() {
            // Act & Assert
            webTestClient
                .get()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/repositories/{repoId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    repository.getId()
                )
                .exchange()
                .expectStatus()
                .isUnauthorized();
        }

        @Test
        @WithAdminUser
        @DisplayName("PATCH /teams/{teamId}/settings/repositories/{repoId} - as admin updates and returns 200")
        void updateRepositoryHidden_asAdmin_shouldUpdateAndReturn200() {
            // Arrange
            ensureAdminMembership(workspace);
            var request = new UpdateRepositorySettingsRequestDTO(true);

            // Act
            WorkspaceTeamRepositorySettingsDTO result = webTestClient
                .patch()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/repositories/{repoId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    repository.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceTeamRepositorySettingsDTO.class)
                .returnResult()
                .getResponseBody();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.hiddenFromContributions()).isTrue();

            // Verify database state
            var savedSettings = repositorySettingsRepository.findByWorkspaceIdAndTeamIdAndRepositoryId(
                workspace.getId(),
                team.getId(),
                repository.getId()
            );
            assertThat(savedSettings).isPresent();
            assertThat(savedSettings.get().isHiddenFromContributions()).isTrue();
        }

        @Test
        @WithMentorUser
        @DisplayName("PATCH /teams/{teamId}/settings/repositories/{repoId} - as non-admin returns 403")
        void updateRepositoryHidden_asNonAdmin_shouldReturn403() {
            // Arrange
            persistUser("mentor");
            User memberUser = persistUser("member-repo-" + System.nanoTime());
            ensureWorkspaceMembership(
                workspace,
                memberUser,
                de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole.MEMBER
            );
            var request = new UpdateRepositorySettingsRequestDTO(true);

            // Act & Assert
            webTestClient
                .patch()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/repositories/{repoId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    repository.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @WithAdminUser
        @DisplayName("PATCH /teams/{teamId}/settings/repositories/{repoId} - invalid repository returns 404")
        void updateRepositoryHidden_invalidRepository_shouldReturn404() {
            // Arrange
            ensureAdminMembership(workspace);
            var request = new UpdateRepositorySettingsRequestDTO(true);
            Long nonExistentRepoId = 999999L;

            // Act & Assert
            webTestClient
                .patch()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/repositories/{repoId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    nonExistentRepoId
                )
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isNotFound();
        }
    }

    // ========================================================================
    // Label Filter Settings Tests
    // ========================================================================

    @Nested
    @DisplayName("Label Filter Settings")
    class LabelFilterSettingsTests {

        @Test
        @WithAdminUser
        @DisplayName("GET /teams/{teamId}/settings/label-filters - as admin returns filters")
        void getLabelFilters_asAdmin_shouldReturnFilters() {
            // Arrange
            ensureAdminMembership(workspace);

            // Act & Assert
            List<LabelInfoDTO> result = webTestClient
                .get()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters",
                    workspace.getWorkspaceSlug(),
                    team.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(LabelInfoDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }

        @Test
        @WithAdminUser
        @DisplayName("POST /teams/{teamId}/settings/label-filters/{labelId} - as admin adds and returns 201")
        void addLabelFilter_asAdmin_shouldAddAndReturn201() {
            // Arrange
            ensureAdminMembership(workspace);

            // Act
            webTestClient
                .post()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    label.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isCreated();

            // Assert: Verify database state
            var filters = labelFilterRepository.findByWorkspaceIdAndTeamId(workspace.getId(), team.getId());
            assertThat(filters).hasSize(1);
            assertThat(filters.get(0).getLabel().getId()).isEqualTo(label.getId());
        }

        @Test
        @WithMentorUser
        @DisplayName("POST /teams/{teamId}/settings/label-filters/{labelId} - as non-admin returns 403")
        void addLabelFilter_asNonAdmin_shouldReturn403() {
            // Arrange
            persistUser("mentor");
            User memberUser = persistUser("member-label-" + System.nanoTime());
            ensureWorkspaceMembership(
                workspace,
                memberUser,
                de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole.MEMBER
            );

            // Act & Assert
            webTestClient
                .post()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    label.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden();
        }

        @Test
        @WithAdminUser
        @DisplayName("POST /teams/{teamId}/settings/label-filters/{labelId} - duplicate label returns existing filter")
        void addLabelFilter_duplicateLabel_shouldReturnExisting() {
            // Arrange
            ensureAdminMembership(workspace);

            // Add the filter first
            webTestClient
                .post()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    label.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isCreated();

            // Act: Try to add the same filter again
            webTestClient
                .post()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    label.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isCreated();

            // Assert: Only one filter exists
            var filters = labelFilterRepository.findByWorkspaceIdAndTeamId(workspace.getId(), team.getId());
            assertThat(filters).hasSize(1);
        }

        @Test
        @WithAdminUser
        @DisplayName("DELETE /teams/{teamId}/settings/label-filters/{labelId} - as admin removes and returns 204")
        void removeLabelFilter_asAdmin_shouldRemoveAndReturn204() {
            // Arrange
            ensureAdminMembership(workspace);
            // First add the filter
            webTestClient
                .post()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    label.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isCreated();

            // Verify filter exists
            var filtersBefore = labelFilterRepository.findByWorkspaceIdAndTeamId(workspace.getId(), team.getId());
            assertThat(filtersBefore).hasSize(1);

            // Act
            webTestClient
                .delete()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    label.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNoContent();

            // Assert: Verify database state
            var filtersAfter = labelFilterRepository.findByWorkspaceIdAndTeamId(workspace.getId(), team.getId());
            assertThat(filtersAfter).isEmpty();
        }

        @Test
        @WithAdminUser
        @DisplayName("DELETE /teams/{teamId}/settings/label-filters/{labelId} - not exists returns 404")
        void removeLabelFilter_notExists_shouldReturn404() {
            // Arrange
            ensureAdminMembership(workspace);
            Long nonExistentLabelId = 999999L;

            // Act & Assert
            webTestClient
                .delete()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    nonExistentLabelId
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isNotFound();
        }

        @Test
        @WithAdminUser
        @DisplayName("GET /teams/{teamId}/settings/label-filters - after adding filters returns them")
        void getLabelFilters_afterAdding_shouldReturnFilters() {
            // Arrange
            ensureAdminMembership(workspace);
            Label secondLabel = createLabel("bug", "d73a4a", repository);

            // Add two filters
            webTestClient
                .post()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    label.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isCreated();

            webTestClient
                .post()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters/{labelId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    secondLabel.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isCreated();

            // Act
            List<LabelInfoDTO> result = webTestClient
                .get()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/label-filters",
                    workspace.getWorkspaceSlug(),
                    team.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(LabelInfoDTO.class)
                .returnResult()
                .getResponseBody();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result).extracting(LabelInfoDTO::name).containsExactlyInAnyOrder("feature", "bug");
        }
    }

    // ========================================================================
    // XP Filtering Integration Tests
    // ========================================================================

    @Nested
    @DisplayName("XP Filtering Integration")
    class XpFilteringIntegrationTests {

        @Test
        @WithAdminUser
        @DisplayName("Hidden team settings persist across requests")
        void hiddenTeamSettings_shouldPersistAcrossRequests() {
            // Arrange
            ensureAdminMembership(workspace);

            // Act: Hide the team
            webTestClient
                .patch()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), team.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateTeamSettingsRequestDTO(true))
                .exchange()
                .expectStatus()
                .isOk();

            // Assert: Verify the team is hidden when fetching settings
            WorkspaceTeamSettingsDTO result = webTestClient
                .get()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), team.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceTeamSettingsDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.hidden()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("Hidden repository settings persist across requests")
        void hiddenRepositorySettings_shouldPersistAcrossRequests() {
            // Arrange
            ensureAdminMembership(workspace);

            // Act: Hide the repository from contributions
            webTestClient
                .patch()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/repositories/{repoId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    repository.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateRepositorySettingsRequestDTO(true))
                .exchange()
                .expectStatus()
                .isOk();

            // Assert: Verify the repository is hidden when fetching settings
            WorkspaceTeamRepositorySettingsDTO result = webTestClient
                .get()
                .uri(
                    "/workspaces/{slug}/teams/{teamId}/settings/repositories/{repoId}",
                    workspace.getWorkspaceSlug(),
                    team.getId(),
                    repository.getId()
                )
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceTeamRepositorySettingsDTO.class)
                .returnResult()
                .getResponseBody();

            assertThat(result).isNotNull();
            assertThat(result.hiddenFromContributions()).isTrue();
        }

        @Test
        @WithAdminUser
        @DisplayName("Can toggle team visibility back to visible")
        void toggleTeamVisibility_shouldWorkBothWays() {
            // Arrange
            ensureAdminMembership(workspace);

            // Act: Hide the team
            webTestClient
                .patch()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), team.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateTeamSettingsRequestDTO(true))
                .exchange()
                .expectStatus()
                .isOk();

            // Act: Unhide the team
            WorkspaceTeamSettingsDTO result = webTestClient
                .patch()
                .uri("/workspaces/{slug}/teams/{teamId}/settings", workspace.getWorkspaceSlug(), team.getId())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateTeamSettingsRequestDTO(false))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceTeamSettingsDTO.class)
                .returnResult()
                .getResponseBody();

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.hidden()).isFalse();
        }
    }

    // ========================================================================
    // Test Helper Methods
    // ========================================================================

    /**
     * Creates and persists a team entity for testing.
     *
     * @param name the team name
     * @param organization the organization name (should match workspace.accountLogin)
     * @return the persisted team
     */
    private Team createTeam(String name, String organization) {
        Team newTeam = new Team();
        newTeam.setNativeId(entityIdGenerator.incrementAndGet());
        newTeam.setName(name);
        newTeam.setOrganization(organization);
        newTeam.setDescription("Test team: " + name);
        newTeam.setHtmlUrl("https://github.com/orgs/" + organization + "/teams/" + name);
        newTeam.setPrivacy(Team.Privacy.VISIBLE);
        newTeam.setProvider(ensureGitHubProvider());
        return teamRepository.save(newTeam);
    }

    /**
     * Creates and persists a repository entity for testing.
     *
     * @param nameWithOwner the full repository name (e.g., "org/repo")
     * @return the persisted repository
     */
    private Repository createRepository(String nameWithOwner) {
        String[] parts = nameWithOwner.split("/");
        String repoName = parts.length > 1 ? parts[1] : nameWithOwner;

        Repository repo = new Repository();
        repo.setNativeId(entityIdGenerator.incrementAndGet());
        repo.setName(repoName);
        repo.setNameWithOwner(nameWithOwner);
        repo.setHtmlUrl("https://github.com/" + nameWithOwner);
        repo.setDescription("Test repository: " + nameWithOwner);
        repo.setPushedAt(Instant.now());
        repo.setCreatedAt(Instant.now());
        repo.setUpdatedAt(Instant.now());
        repo.setProvider(ensureGitHubProvider());
        return repositoryRepository.save(repo);
    }

    /**
     * Creates and persists a label entity for testing.
     *
     * @param name the label name
     * @param color the label color (hex without #)
     * @param labelRepository the repository the label belongs to
     * @return the persisted label
     */
    private Label createLabel(String name, String color, Repository labelRepository) {
        Label newLabel = new Label();
        newLabel.setId(entityIdGenerator.incrementAndGet());
        newLabel.setName(name);
        newLabel.setColor(color);
        newLabel.setDescription("Test label: " + name);
        newLabel.setRepository(labelRepository);
        return this.labelRepository.save(newLabel);
    }
}
