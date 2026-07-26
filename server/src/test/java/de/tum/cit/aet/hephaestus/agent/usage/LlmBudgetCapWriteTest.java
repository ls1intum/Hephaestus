package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateLookup;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * What each cap write does to the row it touches: it sets its own column, records the transition, and
 * frees the jobs that were parked on that cap.
 *
 * <p>That the two writers cannot revert each other — the reason both reads are pessimistic — is a
 * property of two transactions racing for one row, so it is asserted where it can actually happen, in
 * {@link LlmBudgetCapConcurrencyIntegrationTest}. Here the locking read is simply the one that is
 * stubbed: a writer that dropped it would find no workspace and 404.
 */
class LlmBudgetCapWriteTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 7L;
    private static final String SLUG = "acme";

    @Mock
    private LlmUsageEventRepository usageRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private LlmBudgetService llmBudgetService;

    @Mock
    private ConfigAuditPort configAudit;

    @Mock
    private AgentJobRepository jobRepository;

    @Mock
    private FxRateLookup fxRateLookup;

    private LlmUsageAdminService adminService;
    private LlmUsageService workspaceService;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        adminService = new LlmUsageAdminService(
            usageRepository,
            workspaceRepository,
            configAudit,
            jobRepository,
            fxRateLookup
        );
        workspaceService = new LlmUsageService(
            usageRepository,
            workspaceRepository,
            llmBudgetService,
            configAudit,
            jobRepository,
            fxRateLookup
        );
        workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setWorkspaceSlug(SLUG);
    }

    @Test
    @DisplayName("the instance cap write sets the shared-model cap and frees the jobs held on it")
    void instanceCapWriteAppliesAndReleases() {
        when(workspaceRepository.findByWorkspaceSlugForUpdate(SLUG)).thenReturn(Optional.of(workspace));

        adminService.updateBudget(SLUG, new BigDecimal("250.00"));

        assertThat(workspace.getMonthlyLlmBudgetUsd()).isEqualByComparingTo("250.00");
        // The other purse is a different person's money and this write is not about it.
        assertThat(workspace.getMonthlyByoLlmBudgetUsd()).isNull();
        verify(configAudit).record(any());
        // Raising a cap must free the jobs held on it immediately, not up to an hour later.
        verify(jobRepository).releaseBudgetHolds(anyLong(), any());
    }

    @Test
    @DisplayName("the own-provider cap write sets the own-provider cap and frees the jobs held on it")
    void ownProviderCapWriteAppliesAndReleases() {
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));

        workspaceService.updateOwnProviderBudget(WORKSPACE_ID, new BigDecimal("40.00"));

        assertThat(workspace.getMonthlyByoLlmBudgetUsd()).isEqualByComparingTo("40.00");
        assertThat(workspace.getMonthlyLlmBudgetUsd()).isNull();
        verify(configAudit).record(any());
        verify(jobRepository).releaseBudgetHolds(anyLong(), any());
    }

    @Test
    @DisplayName("an unknown workspace is still a 404, not a lock on nothing")
    void unknownWorkspaceIsReportedAsNotFound() {
        when(workspaceRepository.findByWorkspaceSlugForUpdate("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateBudget("ghost", BigDecimal.ONE)).isInstanceOf(
            EntityNotFoundException.class
        );
        verify(configAudit, never()).record(any());
    }
}
