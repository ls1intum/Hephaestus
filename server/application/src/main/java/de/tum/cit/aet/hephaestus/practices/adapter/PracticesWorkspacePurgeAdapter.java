package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyCleanupCustody;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class PracticesWorkspacePurgeAdapter implements WorkspacePurgeContributor {
    private final JdbcClient jdbc;
    private final FeedbackCopyCleanupCustody custody;

    @Override
    public void deleteWorkspaceData(Long ws) {
        custody.requireAcknowledged(
                ws,
                jdbc.sql("SELECT id FROM feedback WHERE workspace_id=:ws ORDER BY id")
                        .param("ws", ws)
                        .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                        .list());
        jdbc.sql("DELETE FROM feedback WHERE workspace_id=:ws").param("ws", ws).update();
        jdbc.sql("DELETE FROM review_item WHERE workspace_id=:ws")
                .param("ws", ws)
                .update();
        jdbc.sql("DELETE FROM result WHERE workspace_id=:ws").param("ws", ws).update();
        jdbc.sql("DELETE FROM review WHERE workspace_id=:ws").param("ws", ws).update();
        jdbc.sql("DELETE FROM review_batch WHERE workspace_id=:ws")
                .param("ws", ws)
                .update();
        jdbc.sql("DELETE FROM review_schedule WHERE workspace_id=:ws")
                .param("ws", ws)
                .update();
        jdbc.sql("SET CONSTRAINTS ALL DEFERRED").update();
        jdbc.sql("DELETE FROM practice_version WHERE workspace_id=:ws")
                .param("ws", ws)
                .update();
        jdbc.sql("DELETE FROM practice WHERE workspace_id=:ws").param("ws", ws).update();
        jdbc.sql("DELETE FROM practice_group WHERE workspace_id=:ws")
                .param("ws", ws)
                .update();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
