package de.tum.cit.aet.hephaestus.agent.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewField;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class AiSettingsServiceTest extends BaseUnitTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private ConfigAuditPort configAudit;

    private AiSettingsService service;
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
        false
    );

    @BeforeEach
    void setUp() {
        service = new AiSettingsService(workspaceRepository, agentConfigRepository, reviewProperties, configAudit);
        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setWorkspaceSlug("ws");
        context = new WorkspaceContext(1L, "ws", "Ws", AccountType.ORG, null, false, false, Set.of());
        // lenient: the read-only getter resolves through findById, the audited writes through the
        // locking variant (which serializes the before-snapshot with the mutation), so each test uses
        // exactly one of the two.
        lenient().when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        lenient().when(workspaceRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workspace));
    }

    @Test
    void getSettingsReturnsEffectiveValuesAndBindings() {
        workspace.setPracticeConfigId(10L);
        workspace.getReviewSettings().setRunForAllUsers(true); // explicit override

        AiSettingsViewDTO view = service.getSettings(context);

        assertThat(view.practiceConfigId()).isEqualTo(10L);
        assertThat(view.mentorConfigId()).isNull();
        assertThat(view.runForAllUsers()).isTrue(); // override wins
        assertThat(view.skipDrafts()).isTrue(); // inherited from property
        assertThat(view.cooldownMinutes()).isEqualTo(15); // inherited from property
        // Raw overrides distinguish explicit-set from inherited.
        assertThat(view.runForAllUsersOverride()).isTrue();
        assertThat(view.skipDraftsOverride()).isNull(); // inheriting
        assertThat(view.cooldownMinutesOverride()).isNull();
    }

    @Test
    void bindPracticeConfigSetsValidatedId() {
        AgentConfig config = new AgentConfig();
        config.setId(10L);
        when(agentConfigRepository.findByIdAndWorkspaceId(10L, 1L)).thenReturn(Optional.of(config));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.bindPracticeConfig(context, 10L);

        assertThat(workspace.getPracticeConfigId()).isEqualTo(10L);
        verify(workspaceRepository).save(workspace);
    }

    @Test
    void effectiveValueUsesOverrideOverPropertyWhenOverrideIsFalse() {
        // Property skipDrafts=true, workspace override=false → effective must be false (override wins,
        // not an OR). Guards the resolution precedence in both directions.
        workspace.getReviewSettings().setSkipDrafts(false);

        AiSettingsViewDTO view = service.getSettings(context);

        assertThat(view.skipDrafts()).isFalse();
    }

    @Test
    void bindRejectsForeignConfigId() {
        when(agentConfigRepository.findByIdAndWorkspaceId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bindMentorConfig(context, 99L)).isInstanceOf(EntityNotFoundException.class);

        verify(workspaceRepository, never()).save(any());
    }

    @Test
    void bindWithNullUnbindsWithoutValidation() {
        workspace.setMentorConfigId(10L);
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.bindMentorConfig(context, null);

        assertThat(workspace.getMentorConfigId()).isNull();
        verify(agentConfigRepository, never()).findByIdAndWorkspaceId(any(), any());
    }

    @Test
    void updatePracticeReviewAppliesPatch() {
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsViewDTO view = service.updatePracticeReview(
            context,
            new UpdatePracticeReviewSettingsDTO(null, false, null, 30, null)
        );

        assertThat(workspace.getReviewSettings().getSkipDrafts()).isFalse();
        assertThat(workspace.getReviewSettings().getCooldownMinutes()).isEqualTo(30);
        assertThat(workspace.getReviewSettings().getRunForAllUsers()).isNull(); // untouched
        // The returned view (what the UI consumes) reflects the patch.
        assertThat(view.skipDrafts()).isFalse();
        assertThat(view.cooldownMinutes()).isEqualTo(30);
    }

    @Test
    void updatePracticeReviewResetsFieldToInherit() {
        workspace.getReviewSettings().setSkipDrafts(false); // explicit override
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiSettingsViewDTO view = service.updatePracticeReview(
            context,
            new UpdatePracticeReviewSettingsDTO(null, null, null, null, Set.of(PracticeReviewField.SKIP_DRAFTS))
        );

        // Override cleared → inheriting again; effective falls back to the fleet default (true).
        assertThat(workspace.getReviewSettings().getSkipDrafts()).isNull();
        assertThat(view.skipDraftsOverride()).isNull();
        assertThat(view.skipDrafts()).isTrue();
    }
}
