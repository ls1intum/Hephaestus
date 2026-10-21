package de.tum.cit.aet.hephaestus.integration.slack.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.slack.api.model.Message;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.MarkdownBlock;
import de.tum.cit.aet.hephaestus.agent.mentor.turn.MentorTurnCompletion;
import de.tum.cit.aet.hephaestus.integration.core.sync.MutableClock;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackTs;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.mentor.MentorConversation;
import de.tum.cit.aet.hephaestus.mentor.MentorConversationService;
import de.tum.cit.aet.hephaestus.mentor.MentorEntry;
import de.tum.cit.aet.hephaestus.mentor.MentorEntryRepository;
import de.tum.cit.aet.hephaestus.mentor.MentorLedgerEvents;
import de.tum.cit.aet.hephaestus.mentor.MentorLedgerFixtures;
import de.tum.cit.aet.hephaestus.mentor.MentorLedgerNotifications;
import de.tum.cit.aet.hephaestus.mentor.MentorSlackDelivery;
import de.tum.cit.aet.hephaestus.mentor.MentorTurn;
import de.tum.cit.aet.hephaestus.mentor.MentorTurnService;
import de.tum.cit.aet.hephaestus.practices.feedback.ConversationFeedbackRows;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.testconfig.ConversationReceipts;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Heph's Slack replies: what reaches Slack, once, and what the ledger records about it and the feedback it showed. The
 * test hands the projector each ledger notification itself. The projector works on the test's thread, its clock is the
 * test's, and its wake-ups wait until the test runs them.
 */
class SlackMentorProjectorIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String TEAM = "T1";
    private static final String CHANNEL = "D9";
    private static final String SLACK_USER = "U1";
    private static final String BOT_USER = "UBOT";
    private static final String BOT_ID = "BHEPH";
    private static final String WORKER = "worker-slack";
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final String FEEDBACK = "Your pull request names the decision but not why it beat the other option.";

    @Autowired
    private MentorConversationService conversations;

    @Autowired
    private MentorTurnService turns;

    @Autowired
    private MentorSlackDelivery delivery;

    @Autowired
    private MentorEntryRepository entryRepository;

    @Autowired
    private MentorLedgerNotifications notifications;

    @Autowired
    private MentorTurnCompletion completion;

    @Autowired
    private JdbcTemplate jdbc;

    private final SlackMessageService slack = mock(SlackMessageService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
    private final ScheduledExecutorService timer = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> pendingWakeUp = mock(ScheduledFuture.class);
    private final List<Runnable> wakeUps = new ArrayList<>();

    private User developer;
    private Workspace workspace;
    private MentorConversation main;
    private SlackMentorProjector projector;
    private long developerAccount;
    private @Nullable UUID shownFeedback;

    @BeforeEach
    void setUpProjector() {
        developer = persistUser("slack-projector-developer");
        workspace =
                createWorkspace("slack-projector", "Slack Projector", "slack-projector", AccountType.ORG, developer);
        developerAccount = accountId(developer);
        main = conversations.findOrCreateMain(workspace.getId(), developerAccount);
        conversations.bindSlackIdentity(workspace.getId(), main.getId(), TEAM, SLACK_USER, CHANNEL);
        when(slack.isConnected(workspace.getId())).thenReturn(true);
        when(slack.resolveBot(workspace.getId()))
                .thenReturn(Optional.of(new SlackMessageService.BotIdentity(BOT_USER, BOT_ID)));
        when(timer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(call -> {
                    wakeUps.add(call.getArgument(0));
                    return pendingWakeUp;
                });
        projector = new SlackMentorProjector(
                notifications,
                delivery,
                completion,
                slack,
                new SlackMentorPoster(slack, delivery),
                clock,
                meters,
                new OnTheCallersThread(),
                timer);
    }

    private MentorTurnService.Submission ask(MentorConversation conversation, String text, String ts) {
        return turns.submit(
                workspace.getId(),
                conversation.getId(),
                new MentorTurnService.Question(MentorTurn.Surface.SLACK, text, "slack:T1:D9:" + ts, ts, null));
    }

    /** Runs each projection when it is asked for, so its effect is there when {@code turnChanged} returns. */
    private static final class OnTheCallersThread extends AbstractExecutorService {
        @Override
        public void execute(Runnable task) {
            task.run();
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    /** The ledger notification of the turn's current state, as the projector hears it. */
    private void changed(MentorTurn turn) {
        MentorTurn now = turns.require(workspace.getId(), turn.getId());
        projector.turnChanged(new MentorLedgerEvents.TurnChanged(
                workspace.getId(),
                now.getConversationId(),
                now.getId(),
                now.getStatus(),
                now.getSurface(),
                now.getAttempt()));
    }

    /** Heph writes more of the answer once the stream may take more text. */
    private void writes(MentorTurn turn, String text) {
        clock.advance(SlackMentorProjector.APPEND_INTERVAL);
        turns.updatePartial(turn.getId(), turn.getAttempt(), WORKER, partial(text));
        changed(turn);
    }

    private void end(MentorTurn turn, MentorTurn.Status outcome, String answer, String error) {
        List<JsonNode> entries = answer.isEmpty() ? List.of() : List.of(answer(answer));
        end(turn, outcome, entries, error);
    }

    private void end(MentorTurn turn, MentorTurn.Status outcome, List<JsonNode> entries, String error) {
        completion.end(
                turn.getId(),
                turn.getAttempt(),
                WORKER,
                new MentorTurnCompletion.Ending(outcome, entries, error.isEmpty() ? null : error, null));
    }

    private static ObjectNode answer(String text) {
        return MentorLedgerFixtures.answer(MentorLedgerFixtures.piId(), text);
    }

    /**
     * Heph says something, shows feedback in the same message, then writes {@code closing}. The running attempt holds
     * the claim on the feedback's conversation copy, as its {@code show_feedback} call left it.
     */
    private List<JsonNode> linkingAnswer(MentorTurn running, String closing) {
        shownFeedback = ConversationFeedbackRows.claimedBy(
                jdbc, workspace.getId(), developerAccount, running.getId(), FEEDBACK);
        String callId = "call-" + UUID.randomUUID();
        return List.of(
                MentorLedgerFixtures.showFeedback(
                        MentorLedgerFixtures.piId(), "Here is what I saw.", Map.of(callId, shownFeedback), FEEDBACK),
                MentorLedgerFixtures.toolResult(
                        MentorLedgerFixtures.piId(),
                        callId,
                        "show_feedback",
                        "Shown to the developer: feedback on prepared feedback " + shownFeedback,
                        false),
                answer(closing));
    }

    /** Whether a receipt settled the shown feedback's conversation copy. */
    private boolean copySettled() {
        ConversationReceipts.awaitSettled(jdbc);
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT posted_at IS NOT NULL OR last_error IS NOT NULL FROM feedback_copy WHERE feedback_id=?",
                Boolean.class,
                Objects.requireNonNull(shownFeedback)));
    }

    private FeedbackState feedbackState() {
        ConversationReceipts.awaitSettled(jdbc);
        return FeedbackState.valueOf(Objects.requireNonNull(jdbc.queryForObject(
                "SELECT state FROM feedback WHERE id=?", String.class, Objects.requireNonNull(shownFeedback))));
    }

    private static ObjectNode partial(String text) {
        ObjectNode snapshot = NODES.objectNode().put("activity", "writing");
        snapshot.putArray("messages")
                .addObject()
                .put("role", "assistant")
                .putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", text);
        return snapshot;
    }

    private MentorEntry entry(UUID id) {
        return entryRepository.findById(id).orElseThrow();
    }

    private MentorEntry question(MentorTurnService.Submission asked) {
        return entry(asked.question().getId());
    }

    private double replies(String state) {
        return meters.counter("mentor.slack.reply", "state", state).count();
    }

    private void stopStreamFails(String error) {
        doThrow(new SlackSendException(workspace.getId(), CHANNEL, error))
                .when(slack)
                .stopStream(anyLong(), anyString(), anyString(), any());
    }

    /** A Slack thread on the top-level question at {@code 100.1}, and its own question running at {@code 100.5}. */
    private MentorTurnService.Submission askInThread() {
        MentorTurnService.Submission parent = ask(main, "How are my reviews?", "100.1");
        MentorConversation thread =
                conversations.openThread(workspace.getId(), parent.question().getId());
        MentorTurnService.Submission asked = ask(thread, "Tell me more", "100.5");
        assertThat(turns.claimNext(WORKER, LEASE)).isPresent(); // the main conversation's turn
        assertThat(turns.claimNext(WORKER, LEASE).map(MentorTurn::getId))
                .contains(asked.turn().getId());
        return asked;
    }

    private MentorTurn running(MentorTurnService.Submission asked) {
        return turns.require(workspace.getId(), asked.turn().getId());
    }

    private MentorEntry answerOf(MentorTurn turn) {
        UUID answerId = turns.answer(workspace.getId(), turn.getId()).answerEntryId();
        return entry(Objects.requireNonNull(answerId));
    }

    private void postsAt(String ts) {
        when(slack.postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any()))
                .thenReturn(new SlackMessageService.Posted(CHANNEL, ts));
    }

    /** The Markdown of every message posted to the top level of the DM. */
    @SuppressWarnings("unchecked") // ArgumentCaptor cannot name a generic type.
    private List<String> postedTopLevel() {
        ArgumentCaptor<List<LayoutBlock>> blocks = ArgumentCaptor.forClass(List.class);
        verify(slack, atLeast(0)).postMessage(anyLong(), eq(CHANNEL), isNull(), blocks.capture(), anyString(), any());
        List<String> texts = new ArrayList<>();
        for (List<LayoutBlock> post : blocks.getAllValues()) {
            for (LayoutBlock block : post) {
                if (block instanceof MarkdownBlock markdown) {
                    texts.add(markdown.getText());
                }
            }
        }
        return texts;
    }

    /** The blocks that replaced the message of the question's stream at {@code 300.1}. */
    @SuppressWarnings("unchecked") // ArgumentCaptor cannot name a generic type.
    private List<LayoutBlock> replacement(MentorTurnService.Submission asked) {
        ArgumentCaptor<List<LayoutBlock>> blocks = ArgumentCaptor.forClass(List.class);
        verify(slack)
                .updateMessage(
                        eq(workspace.getId()),
                        eq(CHANNEL),
                        eq("300.1"),
                        blocks.capture(),
                        anyString(),
                        eq(SlackMentorPosts.metadata(asked.question().getId())));
        return blocks.getValue();
    }

    /** Slack's history of the thread: one page, with the messages its filter takes and nothing after it. */
    private void historyHolds(Message... messages) {
        when(slack.readWorkspaceMessages(eq(workspace.getId()), eq(CHANNEL), eq("100.1"), anyString(), any()))
                .thenAnswer(call -> {
                    Predicate<Message> match = call.getArgument(4);
                    return new SlackMessageService.Matches(
                            Stream.of(messages)
                                    .filter(match)
                                    .map(Message::getTs)
                                    .toList(),
                            false);
                });
    }

    /** A message of the app's bot. */
    private static Message botMessage(String ts, Message.@Nullable Metadata metadata) {
        Message message = new Message();
        message.setTs(ts);
        // Slack's bot message: `bot_id` always, `user` not always.
        message.setBotId(BOT_ID);
        message.setMetadata(metadata);
        return message;
    }

    private static String markdownOf(List<LayoutBlock> blocks) {
        return ((MarkdownBlock) blocks.getFirst()).getText();
    }

    private void appendFails(SlackSendException refusal) {
        doThrow(refusal).when(slack).appendStream(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldPostEveryMessageAndTheFeedbackHephShowedOnceWhenATopLevelTurnEnds() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);

        verify(slack).setReaction(workspace.getId(), CHANNEL, "100.1", "eyes", true);
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.PENDING);

        postsAt("200.1");
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you make of the gap?"), "");
        changed(running);
        changed(running);

        assertThat(postedTopLevel())
                .singleElement()
                .satisfies(text -> assertThat(text)
                        .startsWith("Here is what I saw.")
                        .contains("> " + FEEDBACK)
                        .endsWith("What do you make of the gap?"));
        verify(slack).setReaction(workspace.getId(), CHANNEL, "100.1", "eyes", false);
        assertThat(answerOf(running).getSlackTs()).isEqualTo("200.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(question(asked).getSlackTs()).isEqualTo("100.1");
        assertThat(feedbackState()).isEqualTo(FeedbackState.DELIVERED);
        assertThat(replies("delivered")).isOne();
    }

    @Test
    void shouldDeliverNoFeedbackWhoseTextSlackCannotShowWhenTheReplyIsCut() {
        MentorTurnService.Submission asked = ask(main, "Tell me everything", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        postsAt("200.1");

        List<JsonNode> entries = new ArrayList<>();
        entries.add(answer("a".repeat(13_000)));
        entries.addAll(linkingAnswer(running, "That is all."));
        end(running, MentorTurn.Status.DONE, entries, "");
        changed(running);

        assertThat(postedTopLevel())
                .singleElement()
                .satisfies(text -> assertThat(text).doesNotContain(FEEDBACK).endsWith(SlackMentorPosts.CUT_NOTE));
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState()).isEqualTo(FeedbackState.FAILED);
    }

    @Test
    void shouldLeaveFeedbackUnconfirmedWhenSlackCutsInsideItsOnlyQuote() {
        MentorTurnService.Submission asked = ask(main, "Tell me everything", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        postsAt("200.1");
        UUID feedback = ConversationFeedbackRows.claimedBy(
                jdbc, workspace.getId(), developerAccount, running.getId(), FEEDBACK);
        shownFeedback = feedback;
        String words = FEEDBACK + " " + "b".repeat(13_000);

        end(
                running,
                MentorTurn.Status.DONE,
                List.of(
                        MentorLedgerFixtures.showFeedback(
                                MentorLedgerFixtures.piId(), "Here is what I saw.", Map.of("call-a", feedback), words),
                        MentorLedgerFixtures.toolResult(
                                MentorLedgerFixtures.piId(), "call-a", "show_feedback", "Shown", false),
                        answer("That is all.")),
                "");
        changed(running);

        assertThat(postedTopLevel())
                .singleElement()
                .satisfies(text -> assertThat(text).contains("> " + FEEDBACK).endsWith(SlackMentorPosts.CUT_NOTE));
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState())
                .as("Slack showed the start of its only quote")
                .isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(copySettled()).as("the receipt records it").isTrue();
    }

    @Test
    void shouldLeaveFeedbackUnconfirmedWhenSlackCutsTheSecondOfTheTwoQuotesThatShowIt() {
        MentorTurnService.Submission asked = ask(main, "Tell me everything", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        postsAt("200.1");
        UUID feedback = ConversationFeedbackRows.claimedBy(
                jdbc, workspace.getId(), developerAccount, running.getId(), FEEDBACK);
        shownFeedback = feedback;
        ArrayNode content = NODES.arrayNode();
        content.addObject().put("type", "text").put("text", "Here is what I saw.");
        for (Map.Entry<String, String> call :
                List.of(Map.entry("call-a", FEEDBACK), Map.entry("call-b", "b".repeat(13_000)))) {
            content.addObject()
                    .put("type", "toolCall")
                    .put("id", call.getKey())
                    .put("name", "show_feedback")
                    .putObject("arguments")
                    .put("feedbackId", feedback.toString())
                    .put("text", call.getValue());
        }

        end(
                running,
                MentorTurn.Status.DONE,
                List.of(
                        MentorLedgerFixtures.message(MentorLedgerFixtures.piId(), "assistant", content),
                        MentorLedgerFixtures.toolResult(
                                MentorLedgerFixtures.piId(), "call-a", "show_feedback", "Shown", false),
                        MentorLedgerFixtures.toolResult(
                                MentorLedgerFixtures.piId(), "call-b", "show_feedback", "Shown", false),
                        answer("That is all.")),
                "");
        changed(running);

        assertThat(postedTopLevel())
                .singleElement()
                .satisfies(text -> assertThat(text).contains("> " + FEEDBACK).endsWith(SlackMentorPosts.CUT_NOTE));
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState())
                .as("Slack showed one of its quotes whole and cut the other")
                .isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(copySettled()).as("the receipt records it").isTrue();
    }

    @Test
    void shouldStreamTheAnswerAndStopItUnderFeedbackButtonsWhenAThreadTurnRuns() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenReturn("300.1");

        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        assertThat(question(asked).getSlackStreamTs()).isEqualTo("300.1");
        writes(running, "Your reviews are quick.");
        end(running, MentorTurn.Status.DONE, "Your reviews are quick. Keep it up.", "");
        changed(running);

        MentorEntry answer = answerOf(running);
        verify(slack).setReaction(workspace.getId(), CHANNEL, "100.5", SlackMentorPosts.WORKING_EMOJI, true);
        verify(slack).appendStream(workspace.getId(), CHANNEL, "300.1", " are quick.");
        verify(slack).appendStream(workspace.getId(), CHANNEL, "300.1", " Keep it up.");
        verify(slack)
                .stopStream(
                        workspace.getId(),
                        CHANNEL,
                        "300.1",
                        new SlackMessageService.StreamStop(
                                null,
                                SlackMentorPosts.feedbackButtons(answer.getId()),
                                SlackMentorPosts.metadata(asked.question().getId())));
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(answer.getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldFinishTheReplyOnceWhenAWakeUpForTheStreamIsStillPendingAtTheEnd() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        // Within the append interval: the stream waits for a wake-up.
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews are quick."));
        changed(running);
        assertThat(wakeUps).hasSize(1);

        end(running, MentorTurn.Status.DONE, "Your reviews are quick.", "");
        changed(running);
        wakeUps.forEach(Runnable::run);

        verify(pendingWakeUp).cancel(false);
        verify(slack).appendStream(workspace.getId(), CHANNEL, "300.1", " are quick.");
        verify(slack, times(1)).stopStream(anyLong(), anyString(), anyString(), any());
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(replies("delivered")).isOne();
    }

    @Test
    void shouldReplaceTheStreamWithTheWholeReplyWhenTheReconcilerFindsThatSlackAlreadyEndedIt() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Here is")).thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Here is"));
        changed(running);
        // The server stops before the turn ends; Slack ends the stream when it times out.
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        stopStreamFails("message_not_in_streaming_state");

        assertThat(delivery.retry(workspace.getId(), asked.question().getId())).isTrue();
        projector.reconcile(workspace.getId(), running.getId(), true);

        MentorEntry answer = answerOf(running);
        List<LayoutBlock> replacement = replacement(asked);
        assertThat(markdownOf(replacement))
                .startsWith("Here is what I saw.")
                .contains("> " + FEEDBACK)
                .endsWith("What do you think?");
        assertThat(replacement)
                .endsWith(SlackMentorPosts.feedbackButtons(answer.getId()).toArray(LayoutBlock[]::new));
        assertThat(answer.getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState())
                .as("the replaced message shows the feedback")
                .isEqualTo(FeedbackState.DELIVERED);
    }

    @Test
    void shouldReplaceTheStreamWithTheWholeReplyWhenSlackEndedItWhileHephWorked() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Here is")).thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Here is"));
        changed(running);
        // Slack ends an idle stream on its own, for example while Heph waits for a tool.
        appendFails(new SlackSendException(workspace.getId(), CHANNEL, "message_not_in_streaming_state"));
        stopStreamFails("message_not_in_streaming_state");
        writes(running, "Here is what");
        writes(running, "Here is what I saw.");

        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        changed(running);

        MentorEntry answer = answerOf(running);
        List<LayoutBlock> replacement = replacement(asked);
        verify(slack, times(1)).appendStream(anyLong(), anyString(), anyString(), anyString());
        assertThat(markdownOf(replacement))
                .startsWith("Here is what I saw.")
                .contains("> " + FEEDBACK)
                .endsWith("What do you think?");
        assertThat(replacement)
                .endsWith(SlackMentorPosts.feedbackButtons(answer.getId()).toArray(LayoutBlock[]::new));
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(answer.getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState())
                .as("the replaced message shows the feedback")
                .isEqualTo(FeedbackState.DELIVERED);
        assertThat(replies("delivered")).isOne();
    }

    @Test
    void shouldReplaceTheStreamWithTheWholeReplyWhenSlackEndedItBeforeTheRestOfTheAnswer() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Here is")).thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Here is"));
        changed(running);
        // The stream got no text while Heph showed feedback, and Slack ended it meanwhile.
        appendFails(new SlackSendException(workspace.getId(), CHANNEL, "message_not_in_streaming_state"));

        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        changed(running);

        MentorEntry answer = answerOf(running);
        List<LayoutBlock> replacement = replacement(asked);
        assertThat(markdownOf(replacement))
                .startsWith("Here is what I saw.")
                .contains("> " + FEEDBACK)
                .endsWith("What do you think?");
        assertThat(replacement)
                .endsWith(SlackMentorPosts.feedbackButtons(answer.getId()).toArray(LayoutBlock[]::new));
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState()).isEqualTo(FeedbackState.DELIVERED);
        assertThat(replies("delivered")).isOne();
    }

    @Test
    void shouldReplaceTheStreamWithTheWholeReplyWhenSlackRefusedEightAppendsInARow() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        appendFails(new SlackSendException(workspace.getId(), CHANNEL, "ratelimited", 1_000L));
        for (int written = 1; written <= 9; written++) {
            writes(running, "Your reviews" + ".".repeat(written));
        }

        end(running, MentorTurn.Status.DONE, "Your reviews are quick.", "");
        changed(running);

        MentorEntry answer = answerOf(running);
        verify(slack, times(8)).appendStream(anyLong(), anyString(), anyString(), anyString());
        assertThat(markdownOf(replacement(asked))).isEqualTo("Your reviews are quick.");
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(answer.getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldReplaceTheStreamThatAnUnansweredStartOpenedInsteadOfPostingBelowIt() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenThrow(new SlackSendException(workspace.getId(), CHANNEL, "transport_failure"));
        String opened = SlackTs.ofInstant(clock.instant());
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        historyHolds(botMessage(opened, null));

        end(running, MentorTurn.Status.DONE, "Your reviews are quick.", "");
        changed(running);

        verify(slack)
                .stopStream(
                        workspace.getId(),
                        CHANNEL,
                        opened,
                        new SlackMessageService.StreamStop(
                                null,
                                List.of(),
                                SlackMentorPosts.metadata(asked.question().getId())));
        verify(slack).updateMessage(eq(workspace.getId()), eq(CHANNEL), eq(opened), anyList(), anyString(), any());
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(question(asked).getSlackStreamTs()).isEqualTo(opened);
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldPostTheReplyAndOverwriteNoOtherMessageWhenARefusedStartOpenedNoStream() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenThrow(new SlackSendException(workspace.getId(), CHANNEL, "transport_failure"));
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        // While Heph works, Heph refuses another message in the thread with a reply of its own.
        clock.advance(Duration.ofSeconds(30));
        historyHolds(botMessage(SlackTs.ofInstant(clock.instant()), null));
        postsAt("400.1");

        end(running, MentorTurn.Status.DONE, "Your reviews are quick.", "");
        changed(running);

        verify(slack, never()).updateMessage(anyLong(), anyString(), anyString(), anyList(), anyString(), any());
        verify(slack, never()).stopStream(anyLong(), anyString(), anyString(), any());
        verify(slack).postMessage(eq(workspace.getId()), eq(CHANNEL), eq("100.1"), anyList(), anyString(), any());
        assertThat(answerOf(running).getSlackTs()).isEqualTo("400.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldReplaceTheStreamWhenHephsAnswerNoLongerStartsWithWhatSlackShows() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews ar"))
                .thenReturn("300.1");
        // A model call fails after Slack showed part of its text; the retried call writes another text.
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews ar"));
        changed(running);
        writes(running, "Let me look again.");

        end(running, MentorTurn.Status.DONE, "Let me look again. Your reviews are quick.", "");
        changed(running);

        verify(slack, never()).appendStream(anyLong(), anyString(), anyString(), anyString());
        verify(slack)
                .stopStream(
                        workspace.getId(),
                        CHANNEL,
                        "300.1",
                        new SlackMessageService.StreamStop(
                                null,
                                List.of(),
                                SlackMentorPosts.metadata(asked.question().getId())));
        assertThat(markdownOf(replacement(asked))).isEqualTo("Let me look again. Your reviews are quick.");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldReplaceADivergentStreamWithTheStoredAnswerWhenTheDeveloperStopsTheTurn() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews ar"))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews ar"));
        changed(running);
        writes(running, "Let me look");

        turns.stopRunning(workspace.getId(), running.getConversationId());
        changed(running);
        end(running, MentorTurn.Status.STOPPED, "Let me look again.", "");
        changed(running);

        verify(slack, never()).appendStream(anyLong(), anyString(), anyString(), anyString());
        assertThat(markdownOf(replacement(asked))).isEqualTo("Let me look again.");
        assertThat(answerOf(running).getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldAskSlackAtMostOnceEachIntervalWhenSlackRefusesAppends() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        appendFails(new SlackSendException(workspace.getId(), CHANNEL, "ratelimited", 1_000L));

        writes(running, "Your reviews are");
        // Later than the workspace's spacing, earlier than the stream's interval.
        clock.advance(SlackMentorProjector.WORKSPACE_APPEND_SPACING);
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews are quick."));
        changed(running);
        verify(slack, times(1)).appendStream(anyLong(), anyString(), anyString(), anyString());

        clock.advance(SlackMentorProjector.APPEND_INTERVAL);
        changed(running);
        verify(slack, times(2)).appendStream(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldReplaceTheStreamAndNeverPostTheAnswerTwiceWhenSlackLeavesAnAppendUnanswered() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        appendFails(new SlackSendException(workspace.getId(), CHANNEL, "internal_error"));
        writes(running, "Your reviews are");
        writes(running, "Your reviews are quick.");

        end(running, MentorTurn.Status.DONE, "Your reviews are quick. Keep it up.", "");
        changed(running);

        MentorEntry answer = answerOf(running);
        verify(slack, times(1)).appendStream(anyLong(), anyString(), anyString(), anyString());
        verify(slack)
                .stopStream(
                        workspace.getId(),
                        CHANNEL,
                        "300.1",
                        new SlackMessageService.StreamStop(
                                null,
                                List.of(),
                                SlackMentorPosts.metadata(asked.question().getId())));
        List<LayoutBlock> replacement = replacement(asked);
        assertThat(markdownOf(replacement)).isEqualTo("Your reviews are quick. Keep it up.");
        assertThat(replacement)
                .endsWith(SlackMentorPosts.feedbackButtons(answer.getId()).toArray(LayoutBlock[]::new));
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(answer.getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldLookForThePostAndNotTheStreamWhenThePostThatReplacedAGoneStreamIsUncertain() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        appendFails(new SlackSendException(workspace.getId(), CHANNEL, "message_not_found"));
        writes(running, "Your reviews are quick.");
        when(slack.postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any()))
                .thenThrow(new SlackSendException(workspace.getId(), CHANNEL, "transport_failure"));

        end(running, MentorTurn.Status.DONE, "Your reviews are quick.", "");
        changed(running);

        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.UNCERTAIN);
        assertThat(question(asked).getSlackStreamTs()).isNull();
        when(slack.readWorkspaceMessages(eq(workspace.getId()), eq(CHANNEL), eq("100.1"), anyString(), any()))
                .thenReturn(new SlackMessageService.Matches(List.of("400.1"), false));
        assertThat(delivery.retry(workspace.getId(), asked.question().getId())).isTrue();
        projector.reconcile(workspace.getId(), running.getId(), true);

        verify(slack, never()).stopStream(anyLong(), anyString(), anyString(), any());
        assertThat(answerOf(running).getSlackTs()).isEqualTo("400.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldSpaceTheAppendsOfTwoAnswersThatStreamInOneWorkspace() {
        MentorTurnService.Submission reviews = ask(main, "How are my reviews?", "100.1");
        MentorTurnService.Submission issues = ask(main, "How are my issues?", "100.2");
        MentorConversation reviewsThread =
                conversations.openThread(workspace.getId(), reviews.question().getId());
        MentorConversation issuesThread =
                conversations.openThread(workspace.getId(), issues.question().getId());
        MentorTurnService.Submission first = ask(reviewsThread, "Tell me more", "100.5");
        MentorTurnService.Submission second = ask(issuesThread, "Tell me more", "100.6");
        List<UUID> claimed = Stream.generate(() -> turns.claimNext(WORKER, LEASE))
                .limit(3)
                .flatMap(Optional::stream)
                .map(MentorTurn::getId)
                .toList();
        assertThat(claimed).contains(first.turn().getId(), second.turn().getId());
        MentorTurn one = running(first);
        MentorTurn two = running(second);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenReturn("300.1");
        when(slack.startStream(workspace.getId(), CHANNEL, "100.2", "Your issues"))
                .thenReturn("300.2");
        turns.updatePartial(one.getId(), one.getAttempt(), WORKER, partial("Your reviews"));
        changed(one);
        turns.updatePartial(two.getId(), two.getAttempt(), WORKER, partial("Your issues"));
        changed(two);

        clock.advance(SlackMentorProjector.APPEND_INTERVAL);
        turns.updatePartial(one.getId(), one.getAttempt(), WORKER, partial("Your reviews are quick."));
        changed(one);
        turns.updatePartial(two.getId(), two.getAttempt(), WORKER, partial("Your issues are old."));
        changed(two);
        verify(slack).appendStream(workspace.getId(), CHANNEL, "300.1", " are quick.");
        verify(slack, never()).appendStream(workspace.getId(), CHANNEL, "300.2", " are old.");

        clock.advance(SlackMentorProjector.WORKSPACE_APPEND_SPACING);
        wakeUps.forEach(Runnable::run);
        verify(slack).appendStream(workspace.getId(), CHANNEL, "300.2", " are old.");
    }

    @Test
    void shouldDeliverTheFeedbackTheStreamShowedWhenAThreadAnswerShowsIt() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Here is what I saw."))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Here is what I saw."));
        changed(running);

        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you make of the gap?"), "");
        changed(running);

        ArgumentCaptor<String> appended = ArgumentCaptor.forClass(String.class);
        verify(slack).appendStream(eq(workspace.getId()), eq(CHANNEL), eq("300.1"), appended.capture());
        assertThat(appended.getValue()).contains(FEEDBACK).endsWith("What do you make of the gap?");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState()).isEqualTo(FeedbackState.DELIVERED);
    }

    @Test
    void shouldDeliverNoFeedbackWhenSlackStoppedTheStreamBeforeItsText() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Here is what I saw."))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Here is what I saw."));
        changed(running);
        doThrow(new SlackSendException(workspace.getId(), CHANNEL, "stopped_by_user"))
                .when(slack)
                .appendStream(anyLong(), anyString(), anyString(), anyString());

        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you make of the gap?"), "");
        changed(running);

        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(answerOf(running).getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState()).isEqualTo(FeedbackState.FAILED);
    }

    @Test
    void shouldKeepWhatTheStreamShowsAndAppendNothingWhenTheDeveloperStoppedTheTurn() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Your reviews"))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Your reviews"));
        changed(running);
        stopStreamFails("message_not_in_streaming_state");

        turns.stopRunning(workspace.getId(), running.getConversationId());
        changed(running);
        end(running, MentorTurn.Status.STOPPED, "Your reviews are quick", "");
        changed(running);

        verify(slack, never()).appendStream(anyLong(), anyString(), anyString(), anyString());
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        verify(slack).setReaction(workspace.getId(), CHANNEL, "100.5", SlackMentorPosts.WORKING_EMOJI, false);
        assertThat(answerOf(running).getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
    }

    @Test
    void shouldEndTheStreamInPlaceAndNeverPostItAgainWhenSlackDoesNotAnswerTheEndOfTheStream() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Here is")).thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Here is"));
        changed(running);
        stopStreamFails("ratelimited");

        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        changed(running);

        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.UNCERTAIN);
        assertThat(feedbackState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(replies("uncertain")).isOne();

        doNothing().when(slack).stopStream(anyLong(), anyString(), anyString(), any());
        assertThat(delivery.retry(workspace.getId(), asked.question().getId())).isTrue();
        projector.reconcile(workspace.getId(), running.getId(), true);

        MentorEntry answer = answerOf(running);
        verify(slack)
                .stopStream(
                        workspace.getId(),
                        CHANNEL,
                        "300.1",
                        new SlackMessageService.StreamStop(
                                SlackMentorPosts.CUT_NOTE,
                                SlackMentorPosts.feedbackButtons(answer.getId()),
                                SlackMentorPosts.metadata(asked.question().getId())));
        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        verify(slack, never()).updateMessage(anyLong(), anyString(), anyString(), anyList(), anyString(), any());
        assertThat(answer.getSlackTs()).isEqualTo("300.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState())
                .as("what the stream showed is unknown to the reconciler")
                .isEqualTo(FeedbackState.UNCONFIRMED);
    }

    @Test
    void shouldDeliverTheFeedbackTheStreamShowedWhenSilentModeRefusesTheEndOfTheStream() {
        MentorTurnService.Submission asked = askInThread();
        MentorTurn running = running(asked);
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Here is what I saw."))
                .thenReturn("300.1");
        turns.updatePartial(running.getId(), running.getAttempt(), WORKER, partial("Here is what I saw."));
        changed(running);
        stopStreamFails("silent_mode_engaged");

        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        changed(running);

        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.FAILED);
        assertThat(feedbackState())
                .as("the stream showed the feedback before Silent Mode refused its end")
                .isEqualTo(FeedbackState.DELIVERED);
        assertThat(replies("failed")).isOne();
        assertThat(replies("delivered")).isZero();
    }

    @Test
    void shouldStreamTheRetriedAttemptAfreshWhenTheFirstAttemptLostItsLease() {
        MentorTurnService.Submission parent = ask(main, "How are my reviews?", "100.1");
        MentorConversation thread =
                conversations.openThread(workspace.getId(), parent.question().getId());
        MentorTurnService.Submission asked = ask(thread, "Tell me more", "100.5");
        turns.claimNext(WORKER, LEASE);
        MentorTurn first = turns.claimNext(WORKER, Duration.ofSeconds(-1)).orElseThrow();
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "First try"))
                .thenReturn("300.1");
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Second try"))
                .thenReturn("300.2");
        turns.updatePartial(first.getId(), first.getAttempt(), WORKER, partial("First try"));
        changed(first);

        completion.billLostAttempt(turns.expireLease(first.getId()).orElseThrow());
        changed(first);
        MentorTurn retried = turns.claimNext(WORKER, LEASE).orElseThrow();
        writes(retried, "Second try");

        assertThat(retried.getAttempt()).isEqualTo(2);
        verify(slack)
                .stopStream(
                        workspace.getId(),
                        CHANNEL,
                        "300.1",
                        new SlackMessageService.StreamStop(SlackMentorPosts.RESTART_NOTE, List.of(), null));
        verify(slack).startStream(workspace.getId(), CHANNEL, "100.1", "Second try");
        assertThat(question(asked).getSlackStreamTs()).isEqualTo("300.2");
    }

    @Test
    void shouldStreamTheRetriedAttemptWhenTheLostAttemptCouldNotOpenItsStream() {
        MentorTurnService.Submission parent = ask(main, "How are my reviews?", "100.1");
        MentorConversation thread =
                conversations.openThread(workspace.getId(), parent.question().getId());
        MentorTurnService.Submission asked = ask(thread, "Tell me more", "100.5");
        turns.claimNext(WORKER, LEASE);
        MentorTurn first = turns.claimNext(WORKER, Duration.ofSeconds(-1)).orElseThrow();
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "First try"))
                .thenThrow(new SlackSendException(workspace.getId(), CHANNEL, "transport_failure"));
        when(slack.startStream(workspace.getId(), CHANNEL, "100.1", "Second try"))
                .thenReturn("300.2");
        turns.updatePartial(first.getId(), first.getAttempt(), WORKER, partial("First try"));
        changed(first);

        completion.billLostAttempt(turns.expireLease(first.getId()).orElseThrow());
        changed(first);
        MentorTurn retried = turns.claimNext(WORKER, LEASE).orElseThrow();
        writes(retried, "Second try");

        verify(slack).startStream(workspace.getId(), CHANNEL, "100.1", "Second try");
        assertThat(question(asked).getSlackStreamTs()).isEqualTo("300.2");
    }

    @Test
    void shouldReplyWithTheReasonAndDeliverNoFeedbackWhenTheTurnFails() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        postsAt("200.9");
        end(
                running,
                MentorTurn.Status.FAILED,
                linkingAnswer(running, "unused"),
                "Heph took too long to reply and stopped. Try again.");
        changed(running);

        verify(slack)
                .postMessage(
                        eq(workspace.getId()),
                        eq(CHANNEL),
                        isNull(),
                        anyList(),
                        eq("Heph took too long to reply and stopped. Try again."),
                        eq(SlackMentorPosts.metadata(asked.question().getId())));
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(question(asked).getSlackTs()).isEqualTo("100.1");
        assertThat(feedbackState()).isEqualTo(FeedbackState.UNCONFIRMED);
    }

    @Test
    void shouldPostNothingAndCountNoReplyWhenTheDeveloperStoppedATopLevelTurnBeforeHephWrote() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);

        turns.stopRunning(workspace.getId(), main.getId());
        end(running, MentorTurn.Status.STOPPED, "", "");
        changed(running);

        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        verify(slack).setReaction(workspace.getId(), CHANNEL, "100.1", "eyes", false);
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.FAILED);
        assertThat(meters.find("mentor.slack.reply").counters()).isEmpty();
    }

    @Test
    void shouldDeliverNoFeedbackWhenSlackRefusesTheAnswer() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        when(slack.postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any()))
                .thenThrow(new SlackSendException(workspace.getId(), CHANNEL, "channel_not_found"));
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        changed(running);

        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.FAILED);
        assertThat(feedbackState()).isEqualTo(FeedbackState.FAILED);
    }

    @Test
    void shouldLeaveThePostUncertainForTheReconcilerWhenSlackDoesNotAnswer() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        when(slack.postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any()))
                .thenThrow(new SlackSendException(workspace.getId(), CHANNEL, "transport_failure"));
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        changed(running);

        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.UNCERTAIN);
        assertThat(feedbackState()).isEqualTo(FeedbackState.UNCONFIRMED);
        Instant later = Instant.now().plus(SlackMentorReconciler.GRACE).plusSeconds(60);
        assertThat(delivery.due(
                        later.minus(Duration.ofHours(1)),
                        later,
                        SlackMentorReconciler.GRACE,
                        SlackMentorReconciler.MAX_ATTEMPTS,
                        5,
                        1_000))
                .contains(new MentorSlackDelivery.Unsettled(
                        workspace.getId(), asked.question().getId(), running.getId(), true));
    }

    @Test
    void shouldTakeAnUnsettledReplyOnceAndLeaveItUncertainWhenTheReconcilerCouldNotDecide() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running); // claimed and reacted, then the server died
        end(running, MentorTurn.Status.DONE, "Your reviews are quick.", "");
        UUID questionId = asked.question().getId();
        MentorSlackDelivery.Unsettled unsettled =
                new MentorSlackDelivery.Unsettled(workspace.getId(), questionId, running.getId(), true);
        Duration delay = Duration.ofMinutes(2);
        Instant ended = Objects.requireNonNull(running(asked).getFinishedAt());

        assertThat(delivery.due(ended.minusSeconds(1), ended.plus(delay).plusSeconds(1), delay, 1, 5, 1_000))
                .contains(unsettled);
        assertThat(delivery.retry(workspace.getId(), questionId)).isTrue();
        assertThat(delivery.due(ended.minusSeconds(1), ended.plus(Duration.ofDays(1)), delay, 1, 5, 1_000))
                .as("one history read decides, or nothing does")
                .doesNotContain(unsettled);
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.UNCERTAIN);
    }

    @Test
    void shouldTakeAtMostItsShareFromOneWorkspaceWhenManyRepliesAreUnsettled() {
        List<UUID> questions = new ArrayList<>();
        for (String ts : List.of("100.1", "100.2", "100.3")) {
            MentorTurnService.Submission asked = ask(main, "Question " + ts, ts);
            MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
            end(running, MentorTurn.Status.DONE, "Answer " + ts, "");
            questions.add(asked.question().getId());
        }
        Instant later = Instant.now().plus(Duration.ofMinutes(10));

        List<MentorSlackDelivery.Unsettled> due =
                delivery.due(later.minus(Duration.ofHours(1)), later, Duration.ofMinutes(2), 8, 2, 1_000);

        assertThat(due)
                .filteredOn(unsettled -> questions.contains(unsettled.entryId()))
                .extracting(MentorSlackDelivery.Unsettled::entryId)
                .hasSize(2)
                .containsExactly(questions.get(0), questions.get(1));
    }

    @Test
    void shouldSettleAnUncertainPostFromTheSlackHistoryWhenTheReconcilerFindsIt() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running); // claimed and reacted, then the server died
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        when(slack.readWorkspaceMessages(eq(workspace.getId()), eq(CHANNEL), isNull(), eq("100.1"), any()))
                .thenReturn(new SlackMessageService.Matches(List.of("200.9"), false));

        assertThat(delivery.retry(workspace.getId(), asked.question().getId())).isTrue();
        projector.reconcile(workspace.getId(), running.getId(), true);

        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(answerOf(running).getSlackTs()).isEqualTo("200.9");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState()).isEqualTo(FeedbackState.DELIVERED);
    }

    @Test
    void shouldFailTheAnswerAndItsFeedbackAndNeverPostItAgainWhenTheSlackHistoryDoesNotHoldIt() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        when(slack.readWorkspaceMessages(anyLong(), anyString(), any(), anyString(), any()))
                .thenReturn(new SlackMessageService.Matches(List.of(), false));

        delivery.retry(workspace.getId(), asked.question().getId());
        projector.reconcile(workspace.getId(), running.getId(), true);

        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(question(asked).getSlackDelivery())
                .as("the app shows the answer as not delivered to Slack")
                .isEqualTo(MentorEntry.SlackDelivery.FAILED);
        assertThat(feedbackState()).isEqualTo(FeedbackState.FAILED);
    }

    @Test
    void shouldFailTheAnswerAndLeaveItsFeedbackUnconfirmedWhenSlackIsDisconnectedBeforeTheReconcilerLooks() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        when(slack.isConnected(workspace.getId())).thenReturn(false);

        delivery.retry(workspace.getId(), asked.question().getId());
        projector.reconcile(workspace.getId(), running.getId(), true);

        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(question(asked).getSlackDelivery())
                .as("the app shows the answer as not delivered to Slack")
                .isEqualTo(MentorEntry.SlackDelivery.FAILED);
        assertThat(feedbackState())
                .as("a server took the reply, so Slack may show the feedback: it is never sent again")
                .isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(copySettled())
                .as("the receipt records the unconfirmed answer")
                .isTrue();
    }

    @Test
    void shouldLeaveTheAnswerUncertainAndItsFeedbackUnconfirmedWhenTheSlackHistoryCannotBeRead() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        when(slack.readWorkspaceMessages(anyLong(), anyString(), any(), anyString(), any()))
                .thenThrow(new SlackSendException(workspace.getId(), CHANNEL, "ratelimited"));

        delivery.retry(workspace.getId(), asked.question().getId());
        projector.reconcile(workspace.getId(), running.getId(), true);

        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.UNCERTAIN);
        assertThat(feedbackState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(copySettled())
                .as("the receipt records the unconfirmed answer")
                .isTrue();
    }

    @Test
    void shouldSendAReplyThatNoServerTookWhenTheReconcilerFindsIt() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        // No server projected the turn: the question was never taken.
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        postsAt("201.1");

        assertThat(delivery.claim(workspace.getId(), asked.question().getId())).isTrue();
        projector.reconcile(workspace.getId(), running.getId(), false);

        verify(slack, never()).readWorkspaceMessages(anyLong(), anyString(), any(), anyString(), any());
        verify(slack, times(1)).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(answerOf(running).getSlackTs()).isEqualTo("201.1");
        assertThat(question(asked).getSlackDelivery()).isEqualTo(MentorEntry.SlackDelivery.DELIVERED);
        assertThat(feedbackState()).isEqualTo(FeedbackState.DELIVERED);
    }

    @Test
    void shouldNeverTouchSlackWhenTheQuestionWasAskedInTheApp() {
        turns.submit(
                workspace.getId(),
                main.getId(),
                new MentorTurnService.Question(
                        MentorTurn.Surface.APP,
                        "From the app",
                        UUID.randomUUID().toString(),
                        null,
                        null));
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        end(running, MentorTurn.Status.DONE, "An answer in the app.", "");
        changed(running);

        verifyNoInteractions(slack);
    }

    @Test
    void shouldLeaveTheAnswerUncertainAndItsFeedbackUnconfirmedWhenTheSlackHistoryHoldsMoreThanThePageWithoutIt() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        changed(running);
        end(running, MentorTurn.Status.DONE, linkingAnswer(running, "What do you think?"), "");
        // The developer wrote more messages after the answer than one page holds.
        when(slack.readWorkspaceMessages(anyLong(), anyString(), any(), anyString(), any()))
                .thenReturn(new SlackMessageService.Matches(List.of(), true));

        delivery.retry(workspace.getId(), asked.question().getId());
        projector.reconcile(workspace.getId(), running.getId(), true);

        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(question(asked).getSlackDelivery())
                .as("a page without the answer does not prove that Slack does not hold it")
                .isEqualTo(MentorEntry.SlackDelivery.UNCERTAIN);
        assertThat(feedbackState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(copySettled())
                .as("the receipt records the unconfirmed answer")
                .isTrue();
    }

    @Test
    void shouldNeverSendAReplyAgainWhenAServerClaimsItBetweenTheReconcilerSelectionAndItsTake() {
        MentorTurnService.Submission asked = ask(main, "How are my reviews?", "100.1");
        MentorTurn running = turns.claimNext(WORKER, LEASE).orElseThrow();
        // No server projected the turn yet: the question is not taken.
        end(running, MentorTurn.Status.DONE, "Your reviews are quick.", "");
        UUID questionId = asked.question().getId();
        Instant now = Instant.now().plus(Duration.ofMinutes(5));
        MentorSlackDelivery.Unsettled untaken =
                new MentorSlackDelivery.Unsettled(workspace.getId(), questionId, running.getId(), false);
        List<MentorSlackDelivery.Unsettled> selected = delivery
                .due(
                        now.minus(SlackMentorReconciler.HORIZON),
                        now,
                        SlackMentorReconciler.GRACE,
                        SlackMentorReconciler.MAX_ATTEMPTS,
                        5,
                        1_000)
                .stream()
                .filter(untaken::equals)
                .toList();
        assertThat(selected).containsExactly(untaken);
        MentorSlackDelivery interleaved = mock(MentorSlackDelivery.class);
        when(interleaved.due(any(), any(), any(), anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            // A delayed projector claims the reply after the reconciler selected it as untaken.
            assertThat(delivery.claim(workspace.getId(), questionId)).isTrue();
            return selected;
        });
        when(interleaved.claim(workspace.getId(), questionId))
                .thenAnswer(call -> delivery.claim(workspace.getId(), questionId));
        when(interleaved.retry(workspace.getId(), questionId))
                .thenAnswer(call -> delivery.retry(workspace.getId(), questionId));
        postsAt("201.1");

        new SlackMentorReconciler(
                        interleaved, projector, mock(SlackWeeklyDigestPointer.class), Clock.fixed(now, ZoneOffset.UTC))
                .reconcile();

        verify(slack, never()).postMessage(anyLong(), anyString(), any(), anyList(), anyString(), any());
        assertThat(question(asked).getSlackDelivery())
                .as("the server that claimed the reply still owns it")
                .isEqualTo(MentorEntry.SlackDelivery.PENDING);
    }
}
