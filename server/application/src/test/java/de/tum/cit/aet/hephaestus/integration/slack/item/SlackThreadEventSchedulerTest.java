package de.tum.cit.aet.hephaestus.integration.slack.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.integration.chat.item.ConversationThreadCandidate;
import de.tum.cit.aet.hephaestus.integration.chat.item.ThreadEvents;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.slack.conversation.SlackConversationCandidateSource;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequested;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class SlackThreadEventSchedulerTest extends BaseUnitTest {
    private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    private final SlackConversationCandidateSource candidates = mock(SlackConversationCandidateSource.class);
    private final ThreadItemSource source = mock(ThreadItemSource.class);
    private final ItemWriter items = mock(ItemWriter.class);
    private final SlackThreadRepository threads = mock(SlackThreadRepository.class);
    private final ReviewService reviews = mock(ReviewService.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ItemRef item = new ItemRef(7L, 41L, ThreadEvents.THREAD, 19L, null, null);
    private final ItemVersion version = new ItemVersion("frozen-version", null);

    private SlackThreadEventScheduler scheduler() {
        return new SlackThreadEventScheduler(
                candidates,
                source,
                items,
                threads,
                reviews,
                new TransactionTemplate(manager),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ItemSnapshot snapshot(ItemVersion frozen) {
        return new ItemSnapshot(
                item,
                frozen,
                new IntegrationRef(IntegrationKind.SLACK, 7L, "T1", 21L),
                Map.of(),
                List.of(),
                new ItemLabel(41L, "Thread", "thread", null, "Channel", null, IntegrationKind.SLACK));
    }

    @Test
    void settledThreadRecordsOneFrozenEventWithoutMovingItsWatermarkOrCloningParticipantReviews() {
        String last = (NOW.getEpochSecond() - 1200) + ".000000";
        var candidate = new ConversationThreadCandidate(
                7L, 19L, "C1", "Channel", "1690000000.000000", last, null, List.of(55L, 56L));
        when(candidates.settledCandidates(4)).thenReturn(List.of(candidate));
        when(candidates.liveTurnCount(7L, "C1", candidate.threadTs())).thenReturn(4L);
        when(candidates.liveTurnCountSince(7L, "C1", candidate.threadTs(), null))
                .thenReturn(4L);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(items.upsert(any())).thenReturn(item);
        when(source.current(item)).thenReturn(Optional.of(snapshot(version)));
        assertThat(scheduler().sweepNow()).isEqualTo(1);
        var captured = ArgumentCaptor.forClass(ItemEventInput.class);
        verify(items).record(captured.capture());
        assertThat(captured.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(captured.getValue().workspaceId()).isEqualTo(7L);
        assertThat(captured.getValue().itemId()).isEqualTo(41L);
        assertThat(captured.getValue().itemVersion()).isEqualTo("frozen-version");
        assertThat(captured.getValue().name()).isEqualTo(ThreadEvents.SETTLED);
        assertThat(captured.getValue().foundBy()).isEqualTo(FoundBy.SYNC);
        assertThat(captured.getValue().requestedById()).isNull();
        verify(candidates, never()).markReviewed(anyLong(), anyLong(), any());
        verify(reviews, never()).request(any());
    }

    @Test
    void watermarkMovesOnlyAfterAReviewOfTheSameFrozenVersionWasAccepted() {
        UUID reviewId = UUID.randomUUID();
        when(reviews.find(7L, reviewId))
                .thenReturn(Optional.of(new ReviewSnapshot(
                        reviewId, 7L, item, ReviewKind.ITEM, version, ReviewOrigin.EVENT, 1L, List.of())));
        when(source.current(item)).thenReturn(Optional.of(snapshot(version)));
        var thread = new SlackThread();
        thread.setLastTs("1700000001.000000");
        when(threads.findByWorkspaceIdAndId(7L, 19L)).thenReturn(Optional.of(thread));
        scheduler().onReviewRequested(new ReviewRequested(reviewId, 7L));
        verify(candidates).markReviewed(7L, 19L, "1700000001.000000");
    }

    @Test
    void aNewProviderVersionCannotAdvanceTheWatermarkForAnOlderAcceptedReview() {
        UUID reviewId = UUID.randomUUID();
        when(reviews.find(7L, reviewId))
                .thenReturn(Optional.of(new ReviewSnapshot(
                        reviewId, 7L, item, ReviewKind.ITEM, version, ReviewOrigin.EVENT, 1L, List.of())));
        when(source.current(item)).thenReturn(Optional.of(snapshot(new ItemVersion("new-version", null))));
        scheduler().onReviewRequested(new ReviewRequested(reviewId, 7L));
        verify(candidates, never()).markReviewed(anyLong(), anyLong(), any());
    }

    @Test
    void shouldNotCreateASchedulerWhenConversationIngestIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(SlackThreadEventScheduler.class)
                .withPropertyValues(
                        RuntimeRole.SERVER_PROPERTY + "=true",
                        "hephaestus.integration.slack.enabled=true",
                        "hephaestus.integration.slack.conversation-ingest.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SlackThreadEventScheduler.class);
                });
    }
}
