package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.MonitoredRepositoryScope;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The provider's facts about one pull request, merge request or issue in a repository the workspace monitors, unless
 * the provider deleted it.
 * Read-only, so it extends the bare {@link Repository} and exposes no write.
 */
interface ReviewWorkFactsRepository extends Repository<Feedback, UUID> {

    @Query(value = """
        SELECT i.state AS "state", i.state_reason AS "stateReason", i.is_draft AS "draft", i.is_merged AS "merged",
               i.author_id AS "authorId", i.head_ref_name AS "sourceBranch", i.base_ref_name AS "targetBranch",
               i.head_ref_oid AS "headRevision", i.created_at AS "createdAt", i.updated_at AS "updatedAt",
               i.merged_at AS "mergedAt", i.closed_at AS "closedAt", i.additions AS "additions",
               i.deletions AS "deletions", i.changed_files AS "changedFiles", i.commits AS "commits",
               i.head_check_state AS "checkState", i.head_check_observed_at AS "checkObservedAt",
               i.last_sync_at AS "lastSyncAt"
        FROM issue i
        WHERE i.id = :artifactId AND i.issue_type = :issueType AND i.deleted_at IS NULL
          AND EXISTS (
              SELECT 1 FROM repository r
              JOIN repository_to_monitor rtm ON rtm.name_with_owner = r.name_with_owner
                AND\s""" + MonitoredRepositoryScope.NATIVE_RTM_R + """

              WHERE r.id = i.repository_id AND rtm.workspace_id = :workspaceId)
        """, nativeQuery = true)
    Optional<WorkFactsRow> findFacts(
            @Param("workspaceId") Long workspaceId,
            @Param("issueType") String issueType,
            @Param("artifactId") Long artifactId);

    interface WorkFactsRow {
        @Nullable
        String getState();

        @Nullable
        String getStateReason();

        @Nullable
        Boolean getDraft();

        @Nullable
        Boolean getMerged();

        @Nullable
        Long getAuthorId();

        @Nullable
        String getSourceBranch();

        @Nullable
        String getTargetBranch();

        @Nullable
        String getHeadRevision();

        @Nullable
        Instant getCreatedAt();

        @Nullable
        Instant getUpdatedAt();

        @Nullable
        Instant getMergedAt();

        @Nullable
        Instant getClosedAt();

        @Nullable
        Integer getAdditions();

        @Nullable
        Integer getDeletions();

        @Nullable
        Integer getChangedFiles();

        @Nullable
        Integer getCommits();

        @Nullable
        String getCheckState();

        @Nullable
        Instant getCheckObservedAt();

        @Nullable
        Instant getLastSyncAt();
    }
}
