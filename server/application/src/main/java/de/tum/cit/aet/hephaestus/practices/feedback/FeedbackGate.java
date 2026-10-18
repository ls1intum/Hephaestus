package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.auth.domain.Account;
import de.tum.cit.aet.hephaestus.core.auth.domain.AiChoice;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonProcessingSuppression;
import de.tum.cit.aet.hephaestus.core.settings.spi.SilentModeQuery;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckFacts;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackModeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.workspace.Workspace.WorkspaceStatus;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeDeliveryStatus;
import de.tum.cit.aet.hephaestus.workspace.settings.ReviewScopeMode;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class FeedbackGate {
    private final FeedbackBasisRepository basis;
    private final FeedbackCopyRepository copies;
    private final FeedbackDecisionRepository decisions;
    private final ResultRepository results;
    private final PracticeRepository practices;
    private final PracticeVersionRepository versions;
    private final ReviewRepository reviews;
    private final FeedbackModeQuery feedbackModes;
    private final EvidenceAuthorization evidence;
    private final PersonProcessingSuppression suppression;
    private final SilentModeQuery silentMode;
    private final List<ItemSource> sources;
    private final Clock clock;
    private final FeedbackContentDigest digests;

    @Transactional(readOnly = true)
    public FeedbackCheck evaluate(Feedback feedback, FeedbackCheckStage stage, boolean checkApproval) {
        long workspaceId = feedback.getWorkspaceId();
        UUID feedbackId = feedback.getId();
        var edges = basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspaceId, feedbackId);
        var support = results.findByWorkspaceIdAndIdIn(
                workspaceId,
                edges.stream().map(FeedbackBasis::getResultId).distinct().toList());
        return evaluate(
                feedback,
                stage,
                checkApproval,
                support,
                edges.stream().map(FeedbackBasis::getResultId).distinct().count(),
                basis.hasIncorrectBasis(workspaceId, feedbackId),
                copies.hasWriteFence(workspaceId, feedbackId));
    }

    @Transactional(readOnly = true)
    public FeedbackCheck evaluateComposition(
            ReviewSnapshot review, long developerId, List<Result> support, FeedbackDestination destination) {
        var candidate = Feedback.builder()
                .id(review.id())
                .workspaceId(review.workspaceId())
                .reviewId(review.id())
                .developerId(developerId)
                .destination(destination)
                .itemId(
                        destination == FeedbackDestination.PRACTICE_PAGE
                                ? null
                                : review.item().itemId())
                .build();
        var ids = support.stream().map(Result::getId).distinct().toList();
        return evaluate(
                candidate,
                FeedbackCheckStage.COMPOSITION,
                false,
                support,
                ids.size(),
                !ids.isEmpty() && basis.hasIncorrectResults(review.workspaceId(), ids),
                false);
    }

    private FeedbackCheck evaluate(
            Feedback feedback,
            FeedbackCheckStage stage,
            boolean checkApproval,
            List<Result> support,
            long expectedResults,
            boolean incorrect,
            boolean hasWriteFence) {
        long workspaceId = feedback.getWorkspaceId();
        UUID feedbackId = feedback.getId();
        var review = reviews.findByIdAndWorkspaceId(feedback.getReviewId(), workspaceId);
        var policy = basis.policy(workspaceId, feedback.getDeveloperId());
        boolean inScope = policy.isPresent()
                && policy.get().getLeftAt() == null
                && (policy.get().getPeopleScope() != ReviewScopeMode.SELECTED
                        || policy.get().getSelected());
        boolean authorized = !support.isEmpty()
                && support.stream()
                        .allMatch(result -> Objects.equals(result.getDeveloperId(), feedback.getDeveloperId()))
                && support.stream().map(Result::getId).distinct().count() == expectedResults
                && evidence.permitsForNewDelivery(workspaceId, support, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)
                                .size()
                        == support.size();
        var itemSnapshot = currentSnapshot(feedback);
        boolean current = support.stream().allMatch(this::current) && currentItem(feedback, support, itemSnapshot);
        boolean modelAllowed = policy.isPresent() && policy.get().getAiChoice() != AiChoice.NO_AI;
        boolean deliveryEnabled = policy.isPresent()
                && policy.get().getDeliveryEnabled()
                && !suppression.isAccountSuppressed(feedback.getDeveloperId());
        boolean approvalCurrent = !checkApproval || !requiresApproval(feedback);
        if (checkApproval && !approvalCurrent) {
            var approval = decisions.findLatestApproval(workspaceId, feedbackId);
            approvalCurrent = approval.filter(row -> row.getAction() == FeedbackDecisionAction.APPROVE
                            && Objects.equals(row.getContentDigest(), digests.current(workspaceId, feedbackId)))
                    .isPresent();
        }
        long admitted = review.map(row -> row.getRolloutRevision()).orElse(0L);
        long evaluated = policy.map(FeedbackBasisRepository.PolicyRow::getRolloutRevision)
                .orElse(0L);
        var facts = new FeedbackCheckFacts(
                admitted,
                evaluated,
                inScope,
                authorized,
                modelAllowed,
                deliveryEnabled,
                incorrect,
                current,
                approvalCurrent,
                hasWriteFence);
        FeedbackStopReason refusal = null;
        if (review.isEmpty() || review.get().getStatus() == ReviewStatus.CANCELLED)
            refusal = FeedbackStopReason.REVIEW_CANCELLED;
        else if (review.get().getOrigin() == ReviewOrigin.BACKFILL) refusal = FeedbackStopReason.BACKFILL_QUIET;
        else if (policy.isEmpty()
                || !policy.get().getEnabled()
                || policy.get().getWorkspaceStatus() != WorkspaceStatus.ACTIVE)
            refusal = FeedbackStopReason.WORKSPACE_DISABLED;
        else if (admitted != evaluated) refusal = FeedbackStopReason.STALE_ROLLOUT_REVISION;
        else if (!inScope) refusal = FeedbackStopReason.OUTSIDE_CURRENT_SCOPE;
        else if (suppression.isAccountSuppressed(feedback.getDeveloperId()))
            refusal = FeedbackStopReason.PERSON_SUPPRESSED;
        else if (!modelAllowed) refusal = FeedbackStopReason.NO_AI;
        else if (policy.get().getAccountStatus() != Account.Status.ACTIVE
                && policy.get().getAccountStatus() != Account.Status.UNCLAIMED)
            refusal = FeedbackStopReason.RECIPIENT_UNAVAILABLE;
        else if (!deliveryEnabled) refusal = FeedbackStopReason.DELIVERY_OFF;
        else if (modes(support, workspaceId).contains(FeedbackMode.OFF)) refusal = FeedbackStopReason.FEEDBACK_OFF;
        else if (incorrect) refusal = FeedbackStopReason.RESULT_MARKED_INCORRECT;
        else if (!current) refusal = FeedbackStopReason.REVIEWED_VERSION_CHANGED;
        else if (!eligibleState(feedback, support, itemSnapshot)) refusal = FeedbackStopReason.ITEM_STATE_INELIGIBLE;
        else if (!authorized) refusal = FeedbackStopReason.EVIDENCE_NOT_PERMITTED;
        else if (!approvalCurrent) refusal = FeedbackStopReason.APPROVAL_STALE;
        else if (policy.get().getDeliveryStatus() == PracticeDeliveryStatus.PAUSED)
            refusal = FeedbackStopReason.WORKSPACE_DELIVERY_PAUSED;
        else if (feedback.getDestination() == FeedbackDestination.ON_WORK && silentMode.isSilentModeEngaged())
            refusal = FeedbackStopReason.INSTANCE_SILENCED;
        return new FeedbackCheck(stage, refusal == null, refusal, clock.instant(), facts);
    }

    public boolean requiresApproval(Feedback feedback) {
        return feedback.getDestination() == FeedbackDestination.ON_WORK
                && modes(feedback).contains(FeedbackMode.APPROVE_FIRST);
    }

    public boolean off(Feedback feedback) {
        return modes(feedback).contains(FeedbackMode.OFF);
    }

    private List<FeedbackMode> modes(Feedback feedback) {
        var support = results.findByWorkspaceIdAndIdIn(
                feedback.getWorkspaceId(),
                basis
                        .findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(
                                feedback.getWorkspaceId(), feedback.getId())
                        .stream()
                        .map(FeedbackBasis::getResultId)
                        .distinct()
                        .toList());
        return modes(support, feedback.getWorkspaceId());
    }

    public FeedbackMode mode(Result result) {
        return modes(List.of(result), result.getWorkspaceId()).getFirst();
    }

    private List<FeedbackMode> modes(List<Result> support, long workspaceId) {
        var modes = feedbackModes.effectiveModes(
                workspaceId, support.stream().map(Result::getPracticeId).toList());
        return support.stream()
                .map(result -> modes.getOrDefault(result.getPracticeId(), FeedbackMode.OFF))
                .toList();
    }

    private boolean current(Result result) {
        Long versionId = result.getPracticeVersionId();
        if (result.getEndedAt() != null || versionId == null || result.getEvidenceVersion() != 1) return false;
        var version = versions.findByIdAndWorkspaceId(versionId, result.getWorkspaceId());
        var practice = practices.findByIdAndWorkspaceId(result.getPracticeId(), result.getWorkspaceId());
        return version.isPresent()
                && practice.isPresent()
                && version.get()
                        .getReviewDigest()
                        .equals(practice.get().getCurrentVersion().getReviewDigest());
    }

    private Optional<ItemSnapshot> currentSnapshot(Feedback feedback) {
        Long itemId = feedback.getItemId();
        if (itemId == null) return Optional.empty();
        var rows = basis.findItems(feedback.getWorkspaceId(), List.of(itemId));
        if (rows.size() != 1 || rows.getFirst().getRemovedAt() != null) return Optional.empty();
        var row = rows.getFirst();
        var ref = new ItemRef(
                feedback.getWorkspaceId(),
                row.getId(),
                new ItemKind(row.getKind()),
                row.getSourceId(),
                row.getParentId(),
                row.getPath());
        var matching = sources.stream().filter(source -> source.supports(ref)).toList();
        return matching.size() == 1 ? matching.getFirst().current(ref) : Optional.empty();
    }

    private static boolean currentItem(Feedback feedback, List<Result> support, Optional<ItemSnapshot> snapshot) {
        Long itemId = feedback.getItemId();
        if (itemId == null) return true;
        if (snapshot.isEmpty()) return false;
        return support.stream()
                .filter(result -> result.getItemId() == itemId)
                .allMatch(result -> snapshot.get().version().value().equals(result.getItemVersion()));
    }

    private boolean eligibleState(Feedback feedback, List<Result> support, Optional<ItemSnapshot> snapshot) {
        if (feedback.getDestination() != FeedbackDestination.ON_WORK || feedback.getItemId() == null) return true;
        if (snapshot.isEmpty()) return false;
        for (var result : support) {
            if (result.getPracticeVersionId() == null) return false;
            var version = versions.findByIdAndWorkspaceId(result.getPracticeVersionId(), result.getWorkspaceId());
            if (version.isEmpty()) return false;
            var required = version.get().getDefinition().path("reviewOn").path("state");
            if (!required.isObject()) return false;
            for (String key : required.propertyNames()) {
                var allowed = required.path(key);
                String value = snapshot.get().state().get(key);
                if (value == null || !allowed.isArray()) return false;
                boolean matches = false;
                for (var candidate : allowed)
                    if (candidate.isString() && value.equals(candidate.asString())) matches = true;
                if (!matches) return false;
            }
        }
        return true;
    }
}
