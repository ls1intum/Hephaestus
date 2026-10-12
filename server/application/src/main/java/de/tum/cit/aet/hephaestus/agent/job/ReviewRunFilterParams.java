package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.ReviewRunTargetMapper.WorkKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Optional filters for the review listing, flattened onto the query string by {@link ParameterObject}.
 *
 * <p>{@code from}/{@code to} are the same half-open window the sibling result and feedback
 * listings take — see {@code ReviewResultFilterParams} — down to the rejection message, so the
 * three surfaces under {@code /practices/reviews} answer a pasted date range identically.
 *
 * <p>{@code subjectUserId}, {@code practiceSlug} and {@code groupSlug} keep the reviews that recorded at least one
 * result matching all three, with the names and the meaning the result listing gives them. A result an admin marked
 * incorrect does not count here, while the result listing keeps it unless {@code invalidated} is false. Thus, a
 * developer or practice picked on one list narrows the other to the same reviews only when that list leaves out the
 * results marked incorrect.
 *
 * <p>{@code status}, {@code onHold} and {@code waiting} are alternatives: a review matches when it has one of the
 * listed statuses, with {@code onHold} when it is queued on a hold, or with {@code waiting} when it waits for a worker
 * now, as the queue counts it. {@code onHold} or {@code waiting} alone keeps only those reviews.
 *
 * <p>{@code agentJobId}, {@code artifactKind} and {@code artifactId} keep one review, or the reviews of one piece of
 * work, with the names the result and feedback listings give them.
 *
 * @param artifactKind a bare string, not an {@link ArtifactKind}: springdoc would publish a record as
 *     {@code artifactKind.value}. {@link #validated()} parses it.
 */
public record ReviewRunFilterParams(
        @Parameter(description = "Statuses to list (repeatable); omit for every status")
        @RequestParam(required = false)
        @Nullable
        List<AgentJobStatus> status,

        @Parameter(
                description = "Result-processing statuses to list (repeatable), such as FAILED for the reviews whose"
                        + " results can be processed again; omit for every review, including those without one")
        @RequestParam(required = false)
        @Nullable
        List<DeliveryStatus> resultProcessing,

        @Parameter(description = "Inclusive lower bound on when the review was requested")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant from,

        @Parameter(description = "Exclusive upper bound on when the review was requested")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant to,

        @Parameter(
                description =
                        "Keep the reviews that recorded a result about this developer that is not marked incorrect")
        @RequestParam(required = false)
        @Positive
        @Nullable
        Long subjectUserId,

        @Parameter(
                description = "Keep the reviews that recorded a result of any of these practices that is not"
                        + " marked incorrect (repeatable)")
        @RequestParam(required = false)
        @Nullable
        List<String> practiceSlug,

        @Parameter(
                description = "Keep the reviews that recorded a result of a practice in any of these groups"
                        + " that is not marked incorrect (repeatable)")
        @RequestParam(required = false)
        @Nullable
        List<String> groupSlug,

        @Parameter(description = "Keep this one review") @RequestParam(required = false) @Nullable
        UUID agentJobId,

        @Parameter(description = "Keep the reviews of this kind of work, e.g. scm.pull_request")
        @RequestParam(required = false)
        @Nullable
        String artifactKind,

        @Parameter(description = "Keep the reviews of this one piece of work; requires artifactKind")
        @RequestParam(required = false)
        @Positive
        @Nullable
        Long artifactId,

        @Parameter(
                description = "True keeps the pull request and merge request reviews that completed after the work"
                        + " was merged; false or omitted keeps every review")
        @RequestParam(required = false)
        @Nullable
        Boolean finishedAfterMerge,

        @Parameter(
                description = "True also keeps the queued reviews on hold, such as those waiting for the AI budget;"
                        + " alone it keeps only those. False or omitted adds nothing to the status filter")
        @RequestParam(required = false)
        @Nullable
        Boolean onHold,

        @Parameter(
                description = "True also keeps the queued reviews that wait for a worker now, as the queue counts them:"
                        + " not held for the AI budget and due now, not backing off before a retry. Alone it keeps"
                        + " only those. False or omitted adds nothing to the status filter")
        @RequestParam(required = false)
        @Nullable
        Boolean waiting) {
    /**
     * Rejects a backwards window, which would otherwise return an empty page for no visible reason, one item of work
     * without its kind, and a malformed kind.
     */
    public ReviewRunFilterParams validated() {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The start of the range must not be after its end.");
        }
        if (artifactId != null && artifactKind == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Choose a kind of work before you filter by one item.");
        }
        workKey();
        return this;
    }

    public boolean anyReview() {
        return agentJobId == null;
    }

    /** Never null, so the query can bind it even when {@link #anyReview()} leaves it unused. */
    public UUID agentJobIdValue() {
        return agentJobId == null ? new UUID(0L, 0L) : agentJobId;
    }

    public boolean anyWork() {
        return artifactKind == null;
    }

    /** False for a kind that no review reads, so the filter keeps nothing. */
    public boolean reviewableWork() {
        return anyWork() || workKey() != null;
    }

    /** Never null, so the query can bind it even when {@link #anyWork()} leaves it unused. */
    public AgentJobType workJobType() {
        WorkKey key = workKey();
        return key == null ? AgentJobType.PULL_REQUEST_REVIEW : key.jobType();
    }

    /** The metadata field that names the work, never null for the reason {@link #workJobType()} gives. */
    public String workIdField() {
        WorkKey key = workKey();
        return key == null ? "" : key.idField();
    }

    public boolean anyWorkItem() {
        return artifactId == null;
    }

    /** The work's id as the metadata holds it, read as text. Never null, as {@link #workJobType()}. */
    public String workIdText() {
        return artifactId == null ? "" : artifactId.toString();
    }

    private @Nullable WorkKey workKey() {
        if (artifactKind == null) {
            return null;
        }
        try {
            return ReviewRunTargetMapper.workKey(ArtifactKind.of(artifactKind));
        } catch (IllegalArgumentException malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, malformed.getMessage(), malformed);
        }
    }

    public boolean anyMergeTiming() {
        return !Boolean.TRUE.equals(finishedAfterMerge);
    }

    /** Only pull request reviews have work that can be merged. */
    public AgentJobType mergedWorkJobType() {
        return AgentJobType.PULL_REQUEST_REVIEW;
    }

    /** True when neither a status, "on hold" nor "waiting" narrows the list. */
    public boolean anyStatus() {
        return !hasListedStatus() && !heldOnly() && !waitingOnly();
    }

    public boolean hasListedStatus() {
        return status != null && !status.isEmpty();
    }

    /**
     * "On hold" is a status filter value of its own: a queued review that waits on a hold. It joins the listed
     * statuses as one more alternative, so "Failed" and "On hold" together keep both.
     */
    public boolean heldOnly() {
        return Boolean.TRUE.equals(onHold);
    }

    /**
     * "Waiting" is a status filter value of its own: a queued review that a worker could pick up now, the reviews the
     * queue's {@code waiting} count counts. It joins the listed statuses as one more alternative.
     */
    public boolean waitingOnly() {
        return Boolean.TRUE.equals(waiting);
    }

    /** Never empty, so the query can bind it even when {@link #hasListedStatus()} leaves it unused. */
    public Collection<AgentJobStatus> statuses() {
        return hasListedStatus() ? Objects.requireNonNull(status) : EnumSet.allOf(AgentJobStatus.class);
    }

    public boolean anyResultProcessing() {
        return resultProcessing == null || resultProcessing.isEmpty();
    }

    /** Never empty, so the query can bind it even when {@link #anyResultProcessing()} leaves it unused. */
    public Collection<DeliveryStatus> resultProcessingStates() {
        return resultProcessing == null || resultProcessing.isEmpty()
                ? EnumSet.allOf(DeliveryStatus.class)
                : resultProcessing;
    }

    /** True when no result filter is set, so the query can skip its subquery. */
    public boolean anyResult() {
        return anySubject() && anyPractice() && anyGroup();
    }

    public boolean anySubject() {
        return subjectUserId == null;
    }

    /** Never null, so the query can bind it even when {@link #anySubject()} leaves it unused. */
    public long subjectUserIdValue() {
        return subjectUserId == null ? 0L : subjectUserId;
    }

    public boolean anyPractice() {
        return practiceSlug == null || practiceSlug.isEmpty();
    }

    public boolean anyGroup() {
        return groupSlug == null || groupSlug.isEmpty();
    }

    /** Never empty, so the query can bind it even when {@link #anyPractice()} leaves it unused. */
    public Collection<String> practiceSlugs() {
        return anyPractice() ? List.of("") : Objects.requireNonNull(practiceSlug);
    }

    /** Never empty, so the query can bind it even when {@link #anyGroup()} leaves it unused. */
    public Collection<String> groupSlugs() {
        return anyGroup() ? List.of("") : Objects.requireNonNull(groupSlug);
    }
}
