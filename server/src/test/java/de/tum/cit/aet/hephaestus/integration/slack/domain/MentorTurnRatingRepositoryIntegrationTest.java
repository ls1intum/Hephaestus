package de.tum.cit.aet.hephaestus.integration.slack.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * S5 rating-store integration tests (Testcontainers): a thumb persists as an append-only row, "latest wins" is a
 * recency read over those rows, and the workspace-scoped bulk delete (the fifth table folded into
 * {@code SlackWorkspacePurgeAdapter}) empties one workspace without touching another. Distinct workspace ids per
 * test give isolation without a clean-between step.
 */
class MentorTurnRatingRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final AtomicLong WS_SEQ = new AtomicLong(9_100_000L);

    @Autowired
    private MentorTurnRatingRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private MentorTurnRating rating(long workspaceId, long raterId, String ts, TurnRating verdict, Instant createdAt) {
        return MentorTurnRating.builder()
            .workspaceId(workspaceId)
            .raterUserId(raterId)
            .channelId("D9")
            .threadTs("100.0")
            .slackMessageTs(ts)
            .rating(verdict)
            .source(RatingSource.BUTTON)
            .createdAt(createdAt)
            .build();
    }

    @Test
    void thumb_persistsAsRow() {
        long ws = WS_SEQ.incrementAndGet();

        repository.save(rating(ws, 7L, "100.5", TurnRating.HELPFUL, Instant.now()));

        assertThat(repository.countByWorkspaceId(ws)).isEqualTo(1L);
    }

    @Test
    void latestWins_returnsTheNewestRowForTheTurn() {
        long ws = WS_SEQ.incrementAndGet();
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        repository.save(rating(ws, 7L, "100.5", TurnRating.HELPFUL, t0));
        repository.saveAndFlush(rating(ws, 7L, "100.5", TurnRating.UNHELPFUL, t0.plusSeconds(1)));

        MentorTurnRating current = repository
            .findFirstByWorkspaceIdAndRaterUserIdOrderByCreatedAtDescIdDesc(ws, 7L)
            .orElseThrow();

        assertThat(current.getRating()).isEqualTo(TurnRating.UNHELPFUL);
    }

    @Test
    void purgeDelete_emptiesOneWorkspaceOnly() {
        long wsA = WS_SEQ.incrementAndGet();
        long wsB = WS_SEQ.incrementAndGet();
        repository.save(rating(wsA, 7L, "100.5", TurnRating.HELPFUL, Instant.now()));
        repository.save(rating(wsB, 8L, "200.5", TurnRating.HELPFUL, Instant.now()));

        // The production purge runs this bulk delete inside the purge orchestrator's transaction
        // (see WorkspacePurgeIntegrationTest → workspaceLifecycleService.purgeWorkspace); mirror that
        // boundary here so the derived delete has an active EntityManager transaction.
        transactionTemplate.executeWithoutResult(status -> repository.deleteByWorkspaceId(wsA));

        assertThat(repository.countByWorkspaceId(wsA)).isZero();
        assertThat(repository.countByWorkspaceId(wsB)).isEqualTo(1L);
    }
}
