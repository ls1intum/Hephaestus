package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.cit.aet.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.cit.aet.hephaestus.agent.pricing.ModelPricingService;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
import de.tum.cit.aet.hephaestus.mentor.ChatThreadRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * B6: the Pi-cost extraction + sanity-cap path (augmentFinishWithCost / computeFinalCostUsd / extractPiCostUsd
 * / COST_USD_SANITY_CAP) feeds both the {@code chat_message.metadata.costUsd} audit row and the long-lived
 * {@code mentor.turn.cost.usd} histogram. A {@code >}->{@code >=} or dropped-isFinite regression would poison
 * both for the lifetime of the registry, yet it had zero coverage. These unit tests pin the guard.
 */
class MentorTurnPersistenceCostTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    ChatThreadRepository chatThreadRepository;

    @Mock
    ChatMessageRepository chatMessageRepository;

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    ModelPricingService pricingService;

    private MentorTurnPersistence persistence() {
        return new MentorTurnPersistence(
            chatThreadRepository,
            chatMessageRepository,
            workspaceRepository,
            pricingService
        );
    }

    private static TranslatorState stateWithPiCost(String costJson) {
        TranslatorState state = new TranslatorState(UUID.randomUUID());
        JsonNode usage = MAPPER.readTree("{\"cost\":{\"total\":" + costJson + "}}");
        state.observeUsage(usage);
        return state;
    }

    private static UIMessageChunk.Finish bareFinish() {
        return new UIMessageChunk.Finish(UIMessageChunk.FinishReason.STOP, null);
    }

    @Test
    void validPiCostIsInjectedIntoFinishMetadata() {
        UIMessageChunk.Finish out = persistence().augmentFinishWithCost(bareFinish(), stateWithPiCost("0.0123"));
        assertThat(out.messageMetadata()).isNotNull();
        assertThat(out.messageMetadata().costUsd()).isEqualTo(0.0123);
    }

    @Test
    void costAtTheSanityCapIsAccepted_aboveIsRejected() {
        // The cap is inclusive ( v > CAP is rejected ), so exactly 100.0 is kept and 100.01 is dropped.
        UIMessageChunk.Finish atCap = persistence().augmentFinishWithCost(bareFinish(), stateWithPiCost("100.0"));
        assertThat(atCap.messageMetadata()).isNotNull();
        assertThat(atCap.messageMetadata().costUsd()).isEqualTo(100.0);

        UIMessageChunk.Finish overCap = persistence().augmentFinishWithCost(bareFinish(), stateWithPiCost("100.01"));
        // No cost computable → finish returned unchanged (no metadata synthesised).
        assertThat(overCap.messageMetadata()).isNull();
    }

    @Test
    void negativeCostIsRejected() {
        UIMessageChunk.Finish out = persistence().augmentFinishWithCost(bareFinish(), stateWithPiCost("-50.0"));
        assertThat(out.messageMetadata()).isNull();
    }

    @Test
    void absurdlyLargeCostIsRejected() {
        UIMessageChunk.Finish out = persistence().augmentFinishWithCost(bareFinish(), stateWithPiCost("1000000000"));
        assertThat(out.messageMetadata()).isNull();
    }

    @Test
    void noUsageObserved_returnsFinishUnchanged() {
        // No Pi cost AND no usage breakdown → computeFinalCostUsd returns null → finish unchanged.
        TranslatorState empty = new TranslatorState(UUID.randomUUID());
        UIMessageChunk.Finish in = bareFinish();
        assertThat(persistence().augmentFinishWithCost(in, empty)).isSameAs(in);
    }

    @Test
    void nonObjectCostBlockIsIgnored() {
        // usage.cost present but not an object → extractPiCostUsd returns null.
        TranslatorState state = new TranslatorState(UUID.randomUUID());
        state.observeUsage(MAPPER.readTree("{\"cost\":42}"));
        assertThat(persistence().augmentFinishWithCost(bareFinish(), state).messageMetadata()).isNull();
    }
}
