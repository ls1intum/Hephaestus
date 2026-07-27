package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.config.AgentBindingLimits;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.MentorTurnLlmUsage;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Atomically interrupts and accounts mentor turns abandoned by a crashed process.
 *
 * <p>An abandoned turn is the one case where nobody is left to report what it spent: its worker died
 * mid-stream, so there is no runner report and no in-process meter. It is billed from the per-call
 * totals the LLM proxy wrote to the row as each call was served — which is why those columns exist.
 * Only a turn that made no recorded call at all is still booked as UNVERIFIABLE, and that is now a
 * true statement about it rather than a gap in what we kept.
 *
 * <h2>One turn per transaction, and one sweeper per instance</h2>
 *
 * <p>This sweep WRITES MONEY, so neither of the two ways a batch can lose rows is acceptable.
 *
 * <p><b>Per turn, not per batch.</b> {@link #accountOne} owns one turn in its own {@code REQUIRES_NEW}
 * transaction and {@link #reap} catches its failure, so a turn that finishes between the select and the
 * write — the write then losing the optimistic-lock race — costs exactly that one turn's tick. Before,
 * a single such collision rolled back the whole batch INCLUDING turns already billed in it, and every
 * one of those turns kept its {@code in_flight} row: the partial unique index then refuses every
 * further turn on that thread until some later tick happens to get through cleanly. A failure is
 * WARN-logged and counted on {@code mentor.in_flight.reaper.failure} rather than swallowed, because
 * a sustained rate means turns are staying stuck.
 *
 * <p><b>One replica at a time.</b> {@code @SchedulerLock} single-flights the sweep across server pods.
 * Two pods sweeping the same stale rows would race every write; the loser bills nothing and merely
 * burns the collision counter, so the lock is what keeps that counter meaningful.
 *
 * <h2>What actually prevents a double charge</h2>
 *
 * <p>Not the re-read — the <b>ledger's unique constraint</b>. This sweep and the normal turn path
 * both key their event on {@code (MENTOR_TURN, messageId, sourceAttempt=0)}
 * ({@link de.tum.cit.aet.hephaestus.agent.mentor.chat.MentorTurnPersistence} uses the same triple),
 * and {@code LlmUsageEventRepository#insertIfAbsent} is an {@code ON CONFLICT … DO NOTHING}. Two
 * writes for one turn therefore collapse to one row no matter which order they arrive in, and no
 * in-process check is load-bearing for that.
 *
 * <p>What the per-turn re-read buys is which row <em>wins</em>. Without it this sweep would bill a
 * turn that has since finished, and if that write landed first the constraint would reject the normal
 * path's correct amount, leaving the wrong number in the ledger permanently. So the re-read is not
 * what makes the sweep idempotent; it is what keeps an idempotent write from being the wrong one.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Sweeps stuck rows by created_at; not a tenant data accessor")
public class MentorInFlightReaper {

    private static final Logger log = LoggerFactory.getLogger(MentorInFlightReaper.class);
    /**
     * What a turn may take beyond its own budget before it is certainly not running: sandbox startup,
     * streaming finalisation, and this sweep's own cron/lock delay.
     */
    private static final Duration OVERHEAD_ALLOWANCE = Duration.ofMinutes(10);

    /**
     * The floor under the configured window. A turn reaped while it is still running is billed as
     * abandoned and loses its thread to the in-flight index, so the window has to clear the longest
     * turn a binding can produce.
     *
     * <p>Derived, not chosen: {@link AgentBindingLimits#MAX_TIMEOUT_SECONDS} is the ceiling the binding
     * API enforces and {@code MentorPiAdapter} clamps every turn's budget to, so the longest possible
     * turn is bounded and this floor clears it by {@link #OVERHEAD_ALLOWANCE}. Raise the ceiling and
     * this window rises with it.
     */
    private static final Duration MINIMUM_SAFE_WINDOW = Duration.ofSeconds(AgentBindingLimits.MAX_TIMEOUT_SECONDS).plus(
        OVERHEAD_ALLOWANCE
    );
    private final ChatMessageRepository chatMessageRepository;
    private final LlmUsageRecorder usageRecorder;
    private final MeterRegistry meterRegistry;
    private final MentorInFlightReaper self;
    private final Duration window;

    public MentorInFlightReaper(
        ChatMessageRepository chatMessageRepository,
        LlmUsageRecorder usageRecorder,
        MeterRegistry meterRegistry,
        @Lazy MentorInFlightReaper self,
        @Value("${hephaestus.mentor.in-flight-reaper.window:PT70M}") Duration window
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.usageRecorder = usageRecorder;
        this.meterRegistry = meterRegistry;
        this.self = self;
        this.window = safeWindow(window);
    }

    /**
     * Select the stale turns, then account each one on its own. Deliberately NOT transactional: the
     * batch must not be an all-or-nothing unit, so the only transactions here are the per-turn ones
     * {@link #accountOne} opens.
     */
    @Scheduled(cron = "${hephaestus.mentor.in-flight-reaper.cron:0 */2 * * * *}")
    @SchedulerLock(name = "mentor-in-flight-reaper", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void reap() {
        List<UUID> stale = chatMessageRepository
            .findStaleInFlightForAccounting(Instant.now().minus(window))
            .stream()
            .map(ChatMessage::getId)
            .toList();
        int billed = 0;
        int failed = 0;
        for (UUID messageId : stale) {
            try {
                // Self-invocation through the proxy, or the per-turn transaction boundary below would
                // not exist and one bad row would still be able to take the batch down.
                if (self.accountOne(messageId)) {
                    billed++;
                }
            } catch (RuntimeException e) {
                failed++;
                // Never rethrown: one turn losing its write must not stop the turns behind it. Counted
                // because a sustained rate means turns are staying stuck in flight.
                log.warn(
                    "Mentor in-flight reaper could not account turn {}; leaving it for the next tick",
                    messageId,
                    e
                );
                meterRegistry.counter("mentor.in_flight.reaper.failure").increment();
            }
        }
        if (!stale.isEmpty()) log.info(
            "Mentor in-flight reaper accounted {} stuck row(s); {} billed from proxy-recorded usage, {} failed",
            stale.size(),
            billed,
            failed
        );
    }

    /**
     * Interrupt and bill ONE abandoned turn, in its own transaction.
     *
     * <p>The turn is re-read here rather than carried over from the select, so the write is made
     * against a current snapshot and a turn that finished in the meantime is skipped instead of being
     * billed twice.
     *
     * @return true when the turn was billed from proxy-recorded usage; false when it was skipped or
     *     booked UNVERIFIABLE
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean accountOne(UUID messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId).orElse(null);
        if (message == null || message.getStatus() != ChatMessage.Status.in_flight) {
            // It finished, or another sweep got it, between the select and now. Its own path has already
            // recorded what it spent; billing it here would double-charge the workspace.
            return false;
        }
        JsonNode existingMetadata = message.getMetadata();
        LlmPriceSnapshot price = MentorAdmissionMetadata.readPrice(existingMetadata);
        message.setStatus(ChatMessage.Status.interrupted);
        ObjectNode metadata =
            existingMetadata != null && existingMetadata.isObject()
                ? (ObjectNode) existingMetadata.deepCopy()
                : tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        metadata.put("error", "server restart");
        message.setMetadata(metadata);
        chatMessageRepository.saveAndFlush(message);
        // AFTER the status flip, never before: the flush locks the row and moves it out of
        // 'in_flight', which is the predicate the proxy's per-call accumulator writes under. So a
        // call still in flight either lands before this read or matches nothing — the totals read
        // here are final, not a snapshot that can still move.
        MentorTurnLlmUsage observed = chatMessageRepository
            .findLlmUsageById(message.getId())
            .orElse(MentorTurnLlmUsage.NONE);
        Long workspaceId = message.getThread().getWorkspace().getId();
        LlmUsageRecorder.LlmUsageSample sample = new LlmUsageRecorder.LlmUsageSample(
            LlmUsageJobType.MENTOR_TURN,
            LlmUsageSourceType.MENTOR_TURN,
            message.getId(),
            0,
            MentorAdmissionMetadata.readModel(existingMetadata),
            observed.inputTokens(),
            observed.outputTokens(),
            observed.cacheReadTokens(),
            0,
            observed.reasoningTokens(),
            Math.max(1, observed.totalCalls()),
            price,
            Instant.now()
        );
        if (observed.hasBillableUsage()) {
            usageRecorder.record(workspaceId, sample);
            return true;
        }
        // Nothing was ever recorded for this turn — it died before its first call returned, or its
        // provider reported no usage. UNVERIFIABLE is the honest verdict, and it keeps the month's
        // budget decision from silently counting the turn as free.
        usageRecorder.recordUnverifiable(workspaceId, sample);
        return false;
    }

    private static Duration safeWindow(Duration configuredWindow) {
        if (configuredWindow.compareTo(MINIMUM_SAFE_WINDOW) < 0) {
            log.warn(
                "Mentor in-flight reaper window {} is unsafe for configured turns; using {}",
                configuredWindow,
                MINIMUM_SAFE_WINDOW
            );
            return MINIMUM_SAFE_WINDOW;
        }
        return configuredWindow;
    }

    Duration window() {
        return window;
    }
}
