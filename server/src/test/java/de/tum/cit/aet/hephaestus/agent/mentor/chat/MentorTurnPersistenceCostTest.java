package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalDeliveryReconciler;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.node.JsonNodeFactory;

class MentorTurnPersistenceCostTest extends BaseUnitTest {

    private MentorTurnPersistence persistence() {
        return new MentorTurnPersistence(
            mock(ChatThreadRepository.class),
            mock(ChatMessageRepository.class),
            mock(WorkspaceRepository.class),
            mock(ConversationalDeliveryReconciler.class),
            mock(LlmUsageRecorder.class),
            mock(PlatformTransactionManager.class)
        );
    }

    private static TranslatorState state(long input, long output, long cacheRead, long cacheWrite, PricingState mode) {
        TranslatorState state = new TranslatorState(UUID.randomUUID());
        state.bindAdmission(
            "authoritative-model",
            new LlmPriceSnapshot(
                FundingSource.INSTANCE,
                mode,
                12L,
                null,
                new BigDecimal("10"),
                new BigDecimal("11.5"),
                new BigDecimal("20"),
                new BigDecimal("30")
            )
        );
        var usage = JsonNodeFactory.instance.objectNode();
        usage.put("input", input).put("output", output).put("cacheRead", cacheRead).put("cacheWrite", cacheWrite);
        state.observeUsage(usage);
        return state;
    }

    private static UIMessageChunk.Finish finish() {
        return new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null);
    }

    @Test
    void streamedCostUsesTheSameFrozenCatalogPriceAsTheLedger() {
        UIMessageChunk.Finish out = persistence().augmentFinishWithCost(
            finish(),
            state(1000, 200, 0, 0, PricingState.PRICED)
        );

        assertThat(out.messageMetadata().model()).isEqualTo("authoritative-model");
        assertThat(out.messageMetadata().costUsd()).isEqualTo(0.0123);
    }

    @Test
    void cacheBucketsAreAdditiveAndReasoningIsNotDoubleCharged() {
        UIMessageChunk.Finish out = persistence().augmentFinishWithCost(
            finish(),
            state(0, 0, 500, 100, PricingState.PRICED)
        );

        assertThat(out.messageMetadata().costUsd()).isEqualTo(0.013);
    }

    @Test
    void unpricedAdmissionLeavesFinishUnchanged() {
        UIMessageChunk.Finish in = finish();
        assertThat(persistence().augmentFinishWithCost(in, state(1000, 200, 0, 0, PricingState.UNPRICED))).isSameAs(in);
    }
}
