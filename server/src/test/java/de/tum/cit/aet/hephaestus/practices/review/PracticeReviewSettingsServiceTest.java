package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.HealthVisibility;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
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

    // Fleet defaults: runForAll=false, skipDrafts=true, deliverToMerged=false, cooldown=15
    private final PracticeReviewProperties reviewProperties = new PracticeReviewProperties(
        false,
        true,
        false,
        "",
        15,
        false,
        false,
        28
    );

    @BeforeEach
    void setUp() {
        service = new PracticeReviewSettingsService(workspaceRepository, reviewProperties, configAudit);
        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("ws");
        context = new WorkspaceContext(
            1L,
            "ws",
            "Ws",
            AccountType.ORG,
            null,
            false,
            false,
            HealthVisibility.MENTORS_ONLY,
            Set.of()
        );
        // lenient: the read-only getter resolves through findById, the audited writes through the
        // locking variant, so each test uses exactly one of the two.
        lenient().when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        lenient().when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workspace));
    }

    @Test
    void getSettingsReturnsEffectiveAndRawOverrideValues() {
        workspace.getReviewSettings().setRunForAllUsers(true);

        PracticeReviewSettingsDTO view = service.getSettings(context);

        assertThat(view.runForAllUsers()).isTrue();
        assertThat(view.skipDrafts()).isTrue();
        assertThat(view.cooldownMinutes()).isEqualTo(15);
        assertThat(view.runForAllUsersOverride()).isTrue();
        assertThat(view.skipDraftsOverride()).isNull();
        assertThat(view.cooldownMinutesOverride()).isNull();
    }

    @Test
    void effectiveValueUsesOverrideOverPropertyWhenOverrideIsFalse() {
        workspace.getReviewSettings().setSkipDrafts(false);

        PracticeReviewSettingsDTO view = service.getSettings(context);

        assertThat(view.skipDrafts()).isFalse();
    }

    @Test
    void updatePracticeReviewAppliesThePatchAndReturnsTheUpdatedView() {
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PracticeReviewSettingsDTO view = service.updatePracticeReview(
            context,
            new UpdatePracticeReviewSettingsRequestDTO(null, false, null, 30, null)
        );

        assertThat(workspace.getReviewSettings().getSkipDrafts()).isFalse();
        assertThat(workspace.getReviewSettings().getCooldownMinutes()).isEqualTo(30);
        assertThat(workspace.getReviewSettings().getRunForAllUsers()).isNull(); // untouched
        assertThat(view.skipDrafts()).isFalse();
        assertThat(view.cooldownMinutes()).isEqualTo(30);
    }
}
