package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackLocation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface FeedbackQueryRepository extends Repository<Feedback, UUID> {
    String LOCATED = """
        WITH located AS (
          SELECT f.*,
            CASE f.destination
              WHEN 'PRACTICE_PAGE' THEN 'PRACTICE_PAGES'
              WHEN 'ON_WORK' THEN CASE WHEN provider.type IN ('GITHUB','GITLAB') THEN provider.type ELSE 'OTHER_WORK' END
              WHEN 'CONVERSATION' THEN CASE WHEN conversation.surface='SLACK_DM' THEN 'SLACK'
                WHEN conversation.surface IS NOT NULL THEN 'HEPH' ELSE 'NEXT_CONVERSATION' END
            END AS location,
            (SELECT d.action='WITHDRAW' FROM feedback_decision d
              WHERE d.workspace_id=f.workspace_id AND d.feedback_id=f.id AND d.action IN ('WITHDRAW','RESTORE')
              ORDER BY d.decided_at DESC,d.id::text DESC LIMIT 1) AS withdrawn,
            (SELECT r.resolution='DISAGREE' FROM feedback_response r
              WHERE r.workspace_id=f.workspace_id AND r.feedback_id=f.id
              ORDER BY r.created_at DESC,r.id::text DESC LIMIT 1) AS disagreed,
            i.kind AS item_kind
          FROM feedback f
          LEFT JOIN item i ON i.id=f.item_id AND i.workspace_id=f.workspace_id
          LEFT JOIN item holder ON holder.id=i.parent_id AND holder.workspace_id=i.workspace_id
          LEFT JOIN pull_request_review pr ON i.kind='scm.pull_request_review' AND pr.id=i.source_id
          LEFT JOIN issue work ON work.id=CASE WHEN i.kind='scm.pull_request_review' THEN pr.pull_request_id
            WHEN i.kind IN ('scm.issue','scm.pull_request') THEN i.source_id END
          LEFT JOIN repository repo ON repo.id=CASE i.kind WHEN 'scm.repository' THEN i.source_id
            WHEN 'scm.file' THEN holder.source_id ELSE work.repository_id END
          LEFT JOIN identity_provider provider ON provider.id=repo.provider_id
          LEFT JOIN LATERAL (SELECT t.surface FROM feedback_copy c
            JOIN chat_message m ON m.id=c.chat_message_id AND m.workspace_id=c.workspace_id
            JOIN chat_thread t ON t.id=m.thread_id AND t.workspace_id=m.workspace_id
            WHERE c.workspace_id=f.workspace_id AND c.feedback_id=f.id AND c.kind='CHAT_TURN' AND c.state='POSTED'
            ORDER BY c.position,c.id LIMIT 1) conversation ON true
          WHERE f.workspace_id=:workspaceId
        )
        """;
    String FILTERED = LOCATED + """
        , requested AS (SELECT CAST(:filter AS jsonb) AS q), filtered AS (
          SELECT f.* FROM located f CROSS JOIN requested
          WHERE (q->>'reviewId' IS NULL OR f.review_id=CAST(q->>'reviewId' AS uuid))
            AND (q->>'itemId' IS NULL OR f.item_id=CAST(q->>'itemId' AS bigint))
            AND (q->>'itemKind' IS NULL OR f.item_kind=q->>'itemKind')
            AND (q->>'developerId' IS NULL OR f.developer_id=CAST(q->>'developerId' AS bigint))
            AND (q->>'destination' IS NULL OR q->'destination' @> to_jsonb(f.destination))
            AND (q->>'state' IS NULL OR q->'state' @> to_jsonb(f.state))
            AND (q->>'stopReason' IS NULL OR q->'stopReason' @> to_jsonb(f.stop_reason))
            AND (q->>'location' IS NULL OR f.location=q->>'location')
            AND (q->>'withdrawn' IS NULL OR COALESCE(f.withdrawn,false)=CAST(q->>'withdrawn' AS boolean))
            AND (q->>'disagreed' IS NULL OR COALESCE(f.disagreed,false)=CAST(q->>'disagreed' AS boolean))
            AND (q->>'from' IS NULL OR f.created_at>=CAST(q->>'from' AS timestamptz))
            AND (q->>'to' IS NULL OR f.created_at<CAST(q->>'to' AS timestamptz))
            AND ((q->>'practiceSlug' IS NULL AND q->>'groupSlug' IS NULL) OR EXISTS (
              SELECT 1 FROM feedback_basis b JOIN result r ON r.id=b.result_id AND r.workspace_id=b.workspace_id
              JOIN practice p ON p.id=r.practice_id AND p.workspace_id=r.workspace_id
              LEFT JOIN practice_group g ON g.id=p.group_id AND g.workspace_id=p.workspace_id
              WHERE b.feedback_id=f.id AND b.workspace_id=f.workspace_id
                AND (q->>'practiceSlug' IS NULL OR p.slug=q->>'practiceSlug')
                AND (q->>'groupSlug' IS NULL OR g.slug=q->>'groupSlug')))
        )
        """;

    @Query(value = """
        SELECT m.thread_id FROM feedback_copy c
        JOIN chat_message m ON m.id=c.chat_message_id AND m.workspace_id=c.workspace_id
        JOIN chat_thread t ON t.id=m.thread_id AND t.workspace_id=m.workspace_id
        JOIN feedback f ON f.id=c.feedback_id AND f.workspace_id=c.workspace_id
        WHERE c.workspace_id=:workspaceId AND c.feedback_id=:feedbackId
          AND c.kind='CHAT_TURN' AND c.posted_at IS NOT NULL
          AND t.account_id=f.developer_id AND f.destination='CONVERSATION'
        ORDER BY c.position LIMIT 1
        """, nativeQuery = true)
    @Nullable
    UUID threadId(@Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Query(value = """
        SELECT EXISTS(SELECT 1 FROM membership m JOIN account a ON a.id=m.account_id
          WHERE m.workspace_id=:workspaceId AND m.account_id=:accountId
            AND m.left_at IS NULL AND a.status IN ('ACTIVE','UNCLAIMED'))
        """, nativeQuery = true)
    boolean member(@Param("workspaceId") long workspaceId, @Param("accountId") long accountId);

    @Query(
            value = "SELECT feedback_content_digest(id) FROM feedback WHERE"
                    + " workspace_id=:workspaceId AND id=:feedbackId",
            nativeQuery = true)
    Optional<String> contentDigest(@Param("workspaceId") long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Query(value = """
        SELECT feedback_package_digest(ARRAY(
          SELECT value FROM jsonb_array_elements_text(CAST(:digests AS jsonb))
            WITH ORDINALITY AS pieces(value, position)
          ORDER BY position))
        """, nativeQuery = true)
    String orderedPackageDigest(@Param("digests") String pieceDigestsJson);

    @Query(value = FILTERED + """
        SELECT f.* FROM filtered f CROSS JOIN requested
        ORDER BY CASE WHEN q->>'sort'='OLDEST' THEN f.created_at END ASC,
          CASE WHEN q->>'sort'='OLDEST' THEN f.id END ASC,
          f.created_at DESC,f.id DESC
        """, countQuery = FILTERED + "SELECT count(*) FROM filtered", nativeQuery = true)
    Page<Feedback> page(@Param("workspaceId") long workspaceId, @Param("filter") String filter, Pageable pageable);

    @Query(value = FILTERED + """
        SELECT CASE WHEN :byLocation THEN location ELSE stop_reason END AS reason,count(*) AS count
        FROM filtered WHERE :byLocation OR stop_reason IS NOT NULL
        GROUP BY CASE WHEN :byLocation THEN location ELSE stop_reason END
        ORDER BY reason
        """, nativeQuery = true)
    List<CountRow> counts(
            @Param("workspaceId") long workspaceId,
            @Param("filter") String filter,
            @Param("byLocation") boolean byLocation);

    interface CountRow {
        String getReason();

        int getCount();
    }

    String ISSUE_RECEIPTS = """
        SELECT i.source_id AS issueId,c.external_ref AS externalRef,c.external_url AS url
        FROM feedback_copy c JOIN feedback f ON f.id=c.feedback_id AND f.workspace_id=c.workspace_id
        JOIN item i ON i.id=f.item_id AND i.workspace_id=f.workspace_id
        JOIN item parent ON parent.id=i.parent_id AND parent.workspace_id=i.workspace_id
        WHERE i.kind=:issueKind AND c.kind=:summaryKind AND c.state IN (:states)
          AND c.external_ref IS NOT NULL
        """;

    @WorkspaceAgnostic("Provider-ingest provenance for the same native issue across its monitoring workspaces")
    @Query(value = ISSUE_RECEIPTS + " AND i.source_id=:issueId", nativeQuery = true)
    List<IssueReceipt> issueReceipts(
            @Param("issueId") long issueId,
            @Param("issueKind") String issueKind,
            @Param("summaryKind") String summaryKind,
            @Param("states") List<String> states);

    @Query(
            value = ISSUE_RECEIPTS + " AND i.workspace_id=:workspaceId AND parent.source_id=:repositoryId",
            nativeQuery = true)
    List<IssueReceipt> repositoryReceipts(
            @Param("workspaceId") long workspaceId,
            @Param("repositoryId") long repositoryId,
            @Param("issueKind") String issueKind,
            @Param("summaryKind") String summaryKind,
            @Param("states") List<String> states);

    interface IssueReceipt {
        long getIssueId();

        String getExternalRef();

        @Nullable
        String getUrl();
    }

    @Query(value = """
        WITH receipts AS (
          SELECT c.external_ref,c.external_url FROM feedback_copy c
          JOIN feedback f ON f.id=c.feedback_id AND f.workspace_id=c.workspace_id
          JOIN item i ON i.id=f.item_id AND i.workspace_id=f.workspace_id
          WHERE i.workspace_id=:workspaceId AND i.kind=:pullRequestKind AND i.source_id=:pullRequestId
            AND c.state IN (:states) AND c.external_ref IS NOT NULL
        ), mirrored AS (
          SELECT false AS inline,c.native_id,'issuecomment-' AS anchor,ip.type AS provider
          FROM issue_comment c JOIN identity_provider ip ON ip.id=c.provider_id
          JOIN issue work ON work.id=c.issue_id AND work.provider_id=c.provider_id
          WHERE work.id=:pullRequestId AND work.issue_type='PULL_REQUEST'
          UNION ALL
          SELECT true,c.native_id,'discussion_r',ip.type FROM pull_request_review_comment c
          JOIN identity_provider ip ON ip.id=c.provider_id
          JOIN issue work ON work.id=c.pull_request_id AND work.provider_id=c.provider_id
          WHERE work.id=:pullRequestId AND work.issue_type='PULL_REQUEST'
        )
        SELECT m.inline,m.native_id AS nativeId FROM mirrored m WHERE EXISTS (
          SELECT 1 FROM receipts r WHERE (m.provider='GITLAB' AND r.external_ref IN (
            'gid://gitlab/Note/'||m.native_id,'gid://gitlab/DiffNote/'||m.native_id,CAST(m.native_id AS text)))
          OR (m.provider='GITHUB' AND split_part(r.external_url,'#',2)=m.anchor||m.native_id))
        """, nativeQuery = true)
    List<CommentReceipt> pullRequestReceipts(
            @Param("workspaceId") long workspaceId,
            @Param("pullRequestId") long pullRequestId,
            @Param("pullRequestKind") String pullRequestKind,
            @Param("states") List<String> states);

    interface CommentReceipt {
        boolean getInline();

        long getNativeId();
    }

    @Query(value = LOCATED + "SELECT id,location FROM located WHERE id IN (:ids)", nativeQuery = true)
    List<LocationRow> locations(@Param("workspaceId") long workspaceId, @Param("ids") Collection<UUID> ids);

    interface LocationRow {
        UUID getId();

        FeedbackLocation getLocation();
    }
}
