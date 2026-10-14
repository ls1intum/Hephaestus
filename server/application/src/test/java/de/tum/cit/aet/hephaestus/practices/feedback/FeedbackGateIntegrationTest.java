package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class FeedbackGateIntegrationTest extends AbstractFeedbackIntegrationTest {
    @Autowired
    FeedbackGate gate;

    @Autowired
    PracticeRepository practiceRepository;

    @Autowired
    PracticeVersionRepository versionRepository;

    @Autowired
    ReviewRepository reviewRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    PersonProcessingSuppression suppression;

    @Autowired
    FeedbackContentDigest digests;

    Feedback piece;

    @BeforeEach
    void admitPolicyRevision() {
        jdbc.update(
                "UPDATE workspace SET practices_enabled=true,practice_rollout_revision=4 WHERE" + " id=?",
                workspace.getId());
        jdbc.update("UPDATE review SET rollout_revision=4 WHERE workspace_id=? AND id=?", workspace.getId(), reviewId);
        piece = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
    }

    private record Supported(FeedbackGate gate, Feedback feedback, ItemSource source, ItemRef item, long practiceId) {}

    private Supported supported() {
        var result = resultWithStateFilter("open");
        var feedback = piece(FeedbackDestination.ON_WORK, FeedbackState.READY, null);
        bind(feedback, result);
        jdbc.update("UPDATE account SET feedback_delivery_enabled=true WHERE id=?", developerId);
        var item = new ItemRef(workspace.getId(), itemId, new ItemKind("scm.pull_request"), 42L, null, null);
        var source = mock(ItemSource.class);
        when(source.supports(item)).thenReturn(true);
        when(source.current(item)).thenReturn(Optional.of(snapshot(item, "open", "v1")));
        var authorization = mock(EvidenceAuthorization.class);
        when(authorization.permitsForNewDelivery(
                        eq(workspace.getId()), any(), eq(SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)))
                .thenReturn(Set.of(result.getId()));
        var nativeGate = new FeedbackGate(
                basis,
                copies,
                decisions,
                results,
                practiceRepository,
                versionRepository,
                reviewRepository,
                workspaceRepository,
                authorization,
                suppression,
                mock(SilentModeQuery.class),
                List.of(source),
                Clock.systemUTC(),
                digests);
        return new Supported(nativeGate, feedback, source, item, result.getPracticeId());
    }

    private FeedbackCheck check(Supported support) {
        return Objects.requireNonNull(new TransactionTemplate(transactionManager)
                .execute(status -> support.gate().evaluate(support.feedback(), FeedbackCheckStage.EGRESS, false)));
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
        var support = supported();
        assertThat(check(support).allowed()).isTrue();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Long next =
                    jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('practice_version','id'))", Long.class);
            jdbc.update(
                    "INSERT INTO practice_version(id,workspace_id,practice_id,number,definition) SELECT"
                            + " ?,workspace_id,practice_id,number+1,jsonb_set(definition,'{criteria}','\"A changed"
                            + " standard.\"'::jsonb) FROM practice_version WHERE id=(SELECT current_version_id FROM"
                            + " practice WHERE id=?)",
                    next,
                    support.practiceId());
            jdbc.update("UPDATE practice SET current_version_id=? WHERE id=?", next, support.practiceId());
        });
        assertThat(check(support).reason()).isEqualTo(FeedbackStopReason.REVIEWED_VERSION_CHANGED);
    }

    @Test
    void shouldRefuseChangedWorkAndClosedWorkAgainstThePersistedStateFilter() {
        var support = supported();
        when(support.source().current(support.item())).thenReturn(Optional.of(snapshot(support.item(), "open", "v2")));
        assertThat(check(support).reason()).isEqualTo(FeedbackStopReason.REVIEWED_VERSION_CHANGED);
        when(support.source().current(support.item()))
                .thenReturn(Optional.of(snapshot(support.item(), "closed", "v1")));
        var check = check(support);
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
