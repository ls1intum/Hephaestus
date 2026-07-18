package de.tum.cit.aet.hephaestus.integration.scm.github.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceLifecycleService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * The uninstall half of the listener's contract: a vendor-side GitHub App uninstall must run
 * {@link WorkspaceLifecycleService#purgeWorkspace(String)} — the same chain an admin-initiated
 * workspace deletion runs — and the generic status helper must refuse {@code PURGED} so no future
 * caller can reintroduce a terminal "purged" label with none of the erasure behind it.
 *
 * <p>Only the two collaborators these paths touch are wired; the remaining constructor arguments
 * are {@code null} because nothing under test reaches them.
 */
class GithubLifecycleListenerPurgeTest extends BaseUnitTest {

    private static final long INSTALLATION_ID = 5001L;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceLifecycleService workspaceLifecycleService;

    private GithubLifecycleListener listener;

    @BeforeEach
    void setUp() {
        listener = new GithubLifecycleListener(
            null,
            workspaceRepository,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            workspaceLifecycleService
        );
    }

    @Test
    @DisplayName("purgeWorkspaceForInstallation delegates to the canonical purge")
    void purgeWorkspaceForInstallation_delegatesToWorkspaceLifecycleService() {
        Workspace workspace = workspace(11L, "acme");
        when(workspaceRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(workspace));
        when(workspaceLifecycleService.purgeWorkspace("acme")).thenReturn(workspace);

        assertThat(listener.purgeWorkspaceForInstallation(INSTALLATION_ID)).contains(workspace);
    }

    @Test
    @DisplayName("the SPI uninstall hook purges rather than flipping a status")
    void onInstanceUninstalled_purgesTheWorkspace() {
        Workspace workspace = workspace(11L, "acme");
        when(workspaceRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(workspace));
        when(workspaceLifecycleService.purgeWorkspace("acme")).thenReturn(workspace);

        listener.onInstanceUninstalled(new IntegrationRef(IntegrationKind.GITHUB, 11L, Long.toString(INSTALLATION_ID)));

        verify(workspaceLifecycleService).purgeWorkspace("acme");
    }

    @Test
    @DisplayName("an installation with no bound workspace purges nothing")
    void purgeWorkspaceForInstallation_withoutAWorkspace_isASilentNoOp() {
        when(workspaceRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        assertThat(listener.purgeWorkspaceForInstallation(INSTALLATION_ID)).isEmpty();

        verifyNoInteractions(workspaceLifecycleService);
    }

    @Test
    @DisplayName("updateWorkspaceStatus refuses PURGED outright")
    void updateWorkspaceStatus_rejectsPurgedSoItCannotBypassThePurgeChain() {
        assertThatThrownBy(() -> listener.updateWorkspaceStatus(INSTALLATION_ID, Workspace.WorkspaceStatus.PURGED))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("purgeWorkspaceForInstallation");

        verifyNoInteractions(workspaceRepository, workspaceLifecycleService);
    }

    private static Workspace workspace(long id, String slug) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        workspace.setWorkspaceSlug(slug);
        return workspace;
    }
}
