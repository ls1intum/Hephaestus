package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.MonitoredRepositoryQuery;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewCoverageService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewBatchScopeRepository {
    private final JdbcClient jdbc;
    private final PracticeReviewCoverageService coverage;
    private final WorkspaceRepository workspaces;
    private final MonitoredRepositoryQuery monitored;

    public List<Long> select(long ws, String kind, @Nullable Instant from, @Nullable Instant to, int limit) {
        if (kind.equals("scm.repository")) {
            var workspace = workspaces.findById(ws).orElseThrow();
            return monitored.repositories(ws).keySet().stream()
                    .sorted()
                    .filter(id -> coverage.admitsRepositoryItem(workspace, id))
                    .limit(limit)
                    .toList();
        }
        String window = from == null
                ? ""
                : " AND EXISTS(SELECT 1 FROM item_event e WHERE e.workspace_id=i.workspace_id AND e.item_id=i.id AND"
                        + " e.occurred_at>=:from AND e.occurred_at<:to)";
        var params = new LinkedHashMap<String, Object>();
        params.put("ws", ws);
        params.put("kind", kind);
        params.put("limit", limit);
        if (from != null) {
            params.put("from", from);
            params.put("to", Objects.requireNonNull(to));
        }
        return jdbc.sql("SELECT i.id FROM item i WHERE i.workspace_id=:ws AND i.kind=:kind AND i.removed_at IS NULL"
                        + window + " ORDER BY i.id LIMIT :limit")
                .params(params)
                .query((rs, n) -> rs.getLong(1))
                .list();
    }

    public List<Long> next(long ws, UUID batch, @Nullable Long cursor, int limit) {
        return jdbc.sql("SELECT item_id FROM review_batch_target WHERE workspace_id=:ws AND batch_id=:batch AND"
                        + " item_id>:cursor ORDER BY item_id LIMIT :limit")
                .param("ws", ws)
                .param("batch", batch)
                .param("cursor", cursor == null ? 0 : cursor)
                .param("limit", limit)
                .query((rs, n) -> rs.getLong(1))
                .list();
    }
}
