package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountAbsorptionContributor;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
@ConditionalOnServerRole
@RequiredArgsConstructor
@WorkspaceAgnostic("Moves product attribution for two globally locked, verified accounts during absorption")
public class ResultAccountAbsorptionContributor implements AccountAbsorptionContributor {
    private final JdbcClient jdbc;
    private final Clock clock;

    @Override
    @Transactional
    public void absorb(long claimedAccountId, long unclaimedAccountId) {
        jdbc.sql("SELECT id FROM review WHERE id IN(SELECT review_id FROM result WHERE developer_id IN"
                        + " (:claimed,:unclaimed)) ORDER BY id::text FOR UPDATE")
                .param("claimed", claimedAccountId)
                .param("unclaimed", unclaimedAccountId)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
        jdbc.sql("SELECT id FROM result WHERE developer_id IN (:claimed,:unclaimed) ORDER BY id::text FOR UPDATE")
                .param("claimed", claimedAccountId)
                .param("unclaimed", unclaimedAccountId)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
        jdbc.sql("""
            WITH ranked AS (
             SELECT r.id,ROW_NUMBER() OVER(PARTITION BY r.workspace_id,r.practice_id,r.item_id ORDER BY v.ordered_at DESC,r.recorded_at DESC,v.id::text DESC) AS ordinal
             FROM result r JOIN review v ON v.id=r.review_id AND v.workspace_id=r.workspace_id
             WHERE r.developer_id IN (:claimed,:unclaimed) AND r.ended_at IS NULL
            ) UPDATE result r SET ended_at=GREATEST(:at,r.recorded_at),end_reason='OUTRANKED'
            FROM ranked WHERE ranked.id=r.id AND ranked.ordinal>1
            """)
                .param("claimed", claimedAccountId)
                .param("unclaimed", unclaimedAccountId)
                .param("at", Timestamp.from(clock.instant()))
                .update();
        jdbc.sql("UPDATE result SET developer_id=:claimed WHERE developer_id=:unclaimed")
                .param("claimed", claimedAccountId)
                .param("unclaimed", unclaimedAccountId)
                .update();
        jdbc.sql("UPDATE review_item SET detail=jsonb_set(detail,'{developerId}',to_jsonb(CAST(:claimed AS"
                        + " bigint))) WHERE detail->>'developerId'=:unclaimed")
                .param("claimed", claimedAccountId)
                .param("unclaimed", Long.toString(unclaimedAccountId))
                .update();
    }

    @Override
    public void finish(long claimedAccountId, long unclaimedAccountId) {
        if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM result WHERE developer_id=:unclaimed)")
                .param("unclaimed", unclaimedAccountId)
                .query(Boolean.class)
                .single()) throw new IllegalStateException("Product attribution was not fully absorbed.");
    }
}
