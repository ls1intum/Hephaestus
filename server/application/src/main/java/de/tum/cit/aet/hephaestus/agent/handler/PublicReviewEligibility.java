package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.providers.GeneralReviewCommentContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.IssueContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.LinkedWorkItemContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.RepositoryTreeContentSource;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewThreadContentSource;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ActorRole;
import de.tum.cit.aet.hephaestus.integration.scm.ReviewTargetQuery;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultCitations;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Which results may support feedback on the reviewed work itself. Public feedback speaks only about the work's
 * author: a result qualifies when the practice revision it was assessed under is about the author (or the author
 * as merger), it is about the mirrored author of this exact work, and its recorded provenance names only captured,
 * public sources. A reviewer result stays private, even when the reviewer wrote the work. An unknown author,
 * revision or role qualifies nothing. The same set governs model input, the owed review and final admission; removing
 * citations cannot remove claims derived from them.
 */
@Component
public class PublicReviewEligibility {

    /** The sources that capture the reviewed work, its repository and its discussion. */
    private static final Set<String> CAPTURED_WORK = Stream.of(
                    PullRequestContentSource.CORE,
                    PullRequestContentSource.DIFF,
                    PullRequestContentSource.COMMENTS,
                    IssueContentSource.CORE,
                    IssueContentSource.COMMENTS,
                    RepositoryTreeContentSource.KIND,
                    LinkedWorkItemContentSource.KIND,
                    ReviewThreadContentSource.KIND,
                    GeneralReviewCommentContentSource.KIND)
            .map(SourceKind::value)
            .collect(Collectors.toUnmodifiableSet());

    /** The evidence branches that list what a result consulted. */
    private static final Set<String> CONSULTING_BRANCHES = Set.of("search", "inapplicability", "undecidability");

    private final ReviewTargetQuery reviewTargets;
    private final RepositoryToMonitorRepository monitoredRepositories;

    private final PracticeResultRepository practiceResults;
    private final FeedbackRepository feedback;
    private final FeedbackEvidenceRepository feedbackResults;

    PublicReviewEligibility(
            ReviewTargetQuery reviewTargets,
            RepositoryToMonitorRepository monitoredRepositories,
            PracticeResultRepository practiceResults,
            FeedbackRepository feedback,
            FeedbackEvidenceRepository feedbackResults) {
        this.reviewTargets = reviewTargets;
        this.monitoredRepositories = monitoredRepositories;
        this.practiceResults = practiceResults;
        this.feedback = feedback;
        this.feedbackResults = feedbackResults;
    }

    /** The ids of the results that may support what the reviewed work is told. */
    public Set<UUID> publicResultIds(AgentJob job, Collection<PracticeResult> practiceResults) {
        Long author = artifactAuthor(job);
        if (author == null) return Set.of();
        return publicResultIds(job, practiceResults, author);
    }

    private Set<UUID> publicResultIds(AgentJob job, Collection<PracticeResult> practiceResults, long author) {
        return practiceResults.stream()
                .filter(practiceResult -> belongsToWork(job, practiceResult)
                        && isAboutAuthor(job, practiceResult, author)
                        && admitsProvenance(practiceResult))
                .map(PracticeResult::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Rechecks the exact supports of a persisted package, before it starts any new provider write. */
    public boolean permitsDelivery(
            AgentJob job, @Nullable UUID feedbackId, Collection<String> practiceSlugs, @Nullable Set<UUID> citedIds) {
        Long author = artifactAuthor(job);
        JsonNode metadata = job.getMetadata();
        if (metadata == null
                || author == null
                || job.getWorkspace() == null
                || job.getWorkspace().getId() == null) return false;
        long workspace = job.getWorkspace().getId();
        List<PracticeResult> support;
        if (feedbackId != null) {
            var stored = feedback.findByIdAndWorkspaceId(feedbackId, workspace).orElse(null);
            if (stored == null
                    || stored.getChannel() != FeedbackChannel.IN_CONTEXT
                    || !Objects.equals(stored.getAboutUserId(), author)
                    || !Objects.equals(stored.getRecipientUserId(), author)
                    || !Objects.equals(stored.getAgentJobId(), job.getId())
                    || !Objects.equals(stored.getArtifactKind(), job.getArtifactKind())
                    || !Objects.equals(
                            stored.getArtifactId(),
                            metadata.path(
                                            ArtifactKinds.PULL_REQUEST.equals(job.getArtifactKind())
                                                    ? "pull_request_id"
                                                    : "issue_id")
                                    .asLong())) return false;
            support = feedbackResults.findForVisibility(workspace, Set.of(feedbackId)).stream()
                    .map(FeedbackEvidenceRepository.FeedbackResultVisibility::getPracticeResult)
                    .toList();
            if (citedIds != null
                    && !citedIds.equals(
                            support.stream().map(PracticeResult::getId).collect(Collectors.toSet()))) return false;
        } else if (citedIds != null) {
            if (citedIds.isEmpty()) return false;
            support = practiceResults.findAllByIdInAndWorkspaceId(citedIds, workspace);
            if (support.size() != citedIds.size()) return false;
        } else {
            support = practiceResults.findByAgentJobId(job.getId(), workspace).stream()
                    .filter(practiceResult ->
                            practiceSlugs.contains(practiceResult.getPractice().getSlug()))
                    .toList();
        }
        if (support.isEmpty()) return false;
        Set<UUID> eligible = publicResultIds(job, support, author);
        return support.stream()
                        .allMatch(practiceResult -> eligible.contains(practiceResult.getId())
                                && (practiceResult.getOutcome() == Outcome.MET
                                        || practiceResult.getOutcome() == Outcome.NOT_MET))
                && practiceSlugs.stream()
                        .allMatch(slug -> support.stream()
                                .anyMatch(practiceResult ->
                                        slug.equals(practiceResult.getPractice().getSlug())));
    }

    /** Public history remains about the author of its original work, under its recorded practice revisions. */
    public boolean permitsPublicHistory(Feedback stored, Collection<PracticeResult> support) {
        if (stored.getChannel() != FeedbackChannel.IN_CONTEXT || stored.getArtifactId() == null || support.isEmpty())
            return false;
        Optional<ReviewTargetQuery.Target> target = ArtifactKinds.PULL_REQUEST.equals(stored.getArtifactKind())
                ? reviewTargets.findPullRequest(stored.getArtifactId())
                : ArtifactKinds.ISSUE.equals(stored.getArtifactKind())
                        ? reviewTargets.findIssue(stored.getArtifactId())
                        : Optional.empty();
        var work = target.orElse(null);
        if (work == null
                || work.deleted()
                || work.authorId() == null
                || !monitoredRepositories.existsByWorkspaceIdAndNameWithOwner(
                        stored.getWorkspaceId(), work.repositoryFullName())
                || !Objects.equals(stored.getAboutUserId(), work.authorId())
                || !Objects.equals(stored.getRecipientUserId(), work.authorId())) return false;
        return support.stream().allMatch(practiceResult -> {
            PracticeRevision revision = practiceResult.getPracticeRevision();
            ActorRole role = revision == null ? null : revision.getSubject();
            return (practiceResult.getOutcome() == Outcome.MET || practiceResult.getOutcome() == Outcome.NOT_MET)
                    && (role == ActorRole.AUTHOR || role == ActorRole.MERGER)
                    && Objects.equals(practiceResult.getAgentJobId(), stored.getAgentJobId())
                    && Objects.equals(practiceResult.getWorkspaceId(), stored.getWorkspaceId())
                    && Objects.equals(practiceResult.getArtifactKind(), stored.getArtifactKind())
                    && Objects.equals(practiceResult.getArtifactId(), stored.getArtifactId())
                    && Objects.equals(practiceResult.getAboutUserId(), work.authorId())
                    && admitsProvenance(practiceResult);
        });
    }

    /**
     * The internal user id of the author of the job's own work, read once from the mirrored work. Only a job about
     * the author has one: a job that names any other subject role reaches the work with nothing.
     */
    private @Nullable Long artifactAuthor(AgentJob job) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null || !isAuthorJob(metadata) || job.getWorkspace() == null) return null;
        Long workspaceId = job.getWorkspace().getId();
        Optional<ReviewTargetQuery.Target> target;
        String numberKey;
        if (ArtifactKinds.PULL_REQUEST.equals(job.getArtifactKind())
                && metadata.path("pull_request_id").isIntegralNumber()) {
            target = reviewTargets.findPullRequest(
                    metadata.path("pull_request_id").asLong());
            numberKey = "pr_number";
        } else if (ArtifactKinds.ISSUE.equals(job.getArtifactKind())
                && metadata.path("issue_id").isIntegralNumber()) {
            target = reviewTargets.findIssue(metadata.path("issue_id").asLong());
            numberKey = "issue_number";
        } else {
            return null;
        }
        return target.filter(work -> workspaceId != null
                        && PracticeFeedbackDeliveryPolicy.matchesArtifact(work, metadata, numberKey)
                        && monitoredRepositories.existsByWorkspaceIdAndNameWithOwner(
                                workspaceId, work.repositoryFullName()))
                .map(ReviewTargetQuery.Target::authorId)
                .orElse(null);
    }

    /** A job without a subject role is about the author; that is how author reviews are recorded. */
    static boolean isAuthorJob(JsonNode metadata) {
        JsonNode role = metadata.path("subject_role");
        return role.isMissingNode() || ActorRole.AUTHOR.name().equals(role.asString(""));
    }

    private static boolean belongsToWork(AgentJob job, PracticeResult practiceResult) {
        JsonNode metadata = job.getMetadata();
        if (metadata == null || job.getWorkspace() == null || practiceResult.getId() == null) return false;
        String key = ArtifactKinds.PULL_REQUEST.equals(job.getArtifactKind()) ? "pull_request_id" : "issue_id";
        return Objects.equals(practiceResult.getAgentJobId(), job.getId())
                && Objects.equals(
                        practiceResult.getWorkspaceId(), job.getWorkspace().getId())
                && Objects.equals(practiceResult.getArtifactKind(), job.getArtifactKind())
                && metadata.path(key).isIntegralNumber()
                && Objects.equals(
                        practiceResult.getArtifactId(), metadata.path(key).asLong());
    }

    private static boolean isAboutAuthor(AgentJob job, PracticeResult practiceResult, long author) {
        PracticeRevision revision = practiceResult.getPracticeRevision();
        ActorRole subject = revision == null ? null : revision.getSubject();
        return (subject == ActorRole.AUTHOR
                        || (subject == ActorRole.MERGER
                                && PracticeCatalogInjector.subjectNameable(ActorRole.MERGER, job.getMetadata())))
                && Long.valueOf(author).equals(practiceResult.getAboutUserId());
    }

    /**
     * Whether the result's recorded provenance names only captured, public sources: every place it cites, its
     * problems', its example's and its open questions' included, and every source its warrants consulted.
     */
    static boolean admitsProvenance(PracticeResult practiceResult) {
        JsonNode evidence = practiceResult.getEvidence();
        if (evidence == null || !evidence.isObject()) {
            return false;
        }
        boolean namesASource = false;
        for (JsonNode citation : ResultCitations.all(practiceResult)) {
            if (!CAPTURED_WORK.contains(citation.path("sourceKind").asString(""))) {
                return false;
            }
            namesASource = true;
        }
        for (String branch : CONSULTING_BRANCHES) {
            for (JsonNode consulted : evidence.path(branch).path("consulted")) {
                if (!CAPTURED_WORK.contains(consulted.asString(""))) {
                    return false;
                }
                namesASource = true;
            }
        }
        return namesASource;
    }
}
