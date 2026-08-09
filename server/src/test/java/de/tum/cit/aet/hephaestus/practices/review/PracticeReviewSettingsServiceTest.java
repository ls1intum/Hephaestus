package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewField;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PracticeReviewSettingsServiceTest extends BaseUnitTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ConfigAuditPort configAudit;

    private PracticeReviewSettingsService service;
    private Workspace workspace;
    private WorkspaceContext context;

    private final PracticeReviewProperties reviewProperties = new PracticeReviewProperties(
        false,
        false,
        15,
        5,
        false,
        false
    );

    @BeforeEach
    void setUp() {
        service = new PracticeReviewSettingsService(workspaceRepository, reviewProperties, configAudit);
        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("ws");
        context = new WorkspaceContext(1L, "ws", "Ws", AccountType.ORG, null, false, false, Set.of());
    }

    private void readsWorkspace() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
    }

    private void writesWorkspace() {
        when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workspace));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getSettingsReturnsEffectiveAndRawOverrideValues() {
        readsWorkspace();
        workspace.getReviewSettings().setRunForAllUsers(true);

        PracticeReviewSettingsDTO view = service.getSettings(context);

        assertThat(view.runForAllUsers()).isTrue();
        assertThat(view.cooldownMinutes()).isEqualTo(15);
        assertThat(view.runForAllUsersOverride()).isTrue();
        assertThat(view.cooldownMinutesOverride()).isNull();
    }

    @Test
    void effectiveValueUsesOverrideOverPropertyWhenOverrideIsFalse() {
        readsWorkspace();
        workspace.getReviewSettings().setDeliverToMerged(true);

        PracticeReviewSettingsDTO view = service.getSettings(context);

        assertThat(view.deliverToMerged()).isTrue();
        assertThat(view.deliverToMergedOverride()).isTrue();
    }

    @Test
    void updatePracticeReviewAppliesThePatchAndReturnsTheUpdatedView() {
        writesWorkspace();

        PracticeReviewSettingsDTO view = service.updatePracticeReview(
            context,
            new UpdatePracticeReviewSettingsRequestDTO(null, true, 30, null, null)
        );

        assertThat(workspace.getReviewSettings().getDeliverToMerged()).isTrue();
        assertThat(workspace.getReviewSettings().getCooldownMinutes()).isEqualTo(30);
        assertThat(workspace.getReviewSettings().getRunForAllUsers()).isNull(); // untouched
        assertThat(view.deliverToMerged()).isTrue();
        assertThat(view.cooldownMinutes()).isEqualTo(30);
    }

    @Test
    void updatePracticeReviewReplacesTheReviewScopeWholesale() {
        writesWorkspace();
        workspace.getReviewSettings().applyScope(new WorkspaceReviewScope(List.of("main", "develop"), List.of()));

        PracticeReviewSettingsDTO view = service.updatePracticeReview(
            context,
            new UpdatePracticeReviewSettingsRequestDTO(
                null,
                null,
                null,
                new WorkspaceReviewScope(List.of("main"), List.of()),
                null
            )
        );

        // Replaced, not merged: the lists ARE the setting, so dropping "develop" has to be expressible.
        assertThat(view.reviewScope().targetBranches()).containsExactly("main");
    }

    @Test
    void twoEmptyListsClearTheScopeBackToUnrestricted() {
        writesWorkspace();
        workspace.getReviewSettings().applyScope(new WorkspaceReviewScope(List.of("main"), List.of()));

        PracticeReviewSettingsDTO view = service.updatePracticeReview(
            context,
            new UpdatePracticeReviewSettingsRequestDTO(null, null, null, WorkspaceReviewScope.UNRESTRICTED, null)
        );

        assertThat(view.reviewScope().isUnrestricted()).isTrue();
        // Stored as null rather than an empty object: "never configured" and "configured to nothing" are
        // the same fact, and keeping two spellings of it invites readers to distinguish them.
        assertThat(workspace.getReviewSettings().getReviewScope()).isNull();
    }

    @Test
    void namingReviewScopeInResetClearsIt() {
        writesWorkspace();
        workspace.getReviewSettings().applyScope(new WorkspaceReviewScope(List.of("main"), List.of()));

        service.updatePracticeReview(
            context,
            new UpdatePracticeReviewSettingsRequestDTO(null, null, null, null, Set.of(PracticeReviewField.REVIEW_SCOPE))
        );

        assertThat(workspace.getReviewSettings().getReviewScope()).isNull();
    }
}
