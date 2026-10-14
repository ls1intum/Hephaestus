package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Generic parent reviews for tests of runtime leases and retention, without executing a practice. */
public final class RuntimeJobTestData {
    private RuntimeJobTestData() {}

    public static void initialize(ReviewJob job, long workspaceId) {
        if (job.getId() == null) job.setId(UUID.randomUUID());
        job.setWorkspaceId(workspaceId);
        if (job.getReviewId() == null) job.setReviewId(UUID.randomUUID());
        if (job.getAvailableAt() == null) job.setAvailableAt(Instant.now());
        if (job.getInput() == null)
            job.setInput(new ReviewJobInput(1, List.of(new ReviewAssignmentId(job.getReviewId(), 1, 1))));
    }

    public static ReviewJob persistParent(JdbcTemplate jdbc, ReviewJob job) {
        Boolean present = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM review WHERE id=? AND workspace_id=?)",
                Boolean.class,
                job.getReviewId(),
                job.getWorkspaceId());
        if (Boolean.TRUE.equals(present)) return job;
        long sourceId = (job.getReviewId().getLeastSignificantBits() & Long.MAX_VALUE) | 1;
        Long itemId = jdbc.queryForObject(
                "INSERT INTO item(workspace_id,kind,source_id,created_at) VALUES (?, 'test.item', ?, now()) RETURNING"
                        + " id",
                Long.class,
                job.getWorkspaceId(),
                sourceId);
        if (itemId == null) throw new IllegalStateException("Fixture item was not inserted");
        jdbc.update(
                "INSERT INTO review(id,workspace_id,item_id,kind,item_version,origin,ordered_at) VALUES (?, ?, ?,"
                        + " 'ITEM', 'test:1', 'REQUEST', now())",
                job.getReviewId(),
                job.getWorkspaceId(),
                itemId);
        job.setInput(new ReviewJobInput(itemId, List.of(new ReviewAssignmentId(job.getReviewId(), 1, itemId))));
        return job;
    }
}
