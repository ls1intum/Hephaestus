package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository.ResultFeedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository.PostedCommentUrl;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultDetailDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultListDTO;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunNarrativeLookup;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunNarrativeLookup.ReviewRunNarrative;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Developer-scoped result reads and workspace-scoped supporting queries.
 * Pull-request reads intentionally include results about other developers.
 */
@Service
@RequiredArgsConstructor
public class PracticeResultService {

    private final PracticeResultRepository practiceResultRepository;
    private final FeedbackEvidenceRepository feedbackEvidenceRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;
    private final UserRepository userRepository;
    private final ReviewRunLookup reviewRunLookup;
    private final ReviewRunNarrativeLookup reviewRunNarrativeLookup;
    private final EvidenceAuthorization evidenceAuthorization;
    private final PracticeResultInvalidationRepository invalidationRepository;
    private final ObservationRepository observationRepository;

    /** Feed ordering: by result time or by severity (direction applies to both). */
    public enum ResultSort {
        DATE,
        SEVERITY,
    }

    /**
     * Paginated results for the current user in a workspace, with optional filters. Each row names its problems in
     * display order only when the result detail would show them: {@link EvidenceAuthorization} decides both.
     *
     * @return empty page if user is not a synced developer
     */
    @Transactional(readOnly = true)
    public Page<PracticeResultListDTO> getPracticeResults(Long workspaceId, ResultFeedQuery query, Pageable pageable) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<PracticeResult> page = findPracticeResults(currentUser.get().getId(), workspaceId, query, pageable);
        Set<UUID> evidencePermitted = page.isEmpty()
                ? Set.of()
                : evidenceAuthorization.permitsAll(
                        workspaceId, page.getContent(), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        Map<UUID, List<Observation>> problems = evidencePermitted.isEmpty()
                ? Map.of()
                : observationRepository.findByPracticeResultIds(workspaceId, evidencePermitted).stream()
                        .collect(Collectors.groupingBy(Observation::getPracticeResultId));
        return page.map(practiceResult ->
                PracticeResultListDTO.from(practiceResult, problems.getOrDefault(practiceResult.getId(), List.of())));
    }

    private Page<PracticeResult> findPracticeResults(
            Long developerId, Long workspaceId, ResultFeedQuery query, Pageable pageable) {
        // An IN () over an empty list is invalid SQL. The flags disable empty filters, while the placeholder
        // values keep the query parseable.
        boolean hasArtifactKinds =
                query.artifactKinds() != null && !query.artifactKinds().isEmpty();
        boolean hasSeverities =
                query.severities() != null && !query.severities().isEmpty();
        List<ArtifactKind> artifactKinds =
                hasArtifactKinds ? Objects.requireNonNull(query.artifactKinds()) : List.of(ArtifactKinds.PULL_REQUEST);
        List<Severity> severities = hasSeverities ? Objects.requireNonNull(query.severities()) : List.of(Severity.INFO);
        if (query.sort() == ResultSort.SEVERITY) {
            return practiceResultRepository.findByAboutUserAndWorkspaceSeverityFirst(
                    developerId,
                    workspaceId,
                    query.practiceSlug(),
                    query.groupSlug(),
                    query.outcome(),
                    hasArtifactKinds,
                    artifactKinds,
                    query.artifactKind(),
                    query.artifactId(),
                    hasSeverities,
                    severities,
                    query.displayableOnly(),
                    query.mostSevereFirst() ? 1 : -1,
                    pageable);
        }
        return practiceResultRepository.findByAboutUserAndWorkspace(
                developerId,
                workspaceId,
                query.practiceSlug(),
                query.groupSlug(),
                query.outcome(),
                hasArtifactKinds,
                artifactKinds,
                query.artifactKind(),
                query.artifactId(),
                hasSeverities,
                severities,
                query.displayableOnly(),
                pageable);
    }

    /** Per-practice result counts for the current user in a workspace. */
    @Transactional(readOnly = true)
    public List<DeveloperPracticeSummaryProjection> getSummary(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        return practiceResultRepository.findSummaryByDeveloperAndWorkspace(
                currentUser.get().getId(), workspaceId);
    }

    /**
     * The lanes whose feedback this read model means: the ones that speak about the one result they are
     * bound to. {@code IN_APP} feedback is excluded because it is a message about a way of working across several
     * pieces of work — it binds every problem behind it as evidence, so it would answer "what did you tell
     * me about this result" with a paragraph that is explicitly not about it. Named here rather than
     * defaulted in the query so a fourth lane has to be admitted deliberately.
     */
    private static final List<String> FEEDBACK_CHANNELS =
            List.of(FeedbackChannel.IN_CONTEXT.name(), FeedbackChannel.IN_CHAT.name());

    /** Missing and unowned results both raise not-found to avoid disclosing their existence. */
    @Transactional(readOnly = true)
    public PracticeResultDetailDTO getPracticeResultDetail(Long workspaceId, UUID practiceResultId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            throw new EntityNotFoundException("PracticeResult", practiceResultId.toString());
        }
        return detail(workspaceId, currentUser.get().getId(), practiceResultId);
    }

    private PracticeResultDetailDTO detail(Long workspaceId, Long developerId, UUID practiceResultId) {
        PracticeResult practiceResult = practiceResultRepository
                .findByIdAndDeveloperAndWorkspace(practiceResultId, developerId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("PracticeResult", practiceResultId.toString()));
        Set<UUID> evidencePermitted = evidenceAuthorization.permitsAll(
                workspaceId, List.of(practiceResult), SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        List<UUID> jobIds = List.of(practiceResult.getAgentJobId());
        Map<UUID, ReviewRunLookup.Target> targets = reviewRunLookup.findTargets(workspaceId, jobIds);
        Map<UUID, ReviewRunNarrative> narratives = reviewRunNarrativeLookup.findByJobIds(workspaceId, jobIds);
        return toDetails(workspaceId, developerId, List.of(practiceResult), evidencePermitted, targets, narratives)
                .getFirst();
    }

    /**
     * The detail read model of each result, in the given order, from one batched query per collaborator:
     * the newest piece of feedback that said something about each result to this developer (ADR 0021: advice
     * lives on the delivered {@code Feedback}, not the immutable result), which carries both the text the
     * developer reads and — when it delivered — the handle they answer it with and the links to the comments that
     * carry it on the work, so none of them can come from a different piece of feedback. A caller hands in results it has already loaded and gated, inside the
     * transaction their lazy associations are read in: evidence is included only for the ids in
     * {@code evidencePermitted}, which is {@link EvidenceAuthorization}'s answer for the delivery purpose, and the
     * artifact link comes from the run target of the result's job, absent when the target is no longer
     * resolvable.
     *
     * <p>Takes the workspace even though result ids alone identify the rows: what it loads is feedback,
     * and feedback is tenant-scoped whatever the result is.
     */
    public List<PracticeResultDetailDTO> toDetails(
            Long workspaceId,
            Long developerId,
            List<PracticeResult> practiceResults,
            Set<UUID> evidencePermitted,
            Map<UUID, ReviewRunLookup.Target> targets,
            Map<UUID, ReviewRunNarrative> narratives) {
        if (practiceResults.isEmpty()) {
            return List.of();
        }
        List<UUID> practiceResultIds =
                practiceResults.stream().map(PracticeResult::getId).toList();
        Map<UUID, ResultFeedback> feedback = feedbackByResult(workspaceId, developerId, practiceResultIds);
        Map<UUID, List<String>> commentUrls = commentUrlsByFeedback(workspaceId, developerId, feedback.values());
        Map<UUID, PracticeResultInvalidation> invalidations =
                invalidationRepository.findActiveFor(workspaceId, practiceResultIds).stream()
                        .collect(
                                Collectors.toMap(PracticeResultInvalidation::getPracticeResultId, Function.identity()));
        Map<UUID, List<Observation>> problems =
                observationRepository.findByPracticeResultIds(workspaceId, practiceResultIds).stream()
                        .collect(Collectors.groupingBy(Observation::getPracticeResultId));
        return practiceResults.stream()
                .map(practiceResult -> {
                    ReviewRunLookup.Target target = targets.get(practiceResult.getAgentJobId());
                    ReviewRunNarrative narrative = narratives.get(practiceResult.getAgentJobId());
                    ResultFeedback selected = feedback.get(practiceResult.getId());
                    UUID deliveredId = selected == null ? null : selected.getFeedbackId();
                    return PracticeResultDetailDTO.from(
                            practiceResult,
                            selected,
                            deliveredId == null ? null : commentUrls.get(deliveredId),
                            narrative == null ? null : narrative.nextStepFor(practiceResult.getId()),
                            target == null ? null : target.url(),
                            evidencePermitted.contains(practiceResult.getId()),
                            invalidations.get(practiceResult.getId()),
                            problems.getOrDefault(practiceResult.getId(), List.of()));
                })
                .toList();
    }

    private Map<UUID, ResultFeedback> feedbackByResult(
            Long workspaceId, Long recipientUserId, Collection<UUID> practiceResultIds) {
        return feedbackEvidenceRepository
                .findLatestFeedbackByResultIds(workspaceId, recipientUserId, practiceResultIds, FEEDBACK_CHANNELS)
                .stream()
                .collect(Collectors.toMap(ResultFeedback::getPracticeResultId, Function.identity()));
    }

    /**
     * The comment links of the feedback already selected per result, looked up only for the delivered ones:
     * a link is never borrowed from other feedback bound to the same result.
     */
    private Map<UUID, List<String>> commentUrlsByFeedback(
            Long workspaceId, Long recipientUserId, Collection<ResultFeedback> selected) {
        Set<UUID> deliveredIds = selected.stream()
                .map(ResultFeedback::getFeedbackId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (deliveredIds.isEmpty()) {
            return Map.of();
        }
        return feedbackPlacementRepository.findDeliveredCommentUrls(workspaceId, recipientUserId, deliveredIds).stream()
                .collect(Collectors.groupingBy(
                        PostedCommentUrl::getFeedbackId,
                        Collectors.collectingAndThen(
                                Collectors.mapping(PostedCommentUrl::getUrl, Collectors.toList()),
                                urls -> urls.stream().distinct().toList())));
    }
}
