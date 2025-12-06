package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.GitHubInstallationRepositoryEnumerationService;
import de.tum.in.www1.hephaestus.gitprovider.label.LabelRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationSyncService;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.GitHubDataSyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardService;
import de.tum.in.www1.hephaestus.leaderboard.LeaguePointsCalculationService;
import de.tum.in.www1.hephaestus.monitoring.MonitoringScopeFilter;
import de.tum.in.www1.hephaestus.workspace.exception.InvalidWorkspaceSlugException;
import de.tum.in.www1.hephaestus.workspace.exception.WorkspaceSlugConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.AsyncTaskExecutor;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
@DisplayName("Workspace Slug Rename Tests")
class WorkspaceSlugRenameTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Mock
    private WorkspaceSlugHistoryRepository workspaceSlugHistoryRepository;

    @Mock
    private NatsConsumerService natsConsumerService;

    @Mock
    private GitHubRepositorySyncService repositorySyncService;

    @Mock
    private GitHubInstallationRepositoryEnumerationService installationRepositoryEnumerator;

    @Mock
    private MonitoringScopeFilter monitoringScopeFilter;

    @Mock
    private TeamInfoDTOConverter teamInfoDTOConverter;

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private LeaguePointsCalculationService leaguePointsCalculationService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private WorkspaceMembershipService workspaceMembershipService;

    @Mock
    private GitHubUserSyncService gitHubUserSyncService;

    @Mock
    private GitHubAppTokenService gitHubAppTokenService;

    @Mock
    private OrganizationSyncService organizationSyncService;

    @Mock
    private ObjectProvider<GitHubDataSyncService> gitHubDataSyncServiceProvider;

    @Mock
    private AsyncTaskExecutor monitoringExecutor;

    private WorkspaceService workspaceService;

    private Workspace testWorkspace;

    @BeforeEach
    void setUp() {
        workspaceService = new WorkspaceService(
            false,
            false,
            workspaceRepository,
            repositoryToMonitorRepository,
            userRepository,
            teamRepository,
            repositoryRepository,
            labelRepository,
            workspaceMembershipRepository,
            workspaceSlugHistoryRepository,
            natsConsumerService,
            repositorySyncService,
            installationRepositoryEnumerator,
            monitoringScopeFilter,
            teamInfoDTOConverter,
            leaderboardService,
            leaguePointsCalculationService,
            organizationService,
            workspaceMembershipService,
            gitHubUserSyncService,
            gitHubAppTokenService,
            organizationSyncService,
            gitHubDataSyncServiceProvider,
            monitoringExecutor
        );

        testWorkspace = new Workspace();
        testWorkspace.setId(1L);
        testWorkspace.setWorkspaceSlug("old-slug");
        testWorkspace.setDisplayName("Test Workspace");
        testWorkspace.setAccountLogin("test-account");
        testWorkspace.setAccountType(AccountType.USER);
        testWorkspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should successfully rename slug with valid new slug")
    void shouldRenameSlugSuccessfully() {
        String newSlug = "new-slug";
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
        when(workspaceRepository.existsByWorkspaceSlug(newSlug)).thenReturn(false);
        when(workspaceSlugHistoryRepository.existsByOldSlug(newSlug)).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Workspace result = workspaceService.renameSlug(1L, newSlug);

        assertThat(result.getWorkspaceSlug()).isEqualTo(newSlug);

        ArgumentCaptor<WorkspaceSlugHistory> historyCaptor = ArgumentCaptor.forClass(WorkspaceSlugHistory.class);
        verify(workspaceSlugHistoryRepository).save(historyCaptor.capture());

        WorkspaceSlugHistory history = historyCaptor.getValue();
        assertThat(history.getOldSlug()).isEqualTo("old-slug");
        assertThat(history.getNewSlug()).isEqualTo(newSlug);
        assertThat(history.getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return same workspace when renaming to current slug (no-op)")
    void shouldHandleNoOpRename() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));

        Workspace result = workspaceService.renameSlug(1L, "old-slug");

        assertThat(result.getWorkspaceSlug()).isEqualTo("old-slug");
        verify(workspaceSlugHistoryRepository, never()).save(any());
        verify(workspaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when workspace not found")
    void shouldThrowExceptionWhenWorkspaceNotFound() {
        when(workspaceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.renameSlug(999L, "new-slug"))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Workspace")
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Should throw InvalidWorkspaceSlugException for invalid slug pattern")
    void shouldRejectInvalidSlugPattern() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));

        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "ab"))
            .isInstanceOf(InvalidWorkspaceSlugException.class);
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "New-Slug"))
            .isInstanceOf(InvalidWorkspaceSlugException.class);
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "new_slug"))
            .isInstanceOf(InvalidWorkspaceSlugException.class);
        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "-newslug"))
            .isInstanceOf(InvalidWorkspaceSlugException.class);
    }

    @Test
    @DisplayName("Should throw WorkspaceSlugConflictException when slug exists in active workspace")
    void shouldRejectConflictWithActiveWorkspace() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
        when(workspaceRepository.existsByWorkspaceSlug("existing-slug")).thenReturn(true);

        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "existing-slug"))
            .isInstanceOf(WorkspaceSlugConflictException.class)
            .hasMessageContaining("existing-slug");
    }

    @Test
    @DisplayName("Should throw WorkspaceSlugConflictException when slug exists in redirect history")
    void shouldRejectConflictWithExistingRedirect() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
        when(workspaceRepository.existsByWorkspaceSlug("redirected-slug")).thenReturn(false);
        when(workspaceSlugHistoryRepository.existsByOldSlug("redirected-slug")).thenReturn(true);

        assertThatThrownBy(() -> workspaceService.renameSlug(1L, "redirected-slug"))
            .isInstanceOf(WorkspaceSlugConflictException.class)
            .hasMessageContaining("redirected-slug");
    }
}
