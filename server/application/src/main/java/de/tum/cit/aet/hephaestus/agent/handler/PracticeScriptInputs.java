package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.ReviewedWork;
import de.tum.cit.aet.hephaestus.agent.context.providers.DocumentContentSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationSourceLiveness;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobPreparationException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.scm.ReviewTargetQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.ReviewWhen;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.observation.LatestRun;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What a practice script reads about its review beside the work: its cause, and its practice's previous claim on
 * this work. Both are staged beside the script, and the practice script runner validates them against
 * {@code docker/agents/practice-scripts/lib/contract.ts} before the script starts.
 *
 * <p>The previous claim is the one this review would read as current: the latest run of the claim, then currentness,
 * invalidation, evidence authorization and a conversation's consent, as {@code ReviewHistoryContentSource} reads
 * history. It carries no model text and no delivery state, so a script cannot pass a model's words to the review or
 * read withheld feedback as missing evidence.
 */
final class PracticeScriptInputs {

    static final String MOMENT = "moment";
    static final String RECHECK = "recheck";
    static final String REQUESTED = "requested";

    private final JsonMapper mapper;
    private final ObservationRepository observations;
    private final ObservationVisibilityPolicy visibility;
    private final ConversationSourceLiveness conversations;
    private final AgentJobRepository jobs;
    private final ReviewTargetQuery reviewTargets;
    private final UserRepository users;

    PracticeScriptInputs(
            JsonMapper mapper,
            ObservationRepository observations,
            ObservationVisibilityPolicy visibility,
            ConversationSourceLiveness conversations,
            AgentJobRepository jobs,
            ReviewTargetQuery reviewTargets,
            UserRepository users) {
        this.mapper = mapper;
        this.observations = observations;
        this.visibility = visibility;
        this.conversations = conversations;
        this.jobs = jobs;
        this.reviewTargets = reviewTargets;
        this.users = users;
    }

    /** The cause and the previous claim of each practice, by staged path, beside its script. */
    Map<String, byte[]> stage(AgentJob job, ArtifactKind kind, List<Practice> practices) {
        if (practices.isEmpty()) {
            return Map.of();
        }
        if (job.getWorkspace() == null) {
            throw new JobPreparationException("Job has no workspace: jobId=" + job.getId());
        }
        JsonNode metadata = job.getMetadata() == null ? mapper.createObjectNode() : job.getMetadata();
        Map<String, Observation> previous = previousClaims(job.getWorkspace().getId(), kind, metadata, practices);
        Map<UUID, Optional<AgentJob>> runs = new HashMap<>();
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (Practice practice : practices) {
            String base = SandboxLayout.PRACTICE_SCRIPT_PREFIX + "practices/" + practice.getSlug();
            Observation claim = previous.get(practice.getSlug());
            JsonNode staged = claim == null
                    ? NullNode.getInstance()
                    : previous(job.getWorkspace().getId(), kind, practice, claim, runs);
            files.put(base + SandboxLayout.PRACTICE_SCRIPT_CAUSE_SUFFIX, write(cause(job, kind, metadata, practice)));
            files.put(base + SandboxLayout.PRACTICE_SCRIPT_PREVIOUS_SUFFIX, write(staged));
        }
        return files;
    }

    private ObjectNode cause(AgentJob job, ArtifactKind kind, JsonNode metadata, Practice practice) {
        Instant admittedAt = job.getCreatedAt();
        if (admittedAt == null) {
            throw new JobPreparationException("Job has no admission time: jobId=" + job.getId());
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("kind", kind.value());
        root.putObject("subject").put("role", practice.getSubject().name());
        ObjectNode cause = root.putObject("cause");
        SignalName signal = PracticeCatalogInjector.signalOf(job);
        String type = causeOf(job, signal, practice);
        cause.put("type", type);
        if (!REQUESTED.equals(type) && signal != null) {
            cause.put("signal", signal.value());
        }
        cause.put("origin", ReviewOutputService.originOf(metadata).name());
        ObjectNode state = cause.putObject("state");
        PracticeCatalogInjector.reviewStateOf(job).forEach(state::put);
        cause.put("admittedAt", admittedAt.toString());
        if (MOMENT.equals(type) && ScmSignals.PULL_REQUEST_REVIEWED.equals(signal)) {
            review(metadata).ifPresent(review -> cause.set("review", review));
        }
        return root;
    }

    /**
     * Why the practice is in this review. A request by hand reviews each practice of the kind in any state; a
     * practice that admission rechecked beside the moment, and that is not bound to the moment, is a recheck; every
     * other practice is bound to the moment and its review conditions matched.
     */
    static String causeOf(AgentJob job, @Nullable SignalName signal, Practice practice) {
        if (job.getPracticeTriggerMode() == TriggerMode.MANUAL) {
            return REQUESTED;
        }
        boolean bound = signal != null
                && practice.getSignals().contains(signal)
                && ReviewWhen.matches(practice.getReviewWhen(), PracticeCatalogInjector.reviewStateOf(job));
        return PracticeCatalogInjector.recheckedOf(job).contains(practice.getSlug()) && !bound ? RECHECK : MOMENT;
    }

    /** The submitted review a reviewer practice is about, with the reviewer's login. */
    private Optional<ObjectNode> review(JsonNode metadata) {
        JsonNode reviewId = metadata.path("review_id");
        JsonNode reviewer = metadata.path("about_user_id");
        if (!reviewId.isIntegralNumber() || !reviewer.isIntegralNumber()) {
            return Optional.empty();
        }
        return users.findById(reviewer.asLong()).map(User::getLogin).map(login -> {
            ObjectNode review = mapper.createObjectNode();
            review.put("id", reviewId.asLong());
            review.put("reviewer", login);
            return review;
        });
    }

    /** The current claim of each practice about the person this review is about, on this work, by slug. */
    private Map<String, Observation> previousClaims(
            long workspaceId, ArtifactKind kind, JsonNode metadata, List<Practice> practices) {
        Long work = workOf(kind, metadata);
        Long person = work == null ? null : personOf(kind, metadata, work);
        if (work == null || person == null) {
            return Map.of();
        }
        Set<String> slugs = practices.stream().map(Practice::getSlug).collect(Collectors.toSet());
        List<Observation> latest =
                LatestRun.perClaim(observations.findStandingForWork(workspaceId, kind, work, person)).stream()
                        .filter(observation ->
                                slugs.contains(observation.getPractice().getSlug()))
                        .toList();
        if (latest.isEmpty()) {
            return Map.of();
        }
        Set<UUID> permitted = visibility.permitsAll(workspaceId, latest, SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW);
        // A conversation's claim stands only while its channel consents.
        boolean consented = !ArtifactKinds.CONVERSATION_THREAD.equals(kind)
                || conversations.activeThreadIds(workspaceId, List.of(work)).contains(work);
        return latest.stream()
                .filter(observation -> consented && permitted.contains(observation.getId()))
                .collect(Collectors.toMap(
                        observation -> observation.getPractice().getSlug(),
                        observation -> observation,
                        // One observation per practice and review; should a run hold more, the newest speaks.
                        (a, b) -> NEWEST.compare(a, b) >= 0 ? a : b));
    }

    private static final Comparator<Observation> NEWEST = Comparator.comparing(Observation::getObservedAt)
            .thenComparing(observation -> observation.getId().toString());

    private ObjectNode previous(
            long workspaceId,
            ArtifactKind kind,
            Practice practice,
            Observation claim,
            Map<UUID, Optional<AgentJob>> runs) {
        AgentJob run = runs.computeIfAbsent(claim.getAgentJobId(), id -> jobs.findByIdAndWorkspaceId(id, workspaceId))
                .orElse(null);
        ObjectNode node = mapper.createObjectNode();
        node.put("reviewedAt", claim.getObservedAt().toString());
        SignalName signal = run == null ? null : PracticeCatalogInjector.signalOf(run);
        // A run that is gone still left its origin: a request by hand is one, anything else is taken as its moment.
        String type = run != null
                ? causeOf(run, signal, practice)
                : claim.getOrigin() == ObservationOrigin.LIVE ? MOMENT : REQUESTED;
        node.put("cause", type);
        if (!REQUESTED.equals(type) && signal != null && signal.artifactKind().equals(kind)) {
            node.put("signal", signal.value());
        }
        node.put("outcome", claim.getOutcome().name());
        if (claim.getSeverity() != null) {
            node.put("severity", claim.getSeverity().name());
        }
        var citations = node.putArray("citations");
        JsonNode evidence = claim.getEvidence();
        if (evidence != null) {
            for (JsonNode citation : evidence.path("citations")) {
                int startLine = citation.path("startLine").asInt(0);
                if (!citation.path("sourceKind").isString()
                        || !citation.path("path").isString()
                        || startLine < 1) {
                    continue;
                }
                ObjectNode staged = citations.addObject();
                staged.put("sourceKind", citation.path("sourceKind").asString());
                staged.put("path", citation.path("path").asString());
                staged.put("startLine", startLine);
                staged.put(
                        "endLine", Math.max(startLine, citation.path("endLine").asInt(startLine)));
                String side = citation.path("side").asString("");
                if (side.equals("OLD") || side.equals("NEW")) {
                    staged.put("side", side);
                }
            }
        }
        if (ArtifactKinds.PULL_REQUEST.equals(kind) && run != null && run.getEvidenceSnapshot() != null) {
            JsonNode head =
                    run.getEvidenceSnapshot().path(ReviewedWork.SNAPSHOT_KEY).path("head");
            if (head.isString() && head.asString().matches(CitationVerification.GIT_OBJECT_ID)) {
                node.put("head", head.asString());
            }
        }
        return node;
    }

    /** The reviewed work's id, as the job names it for its kind. */
    private static @Nullable Long workOf(ArtifactKind kind, JsonNode metadata) {
        String key;
        if (ArtifactKinds.PULL_REQUEST.equals(kind)) {
            key = "pull_request_id";
        } else if (ArtifactKinds.ISSUE.equals(kind)) {
            key = "issue_id";
        } else if (ArtifactKinds.DOCUMENT.equals(kind)) {
            key = DocumentContentSource.DOCUMENT_ID_METADATA_KEY;
        } else if (ArtifactKinds.CONVERSATION_THREAD.equals(kind)) {
            key = "slack_thread_id";
        } else {
            return null;
        }
        JsonNode id = metadata.path(key);
        return id.isIntegralNumber() ? id.asLong() : null;
    }

    /**
     * The person the review is about, as delivery files its observations: the person the job names, else the author
     * of the pull request or issue.
     */
    private @Nullable Long personOf(ArtifactKind kind, JsonNode metadata, long work) {
        JsonNode about = metadata.path("about_user_id");
        if (about.isIntegralNumber()) {
            return about.asLong();
        }
        Optional<ReviewTargetQuery.Target> target = ArtifactKinds.PULL_REQUEST.equals(kind)
                ? reviewTargets.findPullRequest(work)
                : ArtifactKinds.ISSUE.equals(kind) ? reviewTargets.findIssue(work) : Optional.empty();
        return target.map(ReviewTargetQuery.Target::authorId).orElse(null);
    }

    private byte[] write(JsonNode node) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
        } catch (JacksonException e) {
            throw new JobPreparationException("Failed to serialize practice script inputs: " + e.getMessage(), e);
        }
    }
}
