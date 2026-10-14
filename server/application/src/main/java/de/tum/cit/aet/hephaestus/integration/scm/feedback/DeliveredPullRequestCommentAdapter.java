package de.tum.cit.aet.hephaestus.integration.scm.feedback;

import de.tum.cit.aet.hephaestus.integration.core.spi.DeliveredPullRequestCommentLookup;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveredPullRequestCommentAdapter implements DeliveredPullRequestCommentLookup {
    private final JdbcClient jdbc;

    @Override
    public CommentIds findForPullRequest(long workspaceId, long pullRequestId) {
        var comments = jdbc.sql("""
            WITH receipts AS (
                SELECT c.external_ref, c.external_url FROM feedback_copy c
                JOIN feedback f ON f.id = c.feedback_id AND f.workspace_id = c.workspace_id
                JOIN item i ON i.id = f.item_id AND i.workspace_id = f.workspace_id
                WHERE i.workspace_id = :workspaceId AND i.kind = 'scm.pull_request' AND i.source_id = :pullRequestId
                    AND c.state IN ('POSTED', 'RETRACTED') AND c.external_ref IS NOT NULL
            ), mirrored AS (
                SELECT false AS inline, c.native_id, 'issuecomment-' AS anchor, ip.type AS provider
                FROM issue_comment c JOIN identity_provider ip ON ip.id = c.provider_id
                JOIN issue work ON work.id = c.issue_id AND work.provider_id = c.provider_id
                WHERE work.id = :pullRequestId AND work.issue_type = 'PULL_REQUEST'
                UNION ALL
                SELECT true, c.native_id, 'discussion_r', ip.type
                FROM pull_request_review_comment c JOIN identity_provider ip ON ip.id = c.provider_id
                JOIN issue work ON work.id = c.pull_request_id AND work.provider_id = c.provider_id
                WHERE work.id = :pullRequestId AND work.issue_type = 'PULL_REQUEST'
            )
            SELECT m.inline, m.native_id FROM mirrored m WHERE EXISTS (
                SELECT 1 FROM receipts r WHERE (m.provider = 'GITLAB' AND r.external_ref IN (
                    'gid://gitlab/Note/' || m.native_id, 'gid://gitlab/DiffNote/' || m.native_id, CAST(m.native_id AS text)))
                OR (m.provider = 'GITHUB' AND split_part(r.external_url, '#', 2) = m.anchor || m.native_id))
            """)
                .param("workspaceId", workspaceId)
                .param("pullRequestId", pullRequestId)
                .query((row, index) -> new Comment(row.getBoolean("inline"), row.getLong("native_id")))
                .list();
        var regular = new HashSet<Long>();
        var inline = new HashSet<Long>();
        comments.forEach(comment -> (comment.inline() ? inline : regular).add(comment.id()));
        return new CommentIds(Set.copyOf(regular), Set.copyOf(inline));
    }

    private record Comment(boolean inline, long id) {}
}
