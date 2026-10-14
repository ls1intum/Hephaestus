package de.tum.cit.aet.hephaestus.practices.feedback.decision;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackContentDigest {
    private final JdbcClient jdbc;

    public String current(long workspaceId, UUID feedbackId) {
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
