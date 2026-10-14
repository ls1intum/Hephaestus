package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.event.ScmMirrorErasedEvent;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyCleanupCustody;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class PracticesScmMirrorErasureListener {
    private final JdbcClient jdbc;
    private final FeedbackCopyCleanupCustody custody;
    private final ScanReportService reports;

    @EventListener
    @Transactional
    public void onScmMirrorErased(ScmMirrorErasedEvent event) {
        long ws = event.workspaceId();
        String ids = "SELECT r.id FROM result r JOIN item i ON i.id=r.item_id AND i.workspace_id=r.workspace_id WHERE"
                + " r.workspace_id=:ws AND i.kind LIKE 'scm.%'";
        custody.requireAcknowledged(
                ws,
                jdbc.sql("SELECT DISTINCT f.id FROM feedback f JOIN feedback_basis b ON b.feedback_id=f.id AND"
                                + " b.workspace_id=f.workspace_id WHERE f.workspace_id=:ws AND b.result_id IN ("
                                + ids + ")")
                        .param("ws", ws)
                        .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                        .list());
        reports.eraseResultReferences(
                ws,
                jdbc.sql(ids)
                        .param("ws", ws)
                        .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                        .list());
        jdbc.sql("DELETE FROM feedback f WHERE f.workspace_id=:ws AND EXISTS(SELECT 1 FROM feedback_basis b"
                        + " WHERE b.workspace_id=f.workspace_id AND b.feedback_id=f.id AND b.result_id IN ("
                        + ids + "))")
                .param("ws", ws)
                .update();
        jdbc.sql("DELETE FROM review_item WHERE workspace_id=:ws AND result_id IN (" + ids + ")")
                .param("ws", ws)
                .update();
        jdbc.sql("DELETE FROM result WHERE workspace_id=:ws AND id IN (" + ids + ")")
                .param("ws", ws)
                .update();
    }
}
