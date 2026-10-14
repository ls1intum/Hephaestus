package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Component;

/** Names only native rows reachable through this workspace's authoritative item index. */
@Component
@RequiredArgsConstructor
public class ScmItemLabelQuery {
    private final JdbcTemplate jdbc;
    private static final String LABELS = """
        WITH selected AS (SELECT * FROM item WHERE workspace_id=? AND id=ANY(?))
        SELECT idx.id,
          CASE idx.kind WHEN 'scm.repository' THEN repository.name_with_owner
            WHEN 'scm.file' THEN idx.path WHEN 'scm.pull_request_review' THEN 'Review of '||work.title
            ELSE work.title END AS title,
          CASE idx.kind WHEN 'scm.repository' THEN 'repository' WHEN 'scm.file' THEN 'file'
            WHEN 'scm.pull_request_review' THEN 'review' WHEN 'scm.issue' THEN 'issue'
            ELSE CASE provider.type WHEN 'GITLAB' THEN 'merge request' ELSE 'pull request' END END AS noun,
          work.number, repository.name_with_owner AS container, provider.type AS provider_type,
          CASE WHEN idx.removed_at IS NOT NULL OR work.deleted_at IS NOT NULL THEN NULL
            WHEN idx.kind='scm.repository' THEN repository.html_url
            WHEN idx.kind='scm.file' THEN NULL
            WHEN idx.kind='scm.pull_request_review' THEN review.html_url
            ELSE work.html_url END AS url
        FROM selected idx
        LEFT JOIN item holder ON holder.workspace_id=idx.workspace_id AND holder.id=idx.parent_id
        LEFT JOIN pull_request_review review ON idx.kind='scm.pull_request_review' AND review.id=idx.source_id
        LEFT JOIN issue work ON work.id=CASE WHEN idx.kind='scm.pull_request_review' THEN review.pull_request_id
          WHEN idx.kind IN ('scm.issue','scm.pull_request') THEN idx.source_id END
        JOIN repository ON repository.id=CASE idx.kind WHEN 'scm.repository' THEN idx.source_id
          WHEN 'scm.file' THEN holder.source_id ELSE work.repository_id END
        JOIN identity_provider provider ON provider.id=repository.provider_id
        WHERE provider.type IN ('GITHUB','GITLAB') AND (
          (idx.kind='scm.issue' AND work.issue_type='ISSUE')
          OR (idx.kind='scm.pull_request' AND work.issue_type='PULL_REQUEST')
          OR (idx.kind='scm.pull_request_review' AND holder.kind='scm.pull_request' AND holder.source_id=review.pull_request_id)
          OR idx.kind='scm.repository' OR (idx.kind='scm.file' AND holder.kind='scm.repository'))
        """;

    public Map<Long, ItemLabel> labels(long workspaceId, Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, ItemLabel> labels = new HashMap<>();
        jdbc.query(
                LABELS,
                rs -> {
                    long id = rs.getLong("id");
                    labels.put(
                            id,
                            new ItemLabel(
                                    id,
                                    Objects.requireNonNull(rs.getString("title")),
                                    Objects.requireNonNull(rs.getString("noun")),
                                    rs.getObject("number", Integer.class),
                                    rs.getString("container"),
                                    rs.getString("url"),
                                    IntegrationKind.valueOf(Objects.requireNonNull(rs.getString("provider_type")))));
                },
                workspaceId,
                new SqlArrayValue("bigint", ids.toArray()));
        return Map.copyOf(labels);
    }
}
