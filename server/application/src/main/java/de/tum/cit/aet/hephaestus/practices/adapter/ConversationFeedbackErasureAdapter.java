package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyCleanupCustody;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanReportService;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ConversationFeedbackErasureAdapter implements ConversationFeedbackErasure {
    private final JdbcClient jdbc;
    private final FeedbackCopyCleanupCustody custody;
    private final ScanReportService reports;

    @Override
    @Transactional
    public int eraseForThreads(long ws, Collection<Long> threads) {
        if (threads.isEmpty()) return 0;
        return erase(ws, " AND i.source_id IN (:threads)", Map.of("threads", threads));
    }

    @Override
    @Transactional
    public int eraseAllConversationForWorkspace(long ws) {
        return erase(ws, "", Map.of());
    }

    @Override
    @Transactional
    public int eraseConversationFeedbackAboutUser(long ws, long account) {
        return erase(ws, " AND r.developer_id=:account", Map.of("account", account));
    }

    private int erase(long ws, String selection, Map<String, ?> extra) {
        var params = new LinkedHashMap<String, Object>();
        params.put("ws", ws);
        params.putAll(extra);
        String ids = "SELECT r.id FROM result r JOIN item i ON i.id=r.item_id AND i.workspace_id=r.workspace_id WHERE"
                + " r.workspace_id=:ws AND i.kind='chat.thread'"
                + selection;
        custody.requireAcknowledged(
                ws,
                jdbc.sql("SELECT DISTINCT f.id FROM feedback f JOIN feedback_basis b ON b.feedback_id=f.id AND"
                                + " b.workspace_id=f.workspace_id WHERE f.workspace_id=:ws AND b.result_id IN ("
                                + ids + ")")
                        .params(params)
                        .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                        .list());
        reports.eraseResultReferences(
                ws,
                jdbc.sql(ids)
                        .params(params)
                        .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                        .list());
        int feedback = jdbc.sql(
                        "DELETE FROM feedback f WHERE f.workspace_id=:ws AND EXISTS(SELECT 1 FROM feedback_basis b"
                                + " WHERE b.workspace_id=f.workspace_id AND b.feedback_id=f.id AND b.result_id IN ("
                                + ids + "))")
                .params(params)
                .update();
        jdbc.sql("DELETE FROM review_item WHERE workspace_id=:ws AND result_id IN (" + ids + ")")
                .params(params)
                .update();
        return feedback
                + jdbc.sql("DELETE FROM result WHERE workspace_id=:ws AND id IN (" + ids + ")")
                        .params(params)
                        .update();
    }
}
