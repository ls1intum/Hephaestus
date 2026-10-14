package de.tum.cit.aet.hephaestus.integration.scm.feedback;

import de.tum.cit.aet.hephaestus.integration.core.spi.DeliveredIssueCommentLookup;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveredIssueCommentAdapter implements DeliveredIssueCommentLookup {
    private final JdbcClient jdbc;
    private static final String RECEIPTS = """
        SELECT i.source_id AS issue_id, c.external_ref, c.external_url
        FROM feedback_copy c JOIN feedback f ON f.id = c.feedback_id AND f.workspace_id = c.workspace_id
        JOIN item i ON i.id = f.item_id AND i.workspace_id = f.workspace_id
        JOIN item parent ON parent.id = i.parent_id AND parent.workspace_id = i.workspace_id
        WHERE i.kind = 'scm.issue' AND c.kind = 'SUMMARY' AND c.state IN ('POSTED', 'RETRACTED')
            AND c.external_ref IS NOT NULL
        """;

    @Override
    public List<DeliveredComment> findForIssue(long issueId) {
        return jdbc.sql(RECEIPTS + " AND i.source_id = :issueId")
                .param("issueId", issueId)
                .query((row, index) ->
                        new DeliveredComment(row.getString("external_ref"), row.getString("external_url")))
                .list();
    }

    @Override
    public Map<Long, List<DeliveredComment>> findForRepository(long workspaceId, long repositoryId) {
        return jdbc
                .sql(RECEIPTS + " AND i.workspace_id = :workspaceId AND parent.source_id = :repositoryId")
                .param("workspaceId", workspaceId)
                .param("repositoryId", repositoryId)
                .query((row, index) -> Map.entry(
                        row.getLong("issue_id"),
                        new DeliveredComment(row.getString("external_ref"), row.getString("external_url"))))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }
}
