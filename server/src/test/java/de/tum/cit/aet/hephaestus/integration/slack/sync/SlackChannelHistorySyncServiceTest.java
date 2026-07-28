package de.tum.cit.aet.hephaestus.integration.slack.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slack.api.model.Message;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackTs;
import de.tum.cit.aet.hephaestus.integration.slack.events.SlackIngestService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService.HistoryPage;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * The consent-safety invariants of the nightly history reconciliation: fail-closed floors, per-request consent
 * re-checks, watermark-only-on-clean-completion, the event-path filter parity, and the hard "sync never tombstones"
 * rule.
 */
@Tag("unit")
class SlackChannelHistorySyncServiceTest extends BaseUnitTest {

    private static final long WS = 42L;
    private static final String TEAM = "T1";
    private static final String CHANNEL = "C1";
    private static final Instant ANNOUNCED = Instant.parse("2026-07-01T00:00:00Z");

    @Mock
    private SlackMonitoredChannelRepository monitoredChannelRepository;

    @Mock
    private SlackThreadRepository threadRepository;

    @Mock
    private SlackMessageService slackMessageService;

    @Mock
    private SlackIngestService ingestService;

    @Mock
    private ConnectionService connectionService;

    private SlackChannelHistorySyncService service;

    @BeforeEach
    void setUp() {
        service = new SlackChannelHistorySyncService(
            monitoredChannelRepository,
            threadRepository,
            slackMessageService,
            ingestService,
            connectionService,
            new SlackSyncProperties("0 0 4 * * *", 10, 15, Duration.ZERO, true, 5, true)
        );
        lenient().when(connectionService.findSlackNotificationConfig(WS)).thenReturn(Optional.empty());
        lenient()
            .when(monitoredChannelRepository.findConsentState(WS, CHANNEL))
            .thenReturn(Optional.of(ConsentState.ACTIVE));
    }

    private SlackMonitoredChannel channel(Instant announcedAt, String watermark) {
        SlackMonitoredChannel c = new SlackMonitoredChannel();
        c.setWorkspaceId(WS);
        c.setSlackTeamId(TEAM);
        c.setSlackChannelId(CHANNEL);
        c.setConsentState(ConsentState.ACTIVE);
        c.setConsentAnnouncedAt(announcedAt);
        c.setLastHistorySyncedTs(watermark);
        return c;
    }

    private void stubChannels(SlackMonitoredChannel... channels) {
        when(monitoredChannelRepository.findForHistorySync(WS, ConsentState.ACTIVE)).thenReturn(List.of(channels));
    }

    private static Message plain(String ts, String user, String text) {
        Message m = new Message();
        m.setTs(ts);
        m.setUser(user);
        m.setText(text);
        return m;
    }

    @Test
    void activeChannelWithoutAnnouncementStamp_isSkipped_failClosed() {
        stubChannels(channel(null, null));

        var summary = service.syncWorkspace(WS);

        assertThat(summary.skipped()).isEqualTo(1);
        verify(slackMessageService, never()).fetchHistoryPage(anyLong(), any(), any(), any(), any(), anyInt());
        verify(monitoredChannelRepository, never()).advanceHistoryWatermark(anyLong(), any(), any(), any());
    }

    @Test
    void cancellationBeforeFirstChannel_makesNoSlackRequest() {
        stubChannels(channel(ANNOUNCED, null));

        var summary = service.syncWorkspace(WS, () -> true);

        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.requestsUsed()).isZero();
        verify(slackMessageService, never()).fetchHistoryPage(anyLong(), any(), any(), any(), any(), anyInt());
        verify(monitoredChannelRepository, never()).advanceHistoryWatermark(anyLong(), any(), any(), any());
    }

    @Test
    void cancellationAfterSkippedChannel_countsAllRemainingChannelsAsSkipped() {
        stubChannels(channel(null, null), channel(ANNOUNCED, null));
        AtomicInteger checks = new AtomicInteger();

        var summary = service.syncWorkspace(WS, () -> checks.incrementAndGet() > 1);

        assertThat(summary.skipped()).isEqualTo(2);
        verify(slackMessageService, never()).fetchHistoryPage(anyLong(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void fetchFloor_isTheLatestOfAnnouncementAndWatermark() {
        String watermark = "1783000000.000000"; // after the announcement
        stubChannels(channel(ANNOUNCED, watermark));
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(), null));

        service.syncWorkspace(WS);

        verify(slackMessageService).fetchHistoryPage(eq(WS), eq(CHANNEL), eq(watermark), anyString(), any(), anyInt());
    }

    @Test
    void cleanWindow_ingestsThroughTheGatedStack_andAdvancesTheWatermark() {
        stubChannels(channel(ANNOUNCED, null));
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(plain("1783000001.000100", "U1", "hello")), null));

        var summary = service.syncWorkspace(WS);

        verify(ingestService).ingestChannelMessage(TEAM, CHANNEL, "1783000001.000100", null, "U1", "hello");
        ArgumentCaptor<String> latest = ArgumentCaptor.forClass(String.class);
        verify(monitoredChannelRepository).advanceHistoryWatermark(eq(WS), eq(CHANNEL), latest.capture(), any());
        assertThat(SlackTs.toEpochMicros(latest.getValue())).isNotNull();
        assertThat(summary.synced()).isEqualTo(1);
    }

    @Test
    void consentRevokedMidSync_stopsBeforeTheNextRequest_andNeverAdvances() {
        stubChannels(channel(ANNOUNCED, null));
        when(monitoredChannelRepository.findConsentState(WS, CHANNEL)).thenReturn(Optional.of(ConsentState.REVOKED));

        var summary = service.syncWorkspace(WS);

        assertThat(summary.skipped()).isEqualTo(1);
        verify(slackMessageService, never()).fetchHistoryPage(anyLong(), any(), any(), any(), any(), anyInt());
        verify(monitoredChannelRepository, never()).advanceHistoryWatermark(anyLong(), any(), any(), any());
    }

    @Test
    void budgetExhaustedMidWindow_doesNotAdvanceTheWatermark() {
        service = new SlackChannelHistorySyncService(
            monitoredChannelRepository,
            threadRepository,
            slackMessageService,
            ingestService,
            connectionService,
            new SlackSyncProperties("0 0 4 * * *", 1, 15, Duration.ZERO, false, 0, true)
        );
        stubChannels(channel(ANNOUNCED, null));
        // First page consumed the whole budget and points at a second page.
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(plain("1783000001.000100", "U1", "one")), "cursor-2"));

        var summary = service.syncWorkspace(WS);

        assertThat(summary.budgetExhausted()).isTrue();
        verify(monitoredChannelRepository, never()).advanceHistoryWatermark(anyLong(), any(), any(), any());
    }

    @Test
    void syncNeverTombstones() {
        stubChannels(channel(ANNOUNCED, null));
        Message deleted = plain("1783000002.000000", "U1", "gone");
        deleted.setSubtype("message_deleted");
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(deleted), null));

        service.syncWorkspace(WS);

        // Absence/tombstone semantics are the event path's job: reconciliation must never delete.
        verify(ingestService, never()).tombstoneMessage(any(), any(), any());
        verify(ingestService, never()).ingestChannelMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void eventPathFilters_applyToFetchedMessages() {
        stubChannels(channel(ANNOUNCED, null));
        Message bot = plain("1783000003.000000", null, "bot noise");
        bot.setBotId("B1");
        Message bareUpload = plain("1783000004.000000", "U1", "");
        bareUpload.setSubtype("file_share");
        Message joinNoise = plain("1783000005.000000", "U1", "joined");
        joinNoise.setSubtype("channel_join");
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(bot, bareUpload, joinNoise), null));

        service.syncWorkspace(WS);

        verify(ingestService, never()).ingestChannelMessage(any(), any(), any(), any(), any(), any());
        verify(ingestService, never()).editMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void editedMessage_routesThroughEditMessage() {
        stubChannels(channel(ANNOUNCED, null));
        Message edited = plain("1783000006.000000", "U1", "fixed wording");
        edited.setEdited(new Message.Edited());
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(edited), null));

        service.syncWorkspace(WS);

        verify(ingestService).editMessage(TEAM, CHANNEL, "1783000006.000000", null, "U1", "fixed wording");
        verify(ingestService, never()).ingestChannelMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void replyGap_fetchesRepliesThroughTheSameStack_skippingTheParentEcho() {
        stubChannels(channel(ANNOUNCED, null));
        Message parent = plain("1783000007.000000", "U1", "root");
        parent.setReplyCount(2);
        parent.setLatestReply("1783000009.000000");
        SlackThread thread = new SlackThread();
        thread.setLastTs("1783000008.000000"); // behind Slack's latest_reply → gap
        thread.setMessageCount(2);
        when(
            threadRepository.findByWorkspaceIdAndSlackChannelIdAndSlackThreadTs(WS, CHANNEL, "1783000007.000000")
        ).thenReturn(Optional.of(thread));
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(parent), null));
        Message parentEcho = plain("1783000007.000000", "U1", "root");
        Message reply = plain("1783000009.000000", "U2", "late reply");
        when(
            slackMessageService.fetchRepliesPage(
                eq(WS),
                eq(CHANNEL),
                eq("1783000007.000000"),
                anyString(),
                any(),
                anyInt()
            )
        ).thenReturn(new HistoryPage(List.of(parentEcho, reply), null));

        service.syncWorkspace(WS);

        verify(ingestService).ingestChannelMessage(TEAM, CHANNEL, "1783000009.000000", null, "U2", "late reply");
        // The parent itself is ingested exactly once (from the history page), not again from the replies echo.
        verify(ingestService).ingestChannelMessage(TEAM, CHANNEL, "1783000007.000000", null, "U1", "root");
    }

    @Test
    void repliesBudgetExhaustedMidPagination_doesNotAdvanceChannelWatermark() {
        service = new SlackChannelHistorySyncService(
            monitoredChannelRepository,
            threadRepository,
            slackMessageService,
            ingestService,
            connectionService,
            new SlackSyncProperties("0 0 4 * * *", 10, 15, Duration.ZERO, true, 1, true)
        );
        stubChannels(channel(ANNOUNCED, null));
        Message parent = plain("1783000007.000000", "U1", "root");
        parent.setReplyCount(2);
        when(
            threadRepository.findByWorkspaceIdAndSlackChannelIdAndSlackThreadTs(WS, CHANNEL, parent.getTs())
        ).thenReturn(Optional.empty());
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(parent), null));
        when(
            slackMessageService.fetchRepliesPage(eq(WS), eq(CHANNEL), eq(parent.getTs()), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(), "next"));

        var summary = service.syncWorkspace(WS);

        assertThat(summary.budgetExhausted()).isTrue();
        assertThat(summary.skipped()).isEqualTo(1);
        verify(monitoredChannelRepository, never()).advanceHistoryWatermark(anyLong(), any(), any(), any());
    }

    @Test
    void threadWithNoNewReplies_doesNotSpendARepliesRequest() {
        stubChannels(channel(ANNOUNCED, null));
        Message parent = plain("1783000010.000000", "U1", "root");
        parent.setReplyCount(1);
        parent.setLatestReply("1783000011.000000");
        SlackThread thread = new SlackThread();
        thread.setLastTs("1783000011.000000"); // aggregate already caught up
        thread.setMessageCount(2);
        when(
            threadRepository.findByWorkspaceIdAndSlackChannelIdAndSlackThreadTs(WS, CHANNEL, "1783000010.000000")
        ).thenReturn(Optional.of(thread));
        when(
            slackMessageService.fetchHistoryPage(eq(WS), eq(CHANNEL), anyString(), anyString(), any(), anyInt())
        ).thenReturn(new HistoryPage(List.of(parent), null));

        service.syncWorkspace(WS);

        verify(slackMessageService, never()).fetchRepliesPage(anyLong(), any(), any(), any(), any(), anyInt());
    }
}
