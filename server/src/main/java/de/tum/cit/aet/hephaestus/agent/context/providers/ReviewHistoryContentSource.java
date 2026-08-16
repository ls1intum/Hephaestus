package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceContribution;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
import de.tum.cit.aet.hephaestus.agent.context.StagedArtifactNames;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.StudentTextSanitizer;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stages what earlier reviews recorded about this person: the observations earlier runs filed against
 * them, carrying the recurrence key that says which are about the same underlying problem, and the
 * feedback already delivered, so a run can tell "this is new" from "we said this before".
 *
 * <p>Both files are written even when a person has no history: an empty {@code observations.json} says
 * the record was read and held nothing, distinct from a source that was never staged.
 *
 * <p>Never reported COMPLETE — the window is a bounded read of a record that keeps growing, so it can
 * show that something recurred but never that it has never happened before.
 *
 * <p>Only what earlier reviews observed and delivered is staged, not how a contributor reacted to it
 * (applied, dismissed, disputed): this class does not reach the reaction package (ADR 0021 F-9, pinned by
 * {@code DetectionReactionFirewallTest}). Detection that knew a finding had been disputed would have a
 * reason to stop reporting a true positive.
 *
 * <p>History can anchor a model into echoing an earlier observation instead of looking; the delivery
 * boundary bounds that by requiring every observation to quote the artifact under review, so history may
 * be cited for recurrence but never for what is present in the current work.
 */
@Component
@Order(500)
public class ReviewHistoryContentSource implements EvidenceSource {

    private static final Logger log = LoggerFactory.getLogger(ReviewHistoryContentSource.class);

    static final SourceKind OBSERVATION_HISTORY = new SourceKind("hephaestus.observation-history");
    static final SourceKind FEEDBACK_HISTORY = new SourceKind("hephaestus.feedback-history");

    static final String OBSERVATIONS_FILE = SandboxLayout.HISTORY_PREFIX + "observations.json";
    static final String FEEDBACK_FILE = SandboxLayout.HISTORY_PREFIX + "feedback.json";

    /** Exposure bounds, not cost bounds — they cap how much of a contributor's record can anchor a model. */
    private static final int LOOKBACK_DAYS = 90;

    private static final int MAX_OBSERVATIONS = 50;
    private static final int MAX_FEEDBACK = 30;

    private final ObservationRepository observationRepository;
    private final FeedbackRepository feedbackRepository;
    private final ObservationVisibilityPolicy visibilityPolicy;
    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final StagedArtifactNames artifactNames;
    private final ObjectMapper objectMapper;

    public ReviewHistoryContentSource(
        ObservationRepository observationRepository,
        FeedbackRepository feedbackRepository,
        ObservationVisibilityPolicy visibilityPolicy,
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        StagedArtifactNames artifactNames,
        ObjectMapper objectMapper
    ) {
        this.observationRepository = observationRepository;
        this.feedbackRepository = feedbackRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.artifactNames = artifactNames;
        this.objectMapper = objectMapper;
    }

    @Override
    public Set<SourceKind> sourceKinds() {
        return Set.of(OBSERVATION_HISTORY, FEEDBACK_HISTORY);
    }

    @Override
    public SourceKind sourceKindFor(String path) {
        return FEEDBACK_FILE.equals(path) ? FEEDBACK_HISTORY : OBSERVATION_HISTORY;
    }

    /** Owns {@code inputs/history/}, not the per-event {@code inputs/context/} namespace. */
    @Override
    public boolean ownsPath(String path) {
        return path.startsWith(SandboxLayout.HISTORY_PREFIX);
    }

    /** Every review of an artifact. History is about the person, so what they produced does not change it. */
    @Override
    public boolean supports(ContextRequest request) {
        return reviewJob(request) != null;
    }

    @Override
    public boolean required() {
        return false;
    }

    @Override
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        files.putAll(capture(request, sourceKinds()).files());
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceContribution capture(ContextRequest request, Set<SourceKind> selectedKinds) {
        AgentJob job = reviewJob(request);
        if (job == null || job.getWorkspace() == null) {
            return new EvidenceContribution(Map.of(), Map.of());
        }
        long workspaceId = job.getWorkspace().getId();
        Long subjectUserId = resolveSubject(request, job);
        if (subjectUserId == null) {
            // Unavailable, not empty: there is no person to read a history for, so nothing was read.
            return absent(selectedKinds, new SourceCaptureState.Unavailable(SourceAbsenceReason.NOT_FOUND));
        }
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

        // Each kind is queried and reported only when selected — the builder rejects completeness or
        // content-state facts about a source that wasn't asked for.
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<SourceKind, SourceCompleteness> completeness = new LinkedHashMap<>();
        Map<SourceKind, SourceContentState> contentStates = new LinkedHashMap<>();
        int observationCount = -1;
        int feedbackCount = -1;

        if (selectedKinds.contains(OBSERVATION_HISTORY)) {
            List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
                subjectUserId,
                workspaceId,
                since,
                PageRequest.of(0, MAX_OBSERVATIONS)
            );
            Set<UUID> visible = visibilityPolicy.permitsAll(
                workspaceId,
                recent,
                SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW
            );
            List<Observation> observations = recent
                .stream()
                .filter(o -> visible.contains(o.getId()))
                .toList();
            observationCount = observations.size();
            files.put(
                OBSERVATIONS_FILE,
                serialize(observationsPayload(workspaceId, observations, since), OBSERVATIONS_FILE)
            );
            completeness.put(OBSERVATION_HISTORY, SourceCompleteness.PARTIAL);
            // Reported explicitly rather than inferred from file presence: the file is always written,
            // so "there is a file" would wrongly answer NON_EMPTY for a person with no history.
            contentStates.put(
                OBSERVATION_HISTORY,
                observations.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY
            );
        }

        if (selectedKinds.contains(FEEDBACK_HISTORY)) {
            List<Feedback> delivered = feedbackRepository.findRecentDeliveredForRecipient(
                workspaceId,
                subjectUserId,
                since,
                PageRequest.of(0, MAX_FEEDBACK)
            );
            feedbackCount = delivered.size();
            files.put(FEEDBACK_FILE, serialize(feedbackPayload(workspaceId, delivered, since), FEEDBACK_FILE));
            completeness.put(FEEDBACK_HISTORY, SourceCompleteness.PARTIAL);
            contentStates.put(
                FEEDBACK_HISTORY,
                delivered.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY
            );
        }

        log.debug(
            "Review history: workspaceId={}, subjectUserId={}, observations={}, feedback={}",
            workspaceId,
            subjectUserId,
            observationCount,
            feedbackCount
        );
        return new EvidenceContribution(
            files,
            Map.copyOf(completeness),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.copyOf(contentStates)
        );
    }

    private ObjectNode observationsPayload(long workspaceId, List<Observation> observations, Instant since) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("window", "observations recorded since " + since + ", newest first");
        root.put("count", observations.size());
        // Stated in the file itself, which the model reads directly, so an empty list can't be read as
        // "never happened".
        root.put(
            "completeness",
            "PARTIAL: the most recent " +
                MAX_OBSERVATIONS +
                " observations within the window. An observation absent here may still have been recorded."
        );
        StagedArtifactNames.Resolved names = artifactNames.resolve(
            workspaceId,
            observations
                .stream()
                .map(o -> new StagedArtifactNames.Reference(o.getArtifactKind(), o.getArtifactId()))
                .toList()
        );
        ArrayNode items = root.putArray("observations");
        for (Observation o : observations) {
            ObjectNode node = items.addObject();
            node.put("practiceSlug", o.getPractice() == null ? null : o.getPractice().getSlug());
            node.put("recurrenceKey", o.getRecurrenceKey());
            node.put("title", o.getTitle());
            node.put("presence", o.getPresence() == null ? null : o.getPresence().name());
            node.put("assessment", o.getAssessment() == null ? null : o.getAssessment().name());
            node.put("severity", o.getSeverity() == null ? null : o.getSeverity().name());
            names.stageInto(node, o.getArtifactKind(), o.getArtifactId());
            node.put("observedAt", o.getObservedAt() == null ? null : o.getObservedAt().toString());
            node.put("reasoning", StudentTextSanitizer.sanitize(o.getReasoning()));
        }
        return root;
    }

    private ObjectNode feedbackPayload(long workspaceId, List<Feedback> delivered, Instant since) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("window", "feedback delivered since " + since + ", newest first");
        root.put("count", delivered.size());
        root.put(
            "completeness",
            "PARTIAL: the most recent " +
                MAX_FEEDBACK +
                " delivered items within the window. Feedback absent here may still have been delivered."
        );
        StagedArtifactNames.Resolved names = artifactNames.resolve(
            workspaceId,
            delivered
                .stream()
                .map(f -> new StagedArtifactNames.Reference(f.getArtifactKind(), f.getArtifactId()))
                .toList()
        );
        ArrayNode items = root.putArray("feedback");
        for (Feedback f : delivered) {
            ObjectNode node = items.addObject();
            node.put("channel", f.getChannel() == null ? null : f.getChannel().name());
            names.stageInto(node, f.getArtifactKind(), f.getArtifactId());
            node.put("deliveredAt", f.getDeliveredAt() == null ? null : f.getDeliveredAt().toString());
            node.put("body", StudentTextSanitizer.sanitize(f.getBody()));
        }
        return root;
    }

    /**
     * The person the review is about: the author for work with one, the explicitly carried subject
     * otherwise — mirrors the resolution delivery performs, so history can't be staged for one person
     * while observations are filed against another.
     */
    private Long resolveSubject(ContextRequest request, AgentJob job) {
        return switch (request) {
            case ContextRequest.PracticeReviewRequest ignored -> authorOfPullRequest(job);
            case ContextRequest.IssueReviewRequest ignored -> authorOfIssue(job);
            case ContextRequest.DocumentReviewRequest ignored -> metadataSubject(job);
            case ContextRequest.ConversationReviewRequest ignored -> metadataSubject(job);
            case ContextRequest.MentorChatRequest ignored -> null;
        };
    }

    private Long authorOfPullRequest(AgentJob job) {
        Long id = metadataLong(job, "pull_request_id");
        if (id == null) return null;
        return pullRequestRepository
            .findByIdWithAuthorAndRepository(id)
            .map(PullRequest::getAuthor)
            .map(author -> author.getId())
            .orElse(null);
    }

    private Long authorOfIssue(AgentJob job) {
        Long id = metadataLong(job, "issue_id");
        if (id == null) return null;
        return issueRepository
            .findByIdWithAuthorAndRepository(id)
            .map(Issue::getAuthor)
            .map(author -> author.getId())
            .orElse(null);
    }

    private Long metadataSubject(AgentJob job) {
        return metadataLong(job, "about_user_id");
    }

    private static Long metadataLong(AgentJob job, String field) {
        var metadata = job.getMetadata();
        if (metadata == null || metadata.isNull()) return null;
        var node = metadata.get(field);
        return node == null || node.isNull() || !node.isNumber() ? null : node.asLong();
    }

    private EvidenceContribution absent(Set<SourceKind> selectedKinds, SourceCaptureState state) {
        Map<SourceKind, SourceCaptureState> overrides = new LinkedHashMap<>();
        for (SourceKind kind : sourceKinds()) {
            if (selectedKinds.contains(kind)) overrides.put(kind, state);
        }
        return new EvidenceContribution(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), overrides);
    }

    private byte[] serialize(ObjectNode payload, String path) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (RuntimeException e) {
            throw new EvidenceCollectionException("Failed to serialize review history: " + path, e);
        }
    }

    private static AgentJob reviewJob(ContextRequest request) {
        return switch (request) {
            case ContextRequest.PracticeReviewRequest r -> r.job();
            case ContextRequest.IssueReviewRequest r -> r.job();
            case ContextRequest.DocumentReviewRequest r -> r.job();
            case ContextRequest.ConversationReviewRequest r -> r.job();
            case ContextRequest.MentorChatRequest ignored -> null;
        };
    }
}
