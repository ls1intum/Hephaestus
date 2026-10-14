package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.item.Item;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Scoped native joins return only the source facts needed by the SCM item boundary. */
public interface ScmItemQueryRepository extends Repository<Item, Long> {
    @Query(value = """
        WITH selected AS (SELECT * FROM item WHERE workspace_id=:workspaceId AND id IN (:ids))
        SELECT idx.id,
          CASE idx.kind WHEN :repositoryKind THEN repository.name_with_owner
            WHEN :fileKind THEN idx.path WHEN :reviewKind THEN 'Review of '||work.title
            ELSE work.title END AS title,
          CASE idx.kind WHEN :repositoryKind THEN 'repository' WHEN :fileKind THEN 'file'
            WHEN :reviewKind THEN 'review' WHEN :issueKind THEN 'issue'
            ELSE CASE provider.type WHEN 'GITLAB' THEN 'merge request' ELSE 'pull request' END END AS noun,
          work.number, repository.name_with_owner AS container, provider.type AS provider_type,
          CASE WHEN idx.removed_at IS NOT NULL OR work.deleted_at IS NOT NULL THEN NULL
            WHEN idx.kind=:repositoryKind THEN repository.html_url
            WHEN idx.kind=:fileKind THEN NULL
            WHEN idx.kind=:reviewKind THEN review.html_url
            ELSE work.html_url END AS url
        FROM selected idx
        LEFT JOIN item holder ON holder.workspace_id=idx.workspace_id AND holder.id=idx.parent_id
        LEFT JOIN pull_request_review review ON idx.kind=:reviewKind AND review.id=idx.source_id
        LEFT JOIN issue work ON work.id=CASE WHEN idx.kind=:reviewKind THEN review.pull_request_id
          WHEN idx.kind IN (:issueKind,:pullKind) THEN idx.source_id END
        JOIN repository ON repository.id=CASE idx.kind WHEN :repositoryKind THEN idx.source_id
          WHEN :fileKind THEN holder.source_id ELSE work.repository_id END
        JOIN identity_provider provider ON provider.id=repository.provider_id
        WHERE provider.type IN ('GITHUB','GITLAB') AND (
          (idx.kind=:issueKind AND work.issue_type='ISSUE')
          OR (idx.kind=:pullKind AND work.issue_type='PULL_REQUEST')
          OR (idx.kind=:reviewKind AND holder.kind=:pullKind AND holder.source_id=review.pull_request_id)
          OR idx.kind=:repositoryKind OR (idx.kind=:fileKind AND holder.kind=:repositoryKind))
        """, nativeQuery = true)
    List<LabelRow> labels(
            @Param("workspaceId") long workspaceId,
            @Param("ids") List<Long> ids,
            @Param("repositoryKind") String repositoryKind,
            @Param("fileKind") String fileKind,
            @Param("reviewKind") String reviewKind,
            @Param("pullKind") String pullKind,
            @Param("issueKind") String issueKind);

    @Query(value = """
                SELECT i.id, work.id AS pull_id, work.issue_type, work.is_merged, work.merged_at, work.repository_id,
                    repository_item.id AS repository_item_id
                FROM item i
                LEFT JOIN item parent ON parent.id=i.parent_id AND parent.workspace_id=i.workspace_id
                LEFT JOIN pull_request_review review ON i.kind=:reviewKind
                    AND review.id=i.source_id AND parent.kind=:pullKind
                    AND parent.source_id=review.pull_request_id
                LEFT JOIN issue work ON work.id=CASE WHEN i.kind=:pullKind THEN i.source_id
                    WHEN i.kind=:reviewKind THEN review.pull_request_id END
                    AND (review.id IS NULL OR review.provider_id=work.provider_id)
                LEFT JOIN item repository_item ON repository_item.workspace_id=i.workspace_id
                    AND repository_item.kind=:repositoryKind AND repository_item.source_id=work.repository_id
                    AND repository_item.id=CASE WHEN i.kind=:pullKind THEN i.parent_id ELSE parent.parent_id END
                WHERE i.workspace_id=:workspaceId AND i.id IN (:ids)
        """, nativeQuery = true)
    List<MergeRow> mergeFacts(
            @Param("workspaceId") long workspaceId,
            @Param("ids") List<Long> ids,
            @Param("repositoryKind") String repositoryKind,
            @Param("reviewKind") String reviewKind,
            @Param("pullKind") String pullKind);

    @Query(value = """
                SELECT i.id AS item_id, m.id AS monitor_id, r.name_with_owner, p.server_url
                FROM item i JOIN repository r ON r.id=i.source_id
                JOIN identity_provider p ON p.id=r.provider_id
                JOIN repository_to_monitor m ON m.workspace_id=i.workspace_id
                  AND m.name_with_owner=r.name_with_owner
                  AND (m.native_id IS NULL OR m.native_id=r.native_id)
                WHERE i.workspace_id=:workspaceId AND i.kind=:repositoryKind AND i.parent_id IS NULL
                  AND i.removed_at IS NULL AND p.type=:providerType ORDER BY i.id
        """, nativeQuery = true)
    List<MonitorRow> monitored(
            @Param("workspaceId") long workspaceId,
            @Param("repositoryKind") String repositoryKind,
            @Param("providerType") String providerType);

    @WorkspaceAgnostic("Reads native SCM mirror facts after the container repository passes workspace authorization")
    @Query(value = """
        SELECT id AS source_id, NULL::bigint AS parent_source_id, repository_id
        FROM issue WHERE repository_id=:repositoryId AND deleted_at IS NULL
          AND issue_type=:discriminator ORDER BY created_at,id
        """, nativeQuery = true)
    List<WorkRow> listWork(@Param("repositoryId") long repositoryId, @Param("discriminator") String discriminator);

    @WorkspaceAgnostic("Reads native SCM mirror facts after the container repository passes workspace authorization")
    @Query(value = """
        SELECT review.id AS source_id, review.pull_request_id AS parent_source_id, work.repository_id
        FROM pull_request_review review JOIN issue work ON work.id=review.pull_request_id
        WHERE work.repository_id=:repositoryId AND work.deleted_at IS NULL
          AND review.submitted_at IS NOT NULL ORDER BY review.submitted_at,review.id
        """, nativeQuery = true)
    List<WorkRow> listReviews(@Param("repositoryId") long repositoryId);

    @WorkspaceAgnostic("Reads native SCM mirror facts after the container repository passes workspace authorization")
    @Query(value = """
        SELECT work.id AS source_id, NULL::bigint AS parent_source_id, work.repository_id
        FROM issue work WHERE NOT :reviews AND work.id IN (:sourceIds)
        UNION ALL
        SELECT review.id AS source_id, review.pull_request_id AS parent_source_id, work.repository_id
        FROM pull_request_review review JOIN issue work ON work.id=review.pull_request_id
        WHERE :reviews AND review.id IN (:sourceIds)
        """, nativeQuery = true)
    List<WorkRow> owners(@Param("sourceIds") List<Long> sourceIds, @Param("reviews") boolean reviews);

    @Query(value = """
        SELECT parent.id FROM item parent JOIN pull_request_review review ON review.pull_request_id=parent.source_id
        WHERE parent.workspace_id=:workspaceId AND parent.kind=:pullKind AND review.id=:reviewId
        """, nativeQuery = true)
    List<Long> reviewParents(
            @Param("workspaceId") long workspaceId,
            @Param("pullKind") String pullKind,
            @Param("reviewId") long reviewId);

    interface LabelRow {
        long getId();

        String getTitle();

        String getNoun();

        @Nullable
        Integer getNumber();

        String getContainer();

        @Nullable
        String getUrl();

        String getProviderType();
    }

    interface MergeRow {
        long getId();

        @Nullable
        Long getPullId();

        @Nullable
        String getIssueType();

        @Nullable
        Long getRepositoryItemId();

        boolean getIsMerged();

        @Nullable
        Instant getMergedAt();

        @Nullable
        Long getRepositoryId();
    }

    interface MonitorRow {
        long getItemId();

        long getMonitorId();

        String getNameWithOwner();

        String getServerUrl();
    }

    interface WorkRow {
        long getSourceId();

        @Nullable
        Long getParentSourceId();

        long getRepositoryId();
    }
}
