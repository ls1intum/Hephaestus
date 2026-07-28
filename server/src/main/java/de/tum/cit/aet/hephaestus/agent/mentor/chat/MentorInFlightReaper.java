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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Interrupts and accounts mentor turns abandoned by a crashed process. Nobody is left to report what
 * such a turn spent — no runner report, no in-process meter — so it is billed from the per-call totals
 * the LLM proxy wrote to its row, and only a turn with no recorded call at all is booked UNVERIFIABLE.
 *
 * <p>This sweep writes money, so it is deliberately per-turn rather than per-batch: {@link #accountOne}
 * owns one turn in its own {@code REQUIRES_NEW} transaction. A batch rollback would also undo turns
 * already billed in it and leave their {@code in_flight} rows in place, and the partial unique index
 * would then refuse every further turn on those threads.
 *
 * <p>What prevents a double charge is the ledger's {@code (MENTOR_TURN, messageId, 0)} unique
 * constraint behind {@code ON CONFLICT … DO NOTHING}, not any in-process check. The per-turn re-read
 * decides which of the two writes is the CORRECT one: without it, this sweep could bill a turn that has
 * since finished and permanently shadow the normal path's real amount.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Sweeps stuck rows by created_at; not a tenant data accessor")
public class MentorInFlightReaper {

    private static final Logger log = LoggerFactory.getLogger(MentorInFlightReaper.class);
    /** Sandbox startup, streaming finalisation and this sweep's own cron/lock delay. */
    private static final Duration OVERHEAD_ALLOWANCE = Duration.ofMinutes(10);

    /**
     * A turn reaped while still running is billed as abandoned and loses its thread to the in-flight
     * index, so the window must clear the longest turn a binding can produce. Derived from the ceiling
     * the binding API enforces, so raising that ceiling raises this floor with it.
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

    /** Deliberately not transactional: the only transactions are the per-turn ones {@link #accountOne} opens. */
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
                // Through the proxy, or the per-turn transaction boundary would not exist.
                if (self.accountOne(messageId)) {
                    billed++;
                }
            } catch (RuntimeException e) {
                failed++;
                // Never rethrown: one turn losing its write must not stop the turns behind it.
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
     * The turn is re-read here rather than carried over from the select, so a turn that finished in the
     * meantime is skipped instead of billed.
     *
     * @return true when the turn was billed from proxy-recorded usage; false when it was skipped or
     *     booked UNVERIFIABLE
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean accountOne(UUID messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId).orElse(null);
        if (message == null || message.getStatus() != ChatMessage.Status.in_flight) {
            // It finished, or another sweep got it. Its own path already recorded what it spent.
            return false;
        }
        JsonNode existingMetadata = message.getMetadata();
        LlmPriceSnapshot price = MentorAdmissionMetadata.readPrice(existingMetadata);
        message.setStatus(ChatMessage.Status.interrupted);
        message.setMetadata(withAbandonedError(existingMetadata));
        chatMessageRepository.saveAndFlush(message);
        // AFTER the status flip, never before: the flush moves the row out of 'in_flight', which is the
        // predicate the proxy's per-call accumulator writes under, so these totals can no longer move.
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
        // The turn died before its first call returned, or the provider reported no usage. UNVERIFIABLE
        // keeps the month's budget decision from counting the turn as free.
        usageRecorder.recordUnverifiable(workspaceId, sample);
        return false;
    }

    private static ObjectNode withAbandonedError(@Nullable JsonNode existingMetadata) {
        ObjectNode metadata =
            existingMetadata != null && existingMetadata.isObject()
                ? (ObjectNode) existingMetadata.deepCopy()
                : JsonNodeFactory.instance.objectNode();
        metadata.put("error", "server restart");
        return metadata;
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
