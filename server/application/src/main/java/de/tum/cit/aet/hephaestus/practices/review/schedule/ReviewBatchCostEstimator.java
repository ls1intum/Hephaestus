package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewBatchCostEstimator {
    private final JdbcClient jdbc;
    private final ReviewBatchProperties properties;
    private final Clock clock;

    public @Nullable BigDecimal meanCostPerReviewUsd(long ws, String itemKind) {
        return jdbc.sql("""
            WITH costs AS (SELECT q.id,sum(u.cost_usd) AS cost,bool_or(u.pricing_state='UNPRICED') AS unpriced
              FROM review q JOIN item i ON i.id=q.item_id AND i.workspace_id=q.workspace_id
              JOIN llm_usage_event u ON u.review_id=q.id AND u.workspace_id=q.workspace_id
              WHERE q.workspace_id=:ws AND i.kind=:kind AND q.status IN ('COMPLETE','PARTIAL') AND q.completed_at>=:since
              GROUP BY q.id)
            SELECT CASE WHEN bool_or(unpriced) THEN NULL ELSE avg(cost) END FROM costs
            """)
                .param("ws", ws)
                .param("kind", itemKind)
                .param("since", Timestamp.from(clock.instant().minus(properties.costHistoryWindow())))
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    public @Nullable BigDecimal estimateTotalUsd(long ws, String itemKind, int count) {
        var mean = meanCostPerReviewUsd(ws, itemKind);
        return mean == null ? null : mean.multiply(BigDecimal.valueOf(count));
    }
}
