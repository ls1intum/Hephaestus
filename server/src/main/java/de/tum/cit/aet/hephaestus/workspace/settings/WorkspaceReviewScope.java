package de.tum.cit.aet.hephaestus.workspace.settings;

import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The work a workspace reviews. Under a {@code SELECTED} mode an empty list admits nobody, never
 * everybody; a selected repository with no branches admits every branch of it.
 */
public record WorkspaceReviewScope(
    @NonNull ReviewRepositoryMode repositoryMode,
    @NonNull ReviewPersonMode personMode,
    @NonNull List<ReviewRepositoryTarget> repositories,
    @NonNull List<Long> personUserIds
) {
    public static final WorkspaceReviewScope ALL = new WorkspaceReviewScope(
        ReviewRepositoryMode.ALL_MONITORED,
        ReviewPersonMode.ALL_ELIGIBLE,
        List.of(),
        List.of()
    );

    public WorkspaceReviewScope {
        repositoryMode = repositoryMode == null ? ReviewRepositoryMode.ALL_MONITORED : repositoryMode;
        personMode = personMode == null ? ReviewPersonMode.ALL_ELIGIBLE : personMode;
        repositories =
            repositories == null
                ? List.of()
                : repositories.stream().filter(java.util.Objects::nonNull).distinct().toList();
        personUserIds =
            personUserIds == null
                ? List.of()
                : personUserIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    public boolean admitsRepository(@Nullable String nameWithOwner, @Nullable String baseBranch) {
        return admitsRepository(nameWithOwner, baseBranch, true);
    }

    public boolean admitsRepository(
        @Nullable String nameWithOwner,
        @Nullable String baseBranch,
        boolean branchRestrictionsApply
    ) {
        if (repositoryMode == ReviewRepositoryMode.ALL_MONITORED) {
            return true;
        }
        return repositories
            .stream()
            .filter(selection -> selection.nameWithOwner().equals(nameWithOwner))
            .anyMatch(
                selection ->
                    !branchRestrictionsApply ||
                    selection.baseBranches().isEmpty() ||
                    (baseBranch != null && selection.baseBranches().contains(baseBranch))
            );
    }

    public boolean admitsPerson(@Nullable Long userId) {
        return userId != null && (personMode == ReviewPersonMode.ALL_ELIGIBLE || personUserIds.contains(userId));
    }

    public boolean admits(@Nullable String nameWithOwner, @Nullable String baseBranch, @Nullable Long userId) {
        return admits(nameWithOwner, baseBranch, userId, true);
    }

    public boolean admits(
        @Nullable String nameWithOwner,
        @Nullable String baseBranch,
        @Nullable Long userId,
        boolean branchRestrictionsApply
    ) {
        return admitsRepository(nameWithOwner, baseBranch, branchRestrictionsApply) && admitsPerson(userId);
    }
}
