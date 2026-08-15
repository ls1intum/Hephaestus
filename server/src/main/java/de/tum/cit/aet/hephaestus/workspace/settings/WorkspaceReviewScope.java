package de.tum.cit.aet.hephaestus.workspace.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The workspace's answer to "which of our work is reviewed at all", ANDed onto every practice binding.
 * It exists because a binding says {@code scm.pull_request.merged} and cannot say <em>merged into
 * what</em>: a trunk is named {@code main} here and {@code develop} there, which is a deployment fact
 * about one workspace that a centrally curated catalogue cannot carry.
 *
 * <p>The scope only ever narrows, never widens: an empty or absent list means "no restriction on this
 * axis".
 *
 * <h2>What this cannot express, and why</h2>
 *
 * <ul>
 *   <li><strong>Changed paths.</strong> Not decidable here: the detection gate holds the
 *       {@code PullRequest} row, not the diff, and changed paths do not exist until the evidence stage,
 *       by which point the review has been admitted and paid for. A path axis here would be a predicate
 *       that quietly never narrows anything.
 *   <li><strong>Branch patterns.</strong> Exact names only; adding globs later stays backward
 *       compatible, taking them away would not.
 *   <li><strong>Any third key.</strong> The vocabulary is closed at the column
 *       ({@code chk_workspace_review_scope}), not by this type: a reader configured to ignore unknown
 *       fields would drop the key in silence, leaving a workspace believing a restriction was in force.
 * </ul>
 *
 * @param targetBranches exact target-branch names (a PR's base ref). Empty = every branch.
 * @param repositories exact {@code owner/name} repository identifiers. Empty = every monitored
 *     repository. This narrows <em>review</em> within the set the workspace already syncs; it never
 *     adds one.
 */
public record WorkspaceReviewScope(List<String> targetBranches, List<String> repositories) {
    public static final WorkspaceReviewScope UNRESTRICTED = new WorkspaceReviewScope(List.of(), List.of());

    /** Explicit property names: the stored JSON must not depend on the {@code -parameters} compile flag. */
    @JsonCreator
    public WorkspaceReviewScope(
        @JsonProperty("targetBranches") @Nullable List<String> targetBranches,
        @JsonProperty("repositories") @Nullable List<String> repositories
    ) {
        this.targetBranches = normalize(targetBranches);
        this.repositories = normalize(repositories);
    }

    private static List<String> normalize(@Nullable List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values
            .stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    @JsonIgnore
    public boolean isUnrestricted() {
        return targetBranches.isEmpty() && repositories.isEmpty();
    }

    /**
     * @param targetBranch the PR's base ref, or {@code null} for work that has no branch at all (an
     *     issue). A null branch <em>passes</em> the branch axis: failing closed would silently stop every
     *     issue review the moment a workspace named a trunk.
     */
    public boolean admits(@Nullable String repositoryNameWithOwner, @Nullable String targetBranch) {
        if (!repositories.isEmpty() && !repositories.contains(repositoryNameWithOwner)) {
            return false;
        }
        return targetBranches.isEmpty() || targetBranch == null || targetBranches.contains(targetBranch);
    }
}
