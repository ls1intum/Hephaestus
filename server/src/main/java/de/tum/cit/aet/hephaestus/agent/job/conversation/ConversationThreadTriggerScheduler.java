package de.tum.cit.aet.hephaestus.agent.job.conversation;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationCandidateSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadCandidate;
import de.tum.cit.aet.hephaestus.agent.handler.ConversationReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobService;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Detects settled Slack conversation threads that are ready for a communication-practice review and enqueues
 * one {@link AgentJobType#CONVERSATION_REVIEW} job per human participant.
 *
 * <p>Three gates, all deterministic:
 * <ul>
 *   <li><b>Quiescence</b> — no new message for {@value #QUIESCENCE_MINUTES} minutes (the thread has settled).</li>
 *   <li><b>Depth</b> — at least {@value #MIN_HUMAN_TURNS} non-tombstoned turns (a real exchange, not a one-liner).</li>
 *   <li><b>Growth</b> — at least {@value #MIN_GROWTH} new non-tombstoned turns since {@code slack_thread.last_reviewed_ts}
 *       (the watermark), so a re-sweep with no fresh human turn past the watermark enqueues nothing.</li>
 * </ul>
 *
 * <p>The watermark is advanced to the thread's newest {@code ts} only <em>after</em> a job is enqueued. Cooldown
 * is keyed on the thread + subject alone (via the idempotency-key prefix, freshness stripped by
 * {@link AgentJobService#extractCooldownKeyPrefix}), NOT on {@code threadId + lastTs}, so a late reply does not
 * immediately re-fire — only genuine growth past the watermark does.
 *
 * <p><b>Tenancy &amp; ownership.</b> The scheduler owns none of the Slack schema: candidate scan, turn counts,
 * and watermark advance go through the agent-owned {@link ConversationCandidateSource} SPI implemented by
 * {@code integration.slack}, keeping the edge one-way (no bounded-context cycle) and raw {@code slack_*} SQL
 * out of the agent.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic(
    "Cross-workspace conversation-thread sweep on a schedule; the candidate scan / counts / watermark advance " +
        "delegate to the Slack-implemented ConversationCandidateSource SPI (workspace-pinned there) and the " +
        "enqueue delegates to AgentJobService#submit, which scopes its own writes (same inherently cross-workspace " +
        "pattern as SlackRetentionSweeper)"
)
public class ConversationThreadTriggerScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConversationThreadTriggerScheduler.class);

    /** Quiescence window: a thread is a candidate only once it has been silent this long. */
    static final int QUIESCENCE_MINUTES = 10;

    /** Minimum non-tombstoned turns for a thread to be worth reviewing at all. */
    static final int MIN_HUMAN_TURNS = 4;

    /** Minimum NEW non-tombstoned turns since the watermark for a re-review to fire. */
    static final int MIN_GROWTH = 2;

    private final ConversationCandidateSource candidateSource;
    private final AgentJobService agentJobService;

    /**
     * Capability flag, available by default. When {@code false} the sweep no-ops, keeping the conversation-detection
     * subsystem dormant in lockstep with
     * {@link de.tum.cit.aet.hephaestus.integration.slack.events.SlackIngestService}'s channel-ingest gate. Bound from
     * {@code hephaestus.integration.slack.conversation-ingest.enabled}.
     */
    private final boolean conversationIngestEnabled;

    public ConversationThreadTriggerScheduler(
        ConversationCandidateSource candidateSource,
        AgentJobService agentJobService,
        @Value("${hephaestus.integration.slack.conversation-ingest.enabled:true}") boolean conversationIngestEnabled
    ) {
        this.candidateSource = candidateSource;
        this.agentJobService = agentJobService;
        this.conversationIngestEnabled = conversationIngestEnabled;
    }

    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "conversation-thread-detection", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void sweep() {
        detectNow();
    }

    /**
     * Run detection immediately across every workspace. Exposed (rather than invoked only via the cron) so
     * integration tests can drive it deterministically.
     *
     * @return the number of conversation-review jobs enqueued this run
     */
    public long detectNow() {
        if (!conversationIngestEnabled) {
            return 0;
        }
        List<ConversationThreadCandidate> candidates = candidateSource.settledCandidates(MIN_HUMAN_TURNS);
        Instant now = Instant.now();
        long enqueued = 0;
        for (ConversationThreadCandidate c : candidates) {
            long totalTurns = candidateSource.liveTurnCount(c.workspaceId(), c.channelId(), c.threadTs());
            long growth = candidateSource.liveTurnCountSince(
                c.workspaceId(),
                c.channelId(),
                c.threadTs(),
                c.lastReviewedTs()
            );
            if (!passesGates(now, c.lastTs(), totalTurns, growth, QUIESCENCE_MINUTES, MIN_HUMAN_TURNS, MIN_GROWTH)) {
                continue;
            }
            boolean enqueuedAny = false;
            for (long participant : c.participantMemberIds()) {
                if (participant <= 0) {
                    continue;
                }
                try {
                    var request = new ConversationReviewSubmissionRequest(
                        c.threadId(),
                        c.channelId(),
                        c.threadTs(),
                        participant,
                        c.lastTs()
                    );
                    if (
                        agentJobService.submit(c.workspaceId(), AgentJobType.CONVERSATION_REVIEW, request).isPresent()
                    ) {
                        enqueuedAny = true;
                        enqueued++;
                    }
                } catch (RuntimeException e) {
                    log.warn(
                        "conversation.detect: enqueue failed for threadId={}, participant={}: {}",
                        c.threadId(),
                        participant,
                        e.toString()
                    );
                }
            }
            // Advance the watermark ONLY after at least one job was enqueued, so a workspace with no enabled
            // agent config keeps re-appearing as a candidate and catches up once one is configured.
            if (enqueuedAny) {
                candidateSource.markReviewed(c.workspaceId(), c.threadId(), c.lastTs());
            }
        }
        if (enqueued > 0) {
            log.info(
                "conversation.detect: enqueued {} review job(s) across {} candidate thread(s)",
                enqueued,
                candidates.size()
            );
        }
        return enqueued;
    }

    /**
     * Pure gate predicate (unit-tested directly). A thread is ready when it has settled ({@code quiescenceMinutes}
     * of silence past its newest {@code ts}), has at least {@code minHumanTurns} live turns, and has grown by at
     * least {@code minGrowth} live turns since the watermark.
     */
    static boolean passesGates(
        Instant now,
        @Nullable String lastTs,
        long totalTurns,
        long growthSinceWatermark,
        int quiescenceMinutes,
        int minHumanTurns,
        int minGrowth
    ) {
        if (totalTurns < minHumanTurns) {
            return false;
        }
        if (growthSinceWatermark < minGrowth) {
            return false;
        }
        Long lastEpoch = slackTsEpochSeconds(lastTs);
        if (lastEpoch == null) {
            return false;
        }
        long ageSeconds = now.getEpochSecond() - lastEpoch;
        return ageSeconds >= (long) quiescenceMinutes * 60L;
    }

    /**
     * Parse the integer-second part of a Slack {@code ts} ({@code "1700000000.123456"}). Returns {@code null}
     * for a null / unparseable value (which the gate treats as not-yet-ready rather than throwing).
     */
    static @Nullable Long slackTsEpochSeconds(@Nullable String slackTs) {
        if (slackTs == null || slackTs.isBlank()) {
            return null;
        }
        int dot = slackTs.indexOf('.');
        String secs = dot >= 0 ? slackTs.substring(0, dot) : slackTs;
        try {
            return Long.parseLong(secs.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
