package de.tum.cit.aet.hephaestus.integration.slack.mentor;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;

import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationFeedbackPreparedEvent;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountPreferencesQuery;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Proactive Slack nudge for prepared conversational feedback — the doorbell, not the delivery. When a detection
 * cycle prepares CONVERSATION units for a recipient ({@link ConversationFeedbackPreparedEvent}), DMs them a
 * count-only pointer so the units do not silently age out under the 14-day TTL when the developer never happens
 * to open a mentor chat. Finding content, severity, and evidence NEVER appear in the DM — the mentor
 * conversation is the consent-gated surface where content flows.
 *
 * <p>All guards must hold, else a silent debug-level skip: an ACTIVE Slack connection for the workspace, a Slack
 * identity link resolving the recipient to a {@code U…} id in that connection's team, and the recipient not
 * having opted out of AI review ({@code aiReviewEnabled}; an absent preferences row means enabled, mirroring
 * {@code FeedbackDeliveryService}).
 *
 * <p><b>Cooldown:</b> at most one DM per recipient per {@link #COOLDOWN} (24h — deliberately a constant, not
 * configuration). Tracked in an in-memory map only: the deployment runs a single worker replica today, so the
 * worst a restart can cost is one duplicate DM — not worth a table. The window is claimed atomically right
 * before the send (so two same-cycle events yield one DM) and released again on a send failure.
 *
 * <p>Runs {@code @Async @TransactionalEventListener(AFTER_COMMIT)} against the preparer's REQUIRES_NEW
 * transaction, so it only sees committed units and a Slack outage never touches the preparation path;
 * failures are logged, never rethrown. The DM posts with the recipient's
 * {@code U…} id as the channel — {@code chat.postMessage} opens the app DM itself, the same surface the Slack
 * mentor already lives in.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackConversationNudgeService {

    private static final Logger log = LoggerFactory.getLogger(SlackConversationNudgeService.class);

    /** At most one nudge DM per recipient per window. */
    static final Duration COOLDOWN = Duration.ofHours(24);

    private final ConnectionService connectionService;
    private final UserRepository userRepository;
    private final AccountPreferencesQuery accountPreferencesQuery;
    private final SlackMentorIdentityResolver identityResolver;
    private final SlackMessageService slackMessageService;

    /** recipient {@code User} id → last nudge instant; single-replica semantics (see class javadoc). */
    private final ConcurrentHashMap<Long, Instant> lastNudgeAt = new ConcurrentHashMap<>();

    public SlackConversationNudgeService(
        ConnectionService connectionService,
        UserRepository userRepository,
        AccountPreferencesQuery accountPreferencesQuery,
        SlackMentorIdentityResolver identityResolver,
        SlackMessageService slackMessageService
    ) {
        this.connectionService = connectionService;
        this.userRepository = userRepository;
        this.accountPreferencesQuery = accountPreferencesQuery;
        this.identityResolver = identityResolver;
        this.slackMessageService = slackMessageService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConversationFeedbackPrepared(ConversationFeedbackPreparedEvent event) {
        try {
            nudge(event);
        } catch (RuntimeException e) {
            log.warn(
                "slack.nudge: failed for workspaceId={}, recipientUserId={}: {}",
                event.workspaceId(),
                event.recipientUserId(),
                e.toString()
            );
        }
    }

    private void nudge(ConversationFeedbackPreparedEvent event) {
        if (event.unitCount() <= 0 || event.workspaceId() == null || event.recipientUserId() == null) {
            return;
        }
        long workspaceId = event.workspaceId();
        long recipientId = event.recipientUserId();
        Optional<Connection> connection = connectionService.findActive(workspaceId, IntegrationKind.SLACK);
        if (connection.isEmpty()) {
            log.debug("slack.nudge: skip, no ACTIVE Slack connection: workspaceId={}", workspaceId);
            return;
        }
        Optional<User> recipient = userRepository.findById(recipientId);
        if (recipient.isEmpty()) {
            log.debug("slack.nudge: skip, unknown recipient: recipientUserId={}", recipientId);
            return;
        }
        boolean aiReviewEnabled = accountPreferencesQuery
            .preferencesForLogin(recipient.get().getLogin())
            .map(AccountPreferencesQuery.PreferencesView::aiReviewEnabled)
            .orElse(true);
        if (!aiReviewEnabled) {
            log.debug("slack.nudge: skip, recipient opted out of AI review: recipientUserId={}", recipientId);
            return;
        }
        Optional<String> slackUserId = identityResolver.resolveSlackUserId(
            recipientId,
            connection.get().getInstanceKey()
        );
        if (slackUserId.isEmpty()) {
            log.debug("slack.nudge: skip, no Slack identity link: recipientUserId={}", recipientId);
            return;
        }
        Instant now = Instant.now();
        if (!claimWindow(recipientId, now)) {
            log.debug("slack.nudge: skip, on cooldown: recipientUserId={}", recipientId);
            return;
        }
        String text = message(event.unitCount());
        try {
            slackMessageService.sendForWorkspace(
                workspaceId,
                slackUserId.get(),
                asBlocks(section(s -> s.text(markdownText(text)))),
                text
            );
        } catch (SlackSendException e) {
            // Release the claimed window so the next cycle's event may retry instead of going dark for 24h.
            lastNudgeAt.remove(recipientId, now);
            log.warn(
                "slack.nudge: send failed: workspaceId={}, recipientUserId={}, slackError={}",
                workspaceId,
                recipientId,
                e.slackError()
            );
        }
    }

    /** Atomically claim the recipient's cooldown window; only the claimant sends. */
    private boolean claimWindow(long recipientId, Instant now) {
        return (
            lastNudgeAt.compute(recipientId, (id, prev) ->
                prev != null && prev.plus(COOLDOWN).isAfter(now) ? prev : now
            ) ==
            now
        );
    }

    /** Count-only copy — deliberately no finding details, no severity, no artifact references. */
    static String message(int unitCount) {
        return unitCount == 1
            ? "You have 1 new practice observation to explore — reply here to go through it."
            : "You have " + unitCount + " new practice observations to explore — reply here to go through them.";
    }
}
