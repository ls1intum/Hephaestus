package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Atomically interrupts and accounts mentor turns abandoned by a crashed process. */
@Component
@WorkspaceAgnostic("Sweeps stuck rows by created_at; not a tenant data accessor")
public class MentorInFlightReaper {

    private static final Logger log = LoggerFactory.getLogger(MentorInFlightReaper.class);
    // Agent configs allow up to 60 minutes. Keep another 10 minutes for startup,
    // streaming finalisation, and scheduler/database delays before declaring a turn abandoned.
    private static final Duration MINIMUM_SAFE_WINDOW = Duration.ofMinutes(70);
    private final ChatMessageRepository chatMessageRepository;
    private final LlmUsageRecorder usageRecorder;
    private final Duration window;

    public MentorInFlightReaper(
        ChatMessageRepository chatMessageRepository,
        LlmUsageRecorder usageRecorder,
        @Value("${hephaestus.mentor.in-flight-reaper.window:PT70M}") Duration window
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.usageRecorder = usageRecorder;
        this.window = safeWindow(window);
    }

    @Transactional
    @Scheduled(cron = "${hephaestus.mentor.in-flight-reaper.cron:0 */2 * * * *}")
    public void reap() {
        var stale = chatMessageRepository.findStaleInFlightForAccounting(Instant.now().minus(window));
        for (ChatMessage message : stale) {
            JsonNode existingMetadata = message.getMetadata();
            JsonNode admission =
                existingMetadata != null
                    ? existingMetadata.path("llmAdmission")
                    : tools.jackson.databind.node.MissingNode.getInstance();
            LlmPriceSnapshot price = readPrice(admission.path("price"));
            message.setStatus(ChatMessage.Status.interrupted);
            ObjectNode metadata =
                existingMetadata != null && existingMetadata.isObject()
                    ? (ObjectNode) existingMetadata.deepCopy()
                    : tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            metadata.put("error", "server restart");
            message.setMetadata(metadata);
            chatMessageRepository.saveAndFlush(message);
            usageRecorder.recordUnverifiable(
                message.getThread().getWorkspace().getId(),
                new LlmUsageRecorder.LlmUsageSample(
                    LlmUsageJobType.MENTOR_TURN,
                    LlmUsageSourceType.MENTOR_TURN,
                    message.getId(),
                    0,
                    admission.path("model").asString(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    price,
                    Instant.now()
                )
            );
        }
        if (!stale.isEmpty()) log.info("Mentor in-flight reaper accounted {} stuck row(s)", stale.size());
    }

    private static LlmPriceSnapshot readPrice(JsonNode node) {
        if (node.isMissingNode()) {
            return new LlmPriceSnapshot(
                FundingSource.INSTANCE,
                PricingState.UNPRICED,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
        return new LlmPriceSnapshot(
            FundingSource.valueOf(node.path("fundingSource").asString()),
            PricingState.valueOf(node.path("pricingState").asString()),
            longOrNull(node, "appliedPriceId"),
            longOrNull(node, "appliedWorkspaceModelId"),
            decimalOrNull(node, "per1mInputUsd"),
            decimalOrNull(node, "per1mOutputUsd"),
            decimalOrNull(node, "per1mCacheReadUsd"),
            decimalOrNull(node, "per1mCacheWriteUsd")
        );
    }

    private static @Nullable Long longOrNull(JsonNode node, String field) {
        return node.path(field).isNull() ? null : Long.valueOf(node.path(field).asString());
    }

    private static @Nullable BigDecimal decimalOrNull(JsonNode node, String field) {
        return node.path(field).isNull() ? null : new BigDecimal(node.path(field).asString());
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
