package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.JobFolderIndex;
import de.tum.cit.aet.hephaestus.agent.context.providers.LinkedWorkItemContentSource;
import de.tum.cit.aet.hephaestus.agent.handler.LinkedRepairFollowupRequest;
import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignal;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignalRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalLedgerProperties;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalState;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** A current capture may finish an authorized event obligation; discovery alone never authorizes a review. */
@Component
@RequiredArgsConstructor
public class LinkedRepairFollowups {
    private static final String REASON = "linked_issue_capture_stale";

    private final AgentJobRepository jobs;
    private final ArtifactSignalRepository signals;
    private final PullRequestRepository pullRequests;
    private final IssueRepository issues;
    private final SignalLedgerProperties properties;
    private final ArtifactSourceCatalogRegistry catalogs;
    private final ObjectMapper mapper;

    enum Decision {
        PROCEED,
        REFUSED,
        OWNERSHIP_LOST
    }

    record Parent(UUID predecessorJobId, UUID rootJobId) {}

    record Admission(@Nullable Parent parent, @Nullable SignalStateReason refusal) {}

    @Transactional
    public Decision refuseStale(AgentJob attempted, @Nullable String workerId) {
        JsonNode facts = attempted.getMetadata();
        if (attempted.getJobType() != AgentJobType.PULL_REQUEST_REVIEW
                || attempted.getPracticeTriggerMode() != TriggerMode.AUTO
                || facts == null
                || !ScmSignals.PULL_REQUEST_LINKED_ISSUE_UPDATED
                        .value()
                        .equals(facts.path("signal").asString())) {
            return Decision.PROCEED;
        }
        if (ResultAdmissionService.isAdmitted(attempted)) return Decision.PROCEED;
        long artifactId = facts.path("pull_request_id").asLong(0);
        if (artifactId <= 0 || pullRequests.lockById(artifactId).isEmpty()) return Decision.PROCEED;
        AgentJob job = jobs.findByIdWithWorkspaceForUpdate(attempted.getId()).orElse(null);
        if (job == null
                || job.getStatus() != AgentJobStatus.RUNNING
                || job.getRetryCount() != attempted.getRetryCount()
                || !Objects.equals(job.getWorkerId(), workerId)
                || !job.getWorkspace().getId().equals(attempted.getWorkspace().getId())) return Decision.OWNERSHIP_LOST;
        if (ResultAdmissionService.isAdmitted(job)) return Decision.PROCEED;
        PullRequest pr = pullRequests.findByIdWithAllForGate(artifactId).orElse(null);
        if (pr == null || pr.getState() != Issue.State.MERGED || pr.getDeletedAt() != null) return Decision.PROCEED;
        for (long id : pullRequests.findClosingIssueIdsById(artifactId)) {
            if (issues.lockForReview(id).isEmpty()) return Decision.PROCEED;
        }
        var key = LinkedWorkItemContentSource.currentClosingMaterialKey(
                        job.getWorkspace().getId(), pr, pullRequests.findClosingIssuesById(artifactId))
                .orElse(null);
        if (key == null
                || key.revision()
                        .value()
                        .equals(facts.path("linked_issue_revision").asString())) {
            return Decision.PROCEED;
        }
        Instant now = Instant.now();
        AgentJob root = rootOf(job);
        if (root == null) return Decision.PROCEED;
        ArtifactSignal current = signals.lockByKey(key).orElse(null);
        if (current == null) return Decision.PROCEED;
        var output = mapper.createObjectNode()
                .put("outcome", ReviewRunOutcome.INSUFFICIENT_EVIDENCE.name())
                .put("reasonCode", REASON);
        // Each successor shares the original event's horizon; changing material never extends it.
        if (sameAuthorAndRepository(root, pr) && !expired(root, now) && vouchedCapture(job)) {
            if (signals.deferRecordedSyncFollowup(key, now) == 1) {
                var linkedFacts = mapper.createObjectNode();
                if (!(job.getMetadata() instanceof ObjectNode storedFacts)) {
                    throw new IllegalStateException("Linked repair metadata is not an object");
                }
                linkedFacts.setAll(storedFacts);
                linkedFacts.put(
                        LinkedRepairFollowupRequest.SUCCESSOR_REVISION,
                        key.revision().value());
                linkedFacts.put(
                        LinkedRepairFollowupRequest.ROOT_JOB, root.getId().toString());
                if (jobs.recordLinkedRepairSuccessor(
                                job.getId(), job.getWorkspace().getId(), workerId, job.getRetryCount(), linkedFacts)
                        != 1) {
                    throw new IllegalStateException("Linked repair attempt ownership changed");
                }
            }
        }
        // The attempt fence and its durable successor link commit with the deferred opportunity, or both roll back.
        if (jobs.transitionToStaleLinkedRepairRefused(job.getId(), workerId, job.getRetryCount(), now, output) != 1) {
            throw new IllegalStateException("Linked repair attempt ownership changed");
        }
        return Decision.REFUSED;
    }

    Admission admission(ArtifactSignal signal, PullRequest pr) {
        if (signal.getDiscoveredVia() != DiscoveredVia.SYNC
                || !ScmSignals.PULL_REQUEST_LINKED_ISSUE_UPDATED.value().equals(signal.getSignalName())) {
            return new Admission(null, null);
        }
        AgentJob predecessor = jobs.findLinkedRepairPredecessor(
                        signal.getWorkspace().getId(),
                        pr.getId(),
                        signal.getRevision(),
                        signal.getStateChangedAt().minus(properties.pendingLapseAfter()),
                        signal.getStateChangedAt())
                .orElse(null);
        if (predecessor == null) return new Admission(null, SignalStateReason.OUT_OF_REVIEW_SCOPE);
        JsonNode predecessorFacts = predecessor.getMetadata();
        UUID rootId =
                uuid(predecessorFacts == null ? null : predecessorFacts.path(LinkedRepairFollowupRequest.ROOT_JOB));
        AgentJob root = rootId == null
                ? null
                : jobs.findByIdAndWorkspaceId(rootId, signal.getWorkspace().getId())
                        .orElse(null);
        if (root == null || !eventRoot(root, pr.getId()) || !sameAuthorAndRepository(root, pr)) {
            return new Admission(null, SignalStateReason.OUT_OF_REVIEW_SCOPE);
        }
        if (expired(root, Instant.now())) {
            return new Admission(null, SignalStateReason.PENDING_DEADLINE_EXCEEDED);
        }
        return new Admission(new Parent(predecessor.getId(), root.getId()), null);
    }

    enum Advancement {
        DEFERRED,
        SETTLED,
        WAITING
    }

    @Transactional
    public Advancement advance(ArtifactSignal previous, Parent parent, SignalKey current) {
        ArtifactSignal next = signals.lockByKey(current).orElse(null);
        if (next == null
                || (next.getDiscoveredVia() != DiscoveredVia.SYNC && next.getDiscoveredVia() != DiscoveredVia.EVENT))
            return Advancement.WAITING;
        if (next.getState() == SignalState.TRIGGERED
                || next.getState() == SignalState.SUPPRESSED
                || next.getState() == SignalState.LAPSED) return Advancement.SETTLED;
        if (next.getState() == SignalState.DEFERRED || next.getState() == SignalState.PENDING) {
            return Advancement.SETTLED;
        }
        if (signals.deferRecordedSyncFollowup(current, Instant.now()) == 0) return Advancement.WAITING;
        if (jobs.advanceLinkedRepairSuccessor(
                        parent.predecessorJobId(),
                        previous.getWorkspace().getId(),
                        parent.rootJobId(),
                        previous.getRevision(),
                        current.revision().value())
                != 1) {
            throw new IllegalStateException("Linked repair predecessor ownership changed");
        }
        return Advancement.DEFERRED;
    }

    private @Nullable AgentJob rootOf(AgentJob job) {
        JsonNode facts = job.getMetadata();
        if (facts == null) return null;
        UUID rootId = uuid(facts.path(LinkedRepairFollowupRequest.ROOT_JOB));
        if (facts.has(LinkedRepairFollowupRequest.ROOT_JOB) && rootId == null) return null;
        AgentJob root = rootId == null
                ? job
                : jobs.findByIdAndWorkspaceId(rootId, job.getWorkspace().getId())
                        .orElse(null);
        return root != null && eventRoot(root, facts.path("pull_request_id").asLong(0)) ? root : null;
    }

    private boolean eventRoot(AgentJob root, long artifactId) {
        JsonNode facts = root.getMetadata();
        return root.getJobType() == AgentJobType.PULL_REQUEST_REVIEW
                && root.getPracticeTriggerMode() == TriggerMode.AUTO
                && !ResultAdmissionService.isAdmitted(root)
                && facts != null
                && "LIVE"
                        .equals(facts.path(ReviewOutputService.ORIGIN_METADATA_KEY)
                                .asString())
                && ScmSignals.PULL_REQUEST_LINKED_ISSUE_UPDATED
                        .value()
                        .equals(facts.path("signal").asString())
                && facts.path("pull_request_id").asLong(0) == artifactId
                && signals.findTriggeredLinkedRepairEvent(
                                root.getWorkspace().getId(),
                                artifactId,
                                root.getId(),
                                facts.path("linked_issue_revision").asString())
                        .isPresent();
    }

    private boolean expired(AgentJob root, Instant now) {
        JsonNode facts = root.getMetadata();
        if (facts == null) return true;
        return signals.findTriggeredLinkedRepairEvent(
                        root.getWorkspace().getId(),
                        facts.path("pull_request_id").asLong(0),
                        root.getId(),
                        facts.path("linked_issue_revision").asString())
                .map(event -> !event.getOccurredAt()
                        .plus(properties.pendingLapseAfter())
                        .isAfter(now))
                .orElse(true);
    }

    private static boolean sameAuthorAndRepository(AgentJob root, PullRequest pr) {
        JsonNode facts = root.getMetadata();
        return facts != null
                && pr.getAuthor() != null
                && pr.getRepository() != null
                && facts.path("author_id").isIntegralNumber()
                && facts.path("repository_id").isIntegralNumber()
                && facts.path("author_id").asLong() == pr.getAuthor().getId()
                && facts.path("repository_id").asLong() == pr.getRepository().getId();
    }

    private boolean vouchedCapture(AgentJob job) {
        JsonNode snapshot = job.getEvidenceSnapshot();
        if (snapshot == null) return false;
        try {
            JobFolderIndex manifest = mapper.treeToValue(snapshot.path("manifest"), JobFolderIndex.class);
            return manifest != null
                    && catalogs.isSourceUsePermitted(
                            manifest.contractVersion(),
                            LinkedWorkItemContentSource.KIND,
                            SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW)
                    && manifest.sources().stream()
                            .anyMatch(source -> LinkedWorkItemContentSource.KIND.equals(source.kind())
                                    && source.state() instanceof SourceCaptureState.Available);
        } catch (JacksonException | IllegalArgumentException invalid) {
            return false;
        }
    }

    private static @Nullable UUID uuid(@Nullable JsonNode node) {
        if (node == null || !node.isString()) return null;
        try {
            return UUID.fromString(node.asString());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
