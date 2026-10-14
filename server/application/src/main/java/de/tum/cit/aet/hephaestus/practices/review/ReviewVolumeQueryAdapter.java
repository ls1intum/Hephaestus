package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewVolumeQuery;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
class ReviewVolumeQueryAdapter implements PracticeReviewVolumeQuery {
    private final JdbcClient jdbc;

    @Override
    public int countSince(long workspaceId, Instant since) {
        return Math.toIntExact(jdbc.sql("SELECT count(*) FROM review WHERE workspace_id=:ws AND created_at>=:since")
                .param("ws", workspaceId)
                .param("since", Timestamp.from(since))
                .query((rs, n) -> rs.getLong(1))
                .single());
    }
}
