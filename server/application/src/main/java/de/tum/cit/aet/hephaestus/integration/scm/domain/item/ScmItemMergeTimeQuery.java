package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemMergeTimeQuery;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Retained item identities scope actual provider merge facts without preparing a new capture. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ScmItemMergeTimeQuery implements ItemMergeTimeQuery {
    private final ScmItemAccess items;
    private final JdbcTemplate jdbc;
    private final RepositoryRepository repositories;
    private final ScmRepositoryAccess access;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Instant> mergedAt(long workspaceId, List<ItemRef> refs) {
        if (workspaceId <= 0 || refs.stream().anyMatch(ref -> ref.workspaceId() != workspaceId))
            throw new ItemCaptureException(
                    ItemCaptureCode.ITEM_NOT_VISIBLE, "Item does not belong to the requested workspace");
        var supported = refs.stream()
                .filter(ref -> ref.kind().equals(ScmItemEvents.PULL_REQUEST)
                        || ref.kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW))
                .distinct()
                .toList();
        if (supported.isEmpty()) return Map.of();
        items.requireAll(workspaceId, supported);
        var rows = jdbc.query(
                """
                SELECT i.id, work.id AS pull_id, work.issue_type, work.is_merged, work.merged_at, work.repository_id,
                    repository_item.id AS repository_item_id
                FROM item i
                LEFT JOIN item parent ON parent.id=i.parent_id AND parent.workspace_id=i.workspace_id
                LEFT JOIN pull_request_review review ON i.kind='scm.pull_request_review'
                    AND review.id=i.source_id AND parent.kind='scm.pull_request'
                    AND parent.source_id=review.pull_request_id
                LEFT JOIN issue work ON work.id=CASE WHEN i.kind='scm.pull_request' THEN i.source_id
                    WHEN i.kind='scm.pull_request_review' THEN review.pull_request_id END
                    AND (review.id IS NULL OR review.provider_id=work.provider_id)
                LEFT JOIN item repository_item ON repository_item.workspace_id=i.workspace_id
                    AND repository_item.kind='scm.repository' AND repository_item.source_id=work.repository_id
                    AND repository_item.id=CASE WHEN i.kind='scm.pull_request' THEN i.parent_id ELSE parent.parent_id END
                WHERE i.workspace_id=? AND i.id=ANY(?)
                """,
                (rs, row) -> {
                    var mergedAt = rs.getTimestamp("merged_at");
                    return new MergeFact(
                            rs.getLong("id"),
                            rs.getObject("pull_id", Long.class),
                            rs.getString("issue_type"),
                            rs.getObject("repository_item_id", Long.class),
                            rs.getBoolean("is_merged"),
                            mergedAt == null ? null : mergedAt.toInstant(),
                            rs.getObject("repository_id", Long.class));
                },
                workspaceId,
                new SqlArrayValue(
                        "bigint", supported.stream().map(ItemRef::itemId).toArray()));
        if (rows.size() != supported.size()) throw unavailable();
        Map<Long, Instant> result = new HashMap<>();
        var authorizedRepositories = new HashSet<Long>();
        for (var row : rows) {
            if (row.pullId() == null || row.repositoryItemId() == null || !"PULL_REQUEST".equals(row.issueType()))
                throw unavailable();
            Long repositoryId = row.repositoryId();
            if (repositoryId == null) throw unavailable();
            if (authorizedRepositories.add(repositoryId))
                access.authorize(
                        workspaceId,
                        repositories.findById(repositoryId).orElseThrow(ScmItemMergeTimeQuery::unavailable));
            Instant at = row.mergedAt();
            if (at != null) result.put(row.itemId(), at);
            else if (row.merged()) throw unavailable();
        }
        return Map.copyOf(result);
    }

    private static ItemCaptureException unavailable() {
        return new ItemCaptureException(ItemCaptureCode.SOURCE_NOT_READY, "SCM merge timing facts are unavailable");
    }

    private record MergeFact(
            long itemId,
            @Nullable Long pullId,
            @Nullable String issueType,
            @Nullable Long repositoryItemId,
            boolean merged,
            @Nullable Instant mergedAt,
            @Nullable Long repositoryId) {}
}
