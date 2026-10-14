package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

class FeedbackGateIntegrationTest extends AbstractFeedbackIntegrationTest {
    @Autowired
    FeedbackGate gate;

    @MockitoBean(name = "scmItemService", enforceOverride = true)
    ItemSource source;

    @MockitoBean
    EvidenceAuthorization authorization;

    Feedback supportedFeedback;
    ItemRef supportedItem;
    long supportedPracticeId;

    Feedback piece;

    @BeforeEach
    void admitPolicyRevision() {
        jdbc.update(
                "UPDATE workspace SET practices_enabled=true,practice_rollout_revision=4 WHERE id=?",
                workspace.getId());
        jdbc.update("UPDATE review SET rollout_revision=4 WHERE workspace_id=? AND id=?", workspace.getId(), reviewId);
        piece = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
    }

    private void supportOnWorkFeedback() {
        var result = resultWithStateFilter("open");
        supportedFeedback = piece(FeedbackDestination.ON_WORK, FeedbackState.READY, null);
        bind(supportedFeedback, result);
        supportedPracticeId = result.getPracticeId();
        jdbc.update("UPDATE account SET feedback_delivery_enabled=true WHERE id=?", developerId);
        supportedItem = new ItemRef(workspace.getId(), itemId, new ItemKind("scm.pull_request"), 42L, null, null);
        when(source.supports(supportedItem)).thenReturn(true);
        when(source.current(supportedItem)).thenReturn(Optional.of(snapshot(supportedItem, "open", "v1")));
        when(authorization.permitsForNewDelivery(
                        eq(workspace.getId()), any(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)))
                .thenReturn(Set.of(result.getId()));
    }

    private FeedbackCheck check() {
        return gate.evaluate(supportedFeedback, FeedbackCheckStage.EGRESS, false);
    }

    private static ItemSnapshot snapshot(ItemRef item, String state, String version) {
        return new ItemSnapshot(
                item,
                new ItemVersion(version, null),
                new IntegrationRef(IntegrationKind.GITHUB, 1, null),
                Map.of("state", state),
                List.of(),
                new ItemLabel(item.itemId(), "Change", "pull request", null, null, null, IntegrationKind.GITHUB));
    }

    @Test
    void shouldRefuseAnEditedPracticeAfterReadingItsPersistedCurrentVersion() {
        supportOnWorkFeedback();
        assertThat(check().allowed()).isTrue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Long next =
                    jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('practice_version','id'))", Long.class);
            jdbc.update(
                    "INSERT INTO practice_version(id,workspace_id,practice_id,number,definition) SELECT"
                            + " ?,workspace_id,practice_id,number+1,jsonb_set(definition,'{criteria}','\"A changed"
                            + " standard.\"'::jsonb) FROM practice_version WHERE id=(SELECT current_version_id FROM"
                            + " practice WHERE id=?)",
                    next,
                    supportedPracticeId);
            jdbc.update("UPDATE practice SET current_version_id=? WHERE id=?", next, supportedPracticeId);
        });
        assertThat(check().reason()).isEqualTo(FeedbackStopReason.REVIEWED_VERSION_CHANGED);
    }

    @Test
    void shouldRefuseChangedWorkAndClosedWorkAgainstThePersistedStateFilter() {
        supportOnWorkFeedback();
        when(source.current(supportedItem)).thenReturn(Optional.of(snapshot(supportedItem, "open", "v2")));
        assertThat(check().reason()).isEqualTo(FeedbackStopReason.REVIEWED_VERSION_CHANGED);
        when(source.current(supportedItem)).thenReturn(Optional.of(snapshot(supportedItem, "closed", "v1")));
        var check = check();
        assertThat(check.reason()).isEqualTo(FeedbackStopReason.ITEM_STATE_INELIGIBLE);
        assertThat(check.facts().currentVersion()).isTrue();
    }

    @Test
    void shouldRefuseChangedRolloutWhenNativePolicyNoLongerMatchesAdmission() {
        jdbc.update("UPDATE workspace SET practice_rollout_revision=5 WHERE id=?", workspace.getId());
        var check = gate.evaluate(piece, FeedbackCheckStage.EGRESS, false);
        assertThat(check.reason()).isEqualTo(FeedbackStopReason.STALE_ROLLOUT_REVISION);
        assertThat(check.facts().admittedRolloutRevision()).isEqualTo(4);
        assertThat(check.facts().evaluatedRolloutRevision()).isEqualTo(5);
    }

    @Test
    void shouldRefuseOptedOutDeveloperWhenTheNativeAccountChoiceChanges() {
        jdbc.update("UPDATE account SET ai_choice='NO_AI',ai_choice_at=now() WHERE id=?", developerId);
        assertThat(gate.evaluate(piece, FeedbackCheckStage.EGRESS, false).reason())
                .isEqualTo(FeedbackStopReason.NO_AI);
    }

    @Test
    void shouldRefuseUnselectedDeveloperWhenNativeMembershipLeavesSelectedScope() {
        jdbc.update("UPDATE workspace SET review_scope_people='SELECTED' WHERE id=?", workspace.getId());
        jdbc.update(
                "UPDATE membership SET review_selected=false WHERE workspace_id=? AND account_id=?",
                workspace.getId(),
                developerId);
        assertThat(gate.evaluate(piece, FeedbackCheckStage.EGRESS, false).reason())
                .isEqualTo(FeedbackStopReason.OUTSIDE_CURRENT_SCOPE);
    }
}
