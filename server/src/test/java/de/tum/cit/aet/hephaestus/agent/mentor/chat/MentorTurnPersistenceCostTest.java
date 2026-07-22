package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalDeliveryReconciler;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.agent.pricing.ModelPricingService;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code augmentFinishWithCost} / {@code computeFinalCostUsd} — the mentor chat's wire-facing cost
 * estimate for the live Finish chunk + {@code chat_message.metadata.costUsd} (#1368 slice 6).
 *
 * <p>Historically this preferred a cost Pi itself reported on {@code Usage.cost.total} (with a sanity
 * cap), falling back to {@link ModelPricingService} only when Pi reported nothing. Slice 6 removed the
 * Pi-side path as dead code: the runner registers only zero SDK-local rates (see
 * {@code pi-provider.mjs}), so Pi can never populate a real {@code Usage.cost.total} — the extraction +
 * sanity-cap guard tested nothing but a payload shape the runner can no longer produce.
 * {@link ModelPricingService}'s global per-model table is now the only source; these tests pin that
 * fallback (unrelated to and unaffected by the runner change — {@code chat_message.metadata.costUsd}
 * is a wire-facing estimate, distinct from the ledger's own catalog-derived cost, which
 * {@code LlmUsageRecorder} resolves separately and later — see {@code MentorTurnPersistence#billTurn}).
 */
class MentorTurnPersistenceCostTest extends BaseUnitTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Mock
    ChatThreadRepository chatThreadRepository;

    @Mock
    ChatMessageRepository chatMessageRepository;

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    ModelPricingService pricingService;

    @Mock
    ConversationalDeliveryReconciler conversationalDeliveryReconciler;

    @Mock
    de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder usageRecorder;

    // Unused by these tests (they exercise pure cost math), but the persistence constructor builds
    // its REQUIRES_NEW template from it.
    @Mock
    org.springframework.transaction.PlatformTransactionManager transactionManager;

    private MentorTurnPersistence persistence() {
        return new MentorTurnPersistence(
            chatThreadRepository,
            chatMessageRepository,
            workspaceRepository,
            pricingService,
            conversationalDeliveryReconciler,
            usageRecorder,
            transactionManager
        );
    }

    private static TranslatorState stateWithUsage(
        String model,
        long input,
        long output,
        long cacheRead,
        long cacheWrite
    ) {
        TranslatorState state = new TranslatorState(UUID.randomUUID());
        state.observeModel(model);
        ObjectNode usage = NODES.objectNode();
        usage.put("input", input).put("output", output).put("cacheRead", cacheRead).put("cacheWrite", cacheWrite);
        state.observeUsage(usage);
        return state;
    }

    private static UIMessageChunk.Finish bareFinish() {
        return new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null);
    }

    @Test
    void pricingTableCostIsInjectedIntoFinishMetadata() {
        when(pricingService.computeCost(eq("gpt-5"), eq(1000L), eq(200L), eq(0L), eq(0L))).thenReturn(
            Optional.of(new BigDecimal("0.0123"))
        );

        UIMessageChunk.Finish out = persistence().augmentFinishWithCost(
            bareFinish(),
            stateWithUsage("gpt-5", 1000, 200, 0, 0)
        );

        assertThat(out.messageMetadata()).isNotNull();
        assertThat(out.messageMetadata().costUsd()).isEqualTo(0.0123);
    }

    @Test
    void noPricingRowMeansNoCostComputable() {
        when(pricingService.computeCost(eq("unknown-model"), eq(1000L), eq(200L), eq(0L), eq(0L))).thenReturn(
            Optional.empty()
        );

        UIMessageChunk.Finish in = bareFinish();
        UIMessageChunk.Finish out = persistence().augmentFinishWithCost(
            in,
            stateWithUsage("unknown-model", 1000, 200, 0, 0)
        );

        // No cost computable → finish returned unchanged (no metadata synthesised).
        assertThat(out).isSameAs(in);
    }

    @Test
    void noUsageObserved_returnsFinishUnchanged() {
        // No usage breakdown at all → computeFinalCostUsd returns null → finish unchanged. The
        // pricing table is never consulted (nothing to price).
        TranslatorState empty = new TranslatorState(UUID.randomUUID());
        UIMessageChunk.Finish in = bareFinish();

        assertThat(persistence().augmentFinishWithCost(in, empty)).isSameAs(in);
        verify(pricingService, never()).computeCost(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void modelWithZeroTokensReturnsFinishUnchangedWithoutConsultingThePricingTable() {
        // A model observed but no tokens burned (e.g. a turn that errored before any LLM call) is
        // nothing to price — short-circuits before the pricing table lookup.
        TranslatorState state = stateWithUsage("gpt-5", 0, 0, 0, 0);
        UIMessageChunk.Finish in = bareFinish();

        assertThat(persistence().augmentFinishWithCost(in, state)).isSameAs(in);
        verify(pricingService, never()).computeCost(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    void cacheTokensAreIncludedInThePricingTableLookup() {
        when(pricingService.computeCost(eq("gpt-5"), eq(1000L), eq(200L), eq(50L), eq(10L))).thenReturn(
            Optional.of(new BigDecimal("0.05"))
        );

        UIMessageChunk.Finish out = persistence().augmentFinishWithCost(
            bareFinish(),
            stateWithUsage("gpt-5", 1000, 200, 50, 10)
        );

        assertThat(out.messageMetadata()).isNotNull();
        assertThat(out.messageMetadata().costUsd()).isEqualTo(0.05);
    }
}
