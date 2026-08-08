package de.tum.cit.aet.hephaestus.agent.context.providers;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceCollectionException;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceContribution;
import de.tum.cit.aet.hephaestus.agent.context.EvidencePlan;
import de.tum.cit.aet.hephaestus.agent.context.EvidenceSource;
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
 * Stages what earlier reviews recorded about this person, and what they were already told.
 *
 * <p>A review occasioned by one event sees one event. That makes every such review a partial practice
 * review, and the partiality is not fixable by reading the event harder — the thing that is missing is
 * the other events. This source is how a run sees the ones before it: the observations earlier runs
 * filed about the same person, carrying the recurrence key that says which of them are about the same
 * underlying problem, and the feedback that was already delivered, so a run can tell "this is new" from
 * "we have said this twice already and they have not acted".
 *
 * <p>Both files are written on every run, including the run where a person has no history at all. An
 * empty {@code observations.json} is the sandbox saying the record was read and held nothing; a missing
 * one would be indistinguishable from a source that was never staged, and the difference between those
 * two is the whole basis on which a review is allowed to reason about what it did <em>not</em> find.
 *
 * <p>Never reported COMPLETE. The window below is a bounded read of a record that keeps growing, so it
 * can support "this recurred" and can never support "this has never happened before".
 *
 * <p><b>Scope note — the reaction firewall stands.</b> What is staged is what earlier reviews
 * <em>observed</em> and what was <em>delivered</em>. What a contributor did about it — applied,
 * dismissed, disputed — is deliberately not staged, and this class does not reach the reaction package
 * (ADR 0021 F-9, pinned by {@code DetectionReactionFirewallTest}). Detection that knew a finding had
 * been disputed would have a reason to stop reporting a true positive, and the accuracy measurement the
 * research rests on would no longer mean anything.
 *
 * <p>The residual risk this source does carry is anchoring: a model shown an earlier observation could
 * echo it instead of looking. That is bounded by the same rule as everything else — an observation about
 * this artifact must quote this artifact, and the history can be cited for whether something recurs but
 * never for whether something is present in the work under review. The prompt says so, and the delivery
 * boundary enforces the quote.
 */
@Component
@Order(500)
public class ReviewHistoryContentSource implements EvidenceSource {

    private static final Logger log = LoggerFactory.getLogger(ReviewHistoryContentSource.class);

    /**
     * The kinds this collector answers for.
     *
     * <p>Aliases rather than definitions: the plan owns them, because the plan is what decides they are
     * staged for every review rather than on request. They are restated here as static
     * {@link SourceKind} fields because that is how the catalog-coverage and completeness-reporting
     * architecture rules discover which collector covers which catalog entry — a collector that names its
     * kinds only through a method is invisible to both, and the catalog entry would read as uncollected.
     */
    static final SourceKind OBSERVATION_HISTORY = EvidencePlan.OBSERVATION_HISTORY;

    static final SourceKind FEEDBACK_HISTORY = EvidencePlan.FEEDBACK_HISTORY;

    static final String OBSERVATIONS_FILE = SandboxLayout.HISTORY_PREFIX + "observations.json";
    static final String FEEDBACK_FILE = SandboxLayout.HISTORY_PREFIX + "feedback.json";

    private static final int LOOKBACK_DAYS = 90;
    private static final int MAX_OBSERVATIONS = 50;
    private static final int MAX_FEEDBACK = 30;

    private final ObservationRepository observationRepository;
    private final FeedbackRepository feedbackRepository;
    private final ObservationVisibilityPolicy visibilityPolicy;
    private final PullRequestRepository pullRequestRepository;
    private final IssueRepository issueRepository;
    private final ObjectMapper objectMapper;

    public ReviewHistoryContentSource(
        ObservationRepository observationRepository,
        FeedbackRepository feedbackRepository,
        ObservationVisibilityPolicy visibilityPolicy,
        PullRequestRepository pullRequestRepository,
        IssueRepository issueRepository,
        ObjectMapper objectMapper
    ) {
        this.observationRepository = observationRepository;
        this.feedbackRepository = feedbackRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.pullRequestRepository = pullRequestRepository;
        this.issueRepository = issueRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Set<SourceKind> sourceKinds() {
        return EvidencePlan.WORKSPACE_CONTEXT_SOURCES;
    }

    @Override
    public SourceKind sourceKindFor(String path) {
        return FEEDBACK_FILE.equals(path) ? EvidencePlan.FEEDBACK_HISTORY : EvidencePlan.OBSERVATION_HISTORY;
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
            // Not an empty history: nothing was read, because there is no person to read it for. Saying
            // "empty" here would license a review to conclude a first-ever occurrence from a lookup that
            // never happened.
            return absent(selectedKinds, new SourceCaptureState.Unavailable(SourceAbsenceReason.NOT_FOUND));
        }
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<Observation> observations = observationRepository
            .findRecentByDeveloperAndWorkspace(subjectUserId, workspaceId, since, PageRequest.of(0, MAX_OBSERVATIONS))
            .stream()
            .filter(o -> visibilityPolicy.permits(workspaceId, o, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW))
            .toList();
        List<Feedback> delivered = feedbackRepository.findRecentDeliveredForRecipient(
            workspaceId,
            subjectUserId,
            since,
            PageRequest.of(0, MAX_FEEDBACK)
        );

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(OBSERVATIONS_FILE, serialize(observationsPayload(observations, since), OBSERVATIONS_FILE));
        files.put(FEEDBACK_FILE, serialize(feedbackPayload(delivered, since), FEEDBACK_FILE));
        log.debug(
            "Review history: workspaceId={}, subjectUserId={}, observations={}, feedback={}",
            workspaceId,
            subjectUserId,
            observations.size(),
            delivered.size()
        );
        return new EvidenceContribution(
            files,
            // A window over a growing record. It can show that something recurred; it can never show
            // that something never happened, so COMPLETE is not a state this source may report.
            Map.of(
                EvidencePlan.OBSERVATION_HISTORY,
                SourceCompleteness.PARTIAL,
                EvidencePlan.FEEDBACK_HISTORY,
                SourceCompleteness.PARTIAL
            ),
            Map.of(),
            Map.of(),
            Map.of(),
            // Reported rather than inferred from the staged file list: both files are always written, so
            // "there is a file" would answer NON_EMPTY for a person with no history at all.
            Map.of(
                EvidencePlan.OBSERVATION_HISTORY,
                observations.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY,
                EvidencePlan.FEEDBACK_HISTORY,
                delivered.isEmpty() ? SourceContentState.EMPTY : SourceContentState.NON_EMPTY
            )
        );
    }

    private ObjectNode observationsPayload(List<Observation> observations, Instant since) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("window", "observations recorded since " + since + ", newest first");
        root.put("count", observations.size());
        // Stated in the file rather than left to the manifest, because the file is what the model reads
        // first and this is the sentence that stops it reading "no rows" as "never happened".
        root.put(
            "completeness",
            "PARTIAL: the most recent " +
                MAX_OBSERVATIONS +
                " observations within the window. An observation absent here may still have been recorded."
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
            node.put("artifactKind", o.getArtifactKind() == null ? null : o.getArtifactKind().value());
            node.put("artifactId", o.getArtifactId());
            node.put("observedAt", o.getObservedAt() == null ? null : o.getObservedAt().toString());
            node.put("reasoning", StudentTextSanitizer.sanitize(o.getReasoning()));
        }
        return root;
    }

    private ObjectNode feedbackPayload(List<Feedback> delivered, Instant since) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("window", "feedback delivered since " + since + ", newest first");
        root.put("count", delivered.size());
        root.put(
            "completeness",
            "PARTIAL: the most recent " +
                MAX_FEEDBACK +
                " delivered items within the window. Feedback absent here may still have been delivered."
        );
        ArrayNode items = root.putArray("feedback");
        for (Feedback f : delivered) {
            ObjectNode node = items.addObject();
            node.put("channel", f.getChannel() == null ? null : f.getChannel().name());
            node.put("artifactKind", f.getArtifactKind() == null ? null : f.getArtifactKind().value());
            node.put("artifactId", f.getArtifactId());
            node.put("deliveredAt", f.getDeliveredAt() == null ? null : f.getDeliveredAt().toString());
            node.put("body", StudentTextSanitizer.sanitize(f.getBody()));
        }
        return root;
    }

    /**
     * The person the review is about.
     *
     * <p>The author for work with an author, and the explicitly carried subject for the artifacts that
     * have none to read — the same resolution delivery performs, because history staged for one person
     * and observations filed against another would let a review cite somebody else's record.
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
