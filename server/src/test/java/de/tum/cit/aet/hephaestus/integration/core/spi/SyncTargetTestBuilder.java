package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.SyncTargetProvider.SyncTarget;
import java.time.Instant;

/**
 * Test-only fluent builder for {@link SyncTarget}. The record's canonical constructor takes 22 positional
 * arguments, most of which are {@code null} in any given test; this keeps the production API free of a
 * "backward-compatible" convenience constructor while letting tests set only the fields a case cares about.
 * All fields default to {@code null} (and {@code nativeId} to {@code null}, the legacy/unresolved value).
 */
public final class SyncTargetTestBuilder {

    private Long id;
    private Long scopeId;
    private Long installationId;
    private String personalAccessToken;
    private AuthMode authMode;
    private String repositoryNameWithOwner;
    private Instant lastLabelsSyncedAt;
    private Instant lastMilestonesSyncedAt;
    private Instant lastIssuesSyncedAt;
    private Instant lastPullRequestsSyncedAt;
    private Instant lastDiscussionsSyncedAt;
    private Instant lastCollaboratorsSyncedAt;
    private Instant lastFullSyncAt;
    private Integer issueBackfillHighWaterMark;
    private Integer issueBackfillCheckpoint;
    private Integer pullRequestBackfillHighWaterMark;
    private Integer pullRequestBackfillCheckpoint;
    private Instant backfillLastRunAt;
    private String issueSyncCursor;
    private String pullRequestSyncCursor;
    private String discussionSyncCursor;
    private Long nativeId;

    private SyncTargetTestBuilder() {}

    public static SyncTargetTestBuilder syncTarget() {
        return new SyncTargetTestBuilder();
    }

    public SyncTargetTestBuilder id(Long value) {
        this.id = value;
        return this;
    }

    public SyncTargetTestBuilder scopeId(Long value) {
        this.scopeId = value;
        return this;
    }

    public SyncTargetTestBuilder installationId(Long value) {
        this.installationId = value;
        return this;
    }

    public SyncTargetTestBuilder personalAccessToken(String value) {
        this.personalAccessToken = value;
        return this;
    }

    public SyncTargetTestBuilder authMode(AuthMode value) {
        this.authMode = value;
        return this;
    }

    public SyncTargetTestBuilder repositoryNameWithOwner(String value) {
        this.repositoryNameWithOwner = value;
        return this;
    }

    public SyncTargetTestBuilder lastLabelsSyncedAt(Instant value) {
        this.lastLabelsSyncedAt = value;
        return this;
    }

    public SyncTargetTestBuilder lastMilestonesSyncedAt(Instant value) {
        this.lastMilestonesSyncedAt = value;
        return this;
    }

    public SyncTargetTestBuilder lastIssuesSyncedAt(Instant value) {
        this.lastIssuesSyncedAt = value;
        return this;
    }

    public SyncTargetTestBuilder lastPullRequestsSyncedAt(Instant value) {
        this.lastPullRequestsSyncedAt = value;
        return this;
    }

    public SyncTargetTestBuilder lastDiscussionsSyncedAt(Instant value) {
        this.lastDiscussionsSyncedAt = value;
        return this;
    }

    public SyncTargetTestBuilder lastCollaboratorsSyncedAt(Instant value) {
        this.lastCollaboratorsSyncedAt = value;
        return this;
    }

    public SyncTargetTestBuilder lastFullSyncAt(Instant value) {
        this.lastFullSyncAt = value;
        return this;
    }

    public SyncTargetTestBuilder issueBackfillHighWaterMark(Integer value) {
        this.issueBackfillHighWaterMark = value;
        return this;
    }

    public SyncTargetTestBuilder issueBackfillCheckpoint(Integer value) {
        this.issueBackfillCheckpoint = value;
        return this;
    }

    public SyncTargetTestBuilder pullRequestBackfillHighWaterMark(Integer value) {
        this.pullRequestBackfillHighWaterMark = value;
        return this;
    }

    public SyncTargetTestBuilder pullRequestBackfillCheckpoint(Integer value) {
        this.pullRequestBackfillCheckpoint = value;
        return this;
    }

    public SyncTargetTestBuilder backfillLastRunAt(Instant value) {
        this.backfillLastRunAt = value;
        return this;
    }

    public SyncTargetTestBuilder issueSyncCursor(String value) {
        this.issueSyncCursor = value;
        return this;
    }

    public SyncTargetTestBuilder pullRequestSyncCursor(String value) {
        this.pullRequestSyncCursor = value;
        return this;
    }

    public SyncTargetTestBuilder discussionSyncCursor(String value) {
        this.discussionSyncCursor = value;
        return this;
    }

    public SyncTargetTestBuilder nativeId(Long value) {
        this.nativeId = value;
        return this;
    }

    public SyncTarget build() {
        return new SyncTarget(
            id,
            scopeId,
            installationId,
            personalAccessToken,
            authMode,
            repositoryNameWithOwner,
            lastLabelsSyncedAt,
            lastMilestonesSyncedAt,
            lastIssuesSyncedAt,
            lastPullRequestsSyncedAt,
            lastDiscussionsSyncedAt,
            lastCollaboratorsSyncedAt,
            lastFullSyncAt,
            issueBackfillHighWaterMark,
            issueBackfillCheckpoint,
            pullRequestBackfillHighWaterMark,
            pullRequestBackfillCheckpoint,
            backfillLastRunAt,
            issueSyncCursor,
            pullRequestSyncCursor,
            discussionSyncCursor,
            nativeId
        );
    }
}
