package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * What the provider last told us about a pull request, merge request or issue: who wrote it, where it stands, its
 * branches, times, size and checks. A field the provider did not report, or that this kind of work does not have,
 * is absent.
 */
@Schema(description = "The facts the provider reported about a piece of reviewed work")
public record ReviewWorkFactsDTO(
        @NonNull ArtifactKind artifactKind,
        @NonNull Long artifactId,

        @Schema(description = "Who opened the work; absent when the account is unknown") @Nullable
        ReviewSubjectDTO author,

        @Schema(description = "OPEN, CLOSED, or MERGED for a merged pull request or merge request") @NonNull
        WorkState state,

        @Schema(description = "Why an issue was closed or reopened") @Nullable
        WorkStateReason stateReason,

        @Schema(description = "Whether a pull request or merge request is a draft; absent for an issue") @Nullable
        Boolean draft,

        @Schema(description = "The branch the changes come from") @Nullable
        String sourceBranch,

        @Schema(description = "The branch the changes go into") @Nullable
        String targetBranch,

        @Schema(description = "The commit at the head of the source branch") @Nullable
        String headRevision,

        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable Instant mergedAt,
        @Nullable Instant closedAt,

        @Schema(description = "Lines added") @Nullable Integer additions,

        @Schema(description = "Lines deleted") @Nullable Integer deletions,

        @Schema(description = "Files changed") @Nullable Integer changedFiles,

        @Schema(description = "Commits on the work") @Nullable
        Integer commits,

        @Schema(description = "The combined state of the checks on the head commit, when the provider reported one")
        @Nullable
        WorkCheckState checkState,

        @Schema(description = "When the check state was last observed") @Nullable
        Instant checkObservedAt,

        @Schema(description = "When the work was last read from the provider") @Nullable
        Instant lastSyncAt) {

    /**
     * Where the work stands, in the API's own words. Practices takes the provider's states by name rather than its
     * entities, so the contract between the two stays a port.
     */
    public enum WorkState {
        OPEN,
        CLOSED,
        MERGED,
    }

    /** Why an issue was closed or reopened, as the provider reported it. */
    public enum WorkStateReason {
        COMPLETED,
        DUPLICATE,
        NOT_PLANNED,
        REOPENED,
        UNKNOWN,
    }

    /** The combined state of the checks on the head commit, as the provider reported it. */
    public enum WorkCheckState {
        SUCCESS,
        FAILURE,
        PENDING,
        CANCELLED,
        NONE,
        NO_PIPELINE,
        SKIPPED,
    }
}
