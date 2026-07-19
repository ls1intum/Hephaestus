package de.tum.cit.aet.hephaestus.workspace.audit;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceFeatures;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Audit snapshots for workspace-administration changes. Records carry no secrets — a token rotation
 *  snapshots only that a token is set, never its value. */
public final class WorkspaceAuditSnapshots {

    private WorkspaceAuditSnapshots() {}

    /** The eight workspace feature flags. */
    public record FeaturesSnapshot(
        @Nullable Boolean practicesEnabled,
        @Nullable Boolean mentorEnabled,
        @Nullable Boolean achievementsEnabled,
        @Nullable Boolean leaderboardEnabled,
        @Nullable Boolean progressionEnabled,
        @Nullable Boolean leaguesEnabled,
        @Nullable Boolean practiceReviewAutoTriggerEnabled,
        @Nullable Boolean practiceReviewManualTriggerEnabled
    ) implements ConfigAuditSnapshot {
        public static FeaturesSnapshot of(WorkspaceFeatures f) {
            return new FeaturesSnapshot(
                f.getPracticesEnabled(),
                f.getMentorEnabled(),
                f.getAchievementsEnabled(),
                f.getLeaderboardEnabled(),
                f.getProgressionEnabled(),
                f.getLeaguesEnabled(),
                f.getPracticeReviewAutoTriggerEnabled(),
                f.getPracticeReviewManualTriggerEnabled()
            );
        }
    }

    /** Whether the workspace is publicly viewable. */
    public record VisibilitySnapshot(@Nullable Boolean publiclyViewable) implements ConfigAuditSnapshot {}

    /** Presence of a stored SCM token — never the token itself. */
    public record TokenSnapshot(
        boolean tokenSet,
        @Nullable String providerKind,
        @Nullable Instant rotatedAt
    ) implements ConfigAuditSnapshot {}

    /** A member's role in the workspace. */
    public record RoleSnapshot(@Nullable String role, boolean hidden) implements ConfigAuditSnapshot {}

    /** The workspace lifecycle status. */
    public record StatusSnapshot(@Nullable String status) implements ConfigAuditSnapshot {
        public static StatusSnapshot of(Workspace w) {
            return new StatusSnapshot(w.getStatus() == null ? null : w.getStatus().name());
        }
    }
}
