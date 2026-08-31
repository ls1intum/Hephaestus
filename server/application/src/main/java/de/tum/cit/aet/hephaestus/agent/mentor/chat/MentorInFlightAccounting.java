package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.UsageProvenance;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.mentor.ChatMessage;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.MentorTurnLlmUsage;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Accounts stuck mentor turns across tenants")
public class MentorInFlightAccounting {

    private final ChatMessageRepository chatMessageRepository;
    private final LlmUsageRecorder usageRecorder;

    public MentorInFlightAccounting(ChatMessageRepository chatMessageRepository, LlmUsageRecorder usageRecorder) {
        this.chatMessageRepository = chatMessageRepository;
        this.usageRecorder = usageRecorder;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean account(UUID messageId) {
        // Re-read here so a turn completed after the sweep query is not billed as abandoned.
        ChatMessage message = chatMessageRepository.findById(messageId).orElse(null);
        if (message == null || message.getStatus() != ChatMessage.Status.in_flight) return false;
        JsonNode existingMetadata = message.getMetadata();
        LlmPriceSnapshot price = MentorAdmissionMetadata.readPrice(existingMetadata);
        message.setStatus(ChatMessage.Status.interrupted);
        message.setMetadata(withAbandonedError(existingMetadata));
        // End the in-flight state before reading usage so the proxy accumulator can no longer change the totals.
        chatMessageRepository.saveAndFlush(message);
        MentorTurnLlmUsage observed =
                chatMessageRepository.findLlmUsageById(message.getId()).orElse(MentorTurnLlmUsage.NONE);
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
                observed.hasBillableUsage() ? UsageProvenance.PROXY : UsageProvenance.NONE,
                Instant.now());
        if (observed.hasBillableUsage()) {
            usageRecorder.record(workspaceId, sample);
            return true;
        }
        usageRecorder.recordUnverifiable(workspaceId, sample);
        return false;
    }

    private static ObjectNode withAbandonedError(@Nullable JsonNode existingMetadata) {
        ObjectNode metadata = existingMetadata != null && existingMetadata.isObject()
                ? (ObjectNode) existingMetadata.deepCopy()
                : JsonNodeFactory.instance.objectNode();
        metadata.put("error", "server restart");
        return metadata;
    }
}
