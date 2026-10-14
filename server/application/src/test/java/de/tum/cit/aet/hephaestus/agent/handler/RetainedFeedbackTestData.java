package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RetainedFeedbackTestData {
    private RetainedFeedbackTestData() {}

    public static Feedback onWork(
            JdbcTemplate jdbc, FeedbackRepository feedback, ItemRef item, long developerId, FeedbackState state) {
        UUID reviewId = UUID.randomUUID();
        Instant at = Instant.now();
        jdbc.update(
                "INSERT INTO review(id,workspace_id,item_id,kind,item_version,origin,ordered_at,status,completed_at)"
                        + " VALUES (?,?,?,'ITEM','retained:one','BACKFILL',?,'COMPLETE',?)",
                reviewId,
                item.workspaceId(),
                item.itemId(),
                Timestamp.from(at),
                Timestamp.from(at));
        return feedback.saveAndFlush(Feedback.builder()
                .id(UUID.randomUUID())
                .workspaceId(item.workspaceId())
                .reviewId(reviewId)
                .itemId(item.itemId())
                .developerId(developerId)
                .destination(FeedbackDestination.ON_WORK)
                .state(state)
                .body("Retained feedback")
                .createdAt(at)
                .deliveredAt(at)
                .build());
    }

    public static FeedbackCopy posted(
            Feedback feedback, FeedbackCopyKind kind, int position, @Nullable String ref, @Nullable String url) {
        boolean anchored = kind == FeedbackCopyKind.LINE_NOTE || kind == FeedbackCopyKind.FILE_NOTE;
        Instant at = Instant.now();
        return FeedbackCopy.builder()
                .id(UUID.randomUUID())
                .workspaceId(feedback.getWorkspaceId())
                .feedbackId(feedback.getId())
                .kind(kind)
                .position(position)
                .state(FeedbackCopyState.POSTED)
                .externalRef(ref)
                .externalUrl(url)
                .createdAt(at)
                .postedAt(at)
                .body(anchored ? "Retained note" : null)
                .path(anchored ? "src/A.java" : null)
                .startLine(anchored ? 4 : null)
                .endLine(anchored ? 4 : null)
                .side(anchored ? CitationSide.NEW : null)
                .build();
    }
}
