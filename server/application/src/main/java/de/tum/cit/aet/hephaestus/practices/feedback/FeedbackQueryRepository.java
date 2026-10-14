package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@ConditionalOnCaptureRole
@Transactional(readOnly = true)
public class FeedbackQueryRepository {
    private final JdbcClient jdbc;

    public @Nullable UUID threadId(long workspaceId, UUID feedbackId) {
        return jdbc.sql("SELECT m.thread_id FROM feedback_copy c JOIN chat_message m ON"
                        + " m.id=c.chat_message_id AND m.workspace_id=c.workspace_id JOIN"
                        + " chat_thread t ON t.id=m.thread_id AND t.workspace_id=m.workspace_id"
                        + " JOIN feedback f ON f.id=c.feedback_id AND"
                        + " f.workspace_id=c.workspace_id WHERE c.workspace_id=:workspaceId AND"
                        + " c.feedback_id=:feedbackId AND c.kind='CHAT_TURN' AND c.posted_at IS"
                        + " NOT NULL AND t.account_id=f.developer_id AND"
                        + " f.destination='CONVERSATION' ORDER BY c.position LIMIT 1")
                .param("workspaceId", workspaceId)
                .param("feedbackId", feedbackId)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    public boolean member(long workspaceId, long accountId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM membership m JOIN account a ON"
                        + " a.id=m.account_id WHERE m.workspace_id=:workspaceId AND"
                        + " m.account_id=:accountId AND m.left_at IS NULL AND a.status IN"
                        + " ('ACTIVE','UNCLAIMED'))")
                .param("workspaceId", workspaceId)
                .param("accountId", accountId)
                .query(Boolean.class)
                .single();
    }

    public Optional<String> conversationSurface(long workspaceId, UUID feedbackId) {
        return jdbc.sql("""
            SELECT t.surface FROM feedback_copy c JOIN chat_message m ON m.id=c.chat_message_id AND m.workspace_id=c.workspace_id
            JOIN chat_thread t ON t.id=m.thread_id AND t.workspace_id=m.workspace_id
            WHERE c.workspace_id=:workspaceId AND c.feedback_id=:id AND c.kind='CHAT_TURN' AND c.state='POSTED'
            ORDER BY c.position,c.id LIMIT 1
            """)
                .param("workspaceId", workspaceId)
                .param("id", feedbackId)
                .query(String.class)
                .optional();
    }

    public List<Long> itemIds(long workspaceId, String kind) {
        return jdbc.sql("SELECT id FROM item WHERE" + " workspace_id=:workspaceId AND" + " kind=:kind")
                .param("workspaceId", workspaceId)
                .param("kind", kind)
                .<Long>query((rs, rowNum) -> rs.getLong("id"))
                .list();
    }

    public String contentDigest(long workspaceId, UUID feedbackId) {
        return jdbc.sql("SELECT feedback_content_digest(id) FROM feedback WHERE"
                        + " workspace_id=:workspaceId AND id=:id")
                .param("workspaceId", workspaceId)
                .param("id", feedbackId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
    }

    public String orderedPackageDigest(List<String> pieces) {
        if (pieces.isEmpty())
            return jdbc.sql("SELECT feedback_package_digest(ARRAY[]::text[])")
                    .query(String.class)
                    .single();
        return jdbc.sql("SELECT feedback_package_digest(ARRAY[:digests]::text[])")
                .param("digests", pieces)
                .query(String.class)
                .single();
    }
}
