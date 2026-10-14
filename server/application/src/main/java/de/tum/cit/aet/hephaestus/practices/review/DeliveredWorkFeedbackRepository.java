package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.practices.review.dto.DeliveredFeedbackPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.DeliveredWorkFeedbackCopyDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.DeliveredWorkFeedbackItemDTO;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnServerRole
@RequiredArgsConstructor
class DeliveredWorkFeedbackRepository {
    private final JdbcClient jdbc;

    List<DeliveredWorkFeedbackItemDTO> findDelivered(long ws, long developer, long item) {
        return jdbc.sql("""
            SELECT f.id,COALESCE(f.delivered_at,min(c.posted_at)) AS delivered_at
            FROM feedback f JOIN feedback_copy c ON c.feedback_id=f.id AND c.workspace_id=f.workspace_id
            WHERE f.workspace_id=:ws AND f.developer_id=:developer AND f.item_id=:item AND f.destination='ON_WORK'
              AND f.state<>'REPLACED' AND c.state='POSTED' AND c.posted_at IS NOT NULL
            GROUP BY f.id,f.delivered_at ORDER BY delivered_at DESC,f.id::text DESC LIMIT 50
            """)
                .param("ws", ws)
                .param("developer", developer)
                .param("item", item)
                .query((rs, n) -> {
                    var id = rs.getObject("id", UUID.class);
                    return new DeliveredWorkFeedbackItemDTO(
                            id,
                            rs.getObject("delivered_at", OffsetDateTime.class).toInstant(),
                            practices(ws, id),
                            copies(ws, id));
                })
                .list();
    }

    List<UUID> basis(long ws, UUID feedback) {
        return jdbc.sql("SELECT DISTINCT result_id FROM feedback_basis WHERE workspace_id=:ws AND feedback_id=:id")
                .param("ws", ws)
                .param("id", feedback)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
    }

    private List<DeliveredFeedbackPracticeDTO> practices(long ws, UUID feedback) {
        return jdbc.sql("""
            SELECT DISTINCT p.slug,v.name FROM feedback_basis b JOIN result r ON r.id=b.result_id AND r.workspace_id=b.workspace_id
              JOIN practice p ON p.id=r.practice_id AND p.workspace_id=r.workspace_id JOIN practice_version v ON v.id=r.practice_version_id AND v.workspace_id=r.workspace_id
            WHERE b.workspace_id=:ws AND b.feedback_id=:id ORDER BY p.slug
            """)
                .param("ws", ws)
                .param("id", feedback)
                .query((rs, n) -> new DeliveredFeedbackPracticeDTO(rs.getString("slug"), rs.getString("name")))
                .list();
    }

    private List<DeliveredWorkFeedbackCopyDTO> copies(long ws, UUID feedback) {
        return jdbc.sql("""
            SELECT id,kind,external_ref,external_url,path,start_line,end_line,side,posted_at FROM feedback_copy
            WHERE workspace_id=:ws AND feedback_id=:id AND state='POSTED' AND posted_at IS NOT NULL ORDER BY position,id::text
            """)
                .param("ws", ws)
                .param("id", feedback)
                .query((rs, n) -> new DeliveredWorkFeedbackCopyDTO(
                        rs.getObject("id", UUID.class),
                        FeedbackCopyKind.valueOf(rs.getString("kind")),
                        rs.getString("external_ref"),
                        rs.getString("external_url"),
                        rs.getString("path"),
                        rs.getObject("start_line", Integer.class),
                        rs.getObject("end_line", Integer.class),
                        rs.getString("side") == null ? null : CitationSide.valueOf(rs.getString("side")),
                        rs.getObject("posted_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
