package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The {@code llmAdmission} block of a mentor turn's {@code chat_message.metadata}: the rates the turn
 * was admitted with, written at admission and read back when {@link MentorInFlightReaper} bills a turn
 * whose worker died mid-stream.
 *
 * <p>Both directions live here because they are one format. Numbers are written as JSON strings: the
 * block reaches the browser through {@code ChatMessageDTO}, and a per-1M rate read into a binary64
 * there would no longer be the rate the turn was priced with.
 *
 * <p>{@link #readPrice} never throws. It is called inside the reaper's per-turn transaction, and a
 * throw there fails that turn's reap on every future tick as well — a row that can never be reaped is
 * a turn that can never be billed, and a thread that can never take another turn (the partial unique
 * in-flight index refuses one while the stuck row is there). So an admission block that cannot be read
 * yields {@link LlmPriceSnapshot#unpricedInstance()}: the month reads unverifiable, which is the honest
 * verdict and a terminal one.
 */
final class MentorAdmissionMetadata {

    private static final Logger log = LoggerFactory.getLogger(MentorAdmissionMetadata.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String ADMISSION = "llmAdmission";

    private MentorAdmissionMetadata() {}

    /** The full {@code metadata} value for a freshly admitted turn. */
    static ObjectNode write(String upstreamModelId, LlmPriceSnapshot price) {
        ObjectNode admission = NODES.objectNode();
        admission.put("model", upstreamModelId);
        ObjectNode rates = admission.putObject("price");
        rates.put("fundingSource", price.fundingSource().name());
        rates.put("pricingState", price.pricingState().name());
        putAsString(rates, "appliedPriceId", price.appliedPriceId());
        putAsString(rates, "appliedWorkspaceModelId", price.appliedWorkspaceModelId());
        putAsString(rates, "per1mInputUsd", price.per1mInputUsd());
        putAsString(rates, "per1mOutputUsd", price.per1mOutputUsd());
        putAsString(rates, "per1mCacheReadUsd", price.per1mCacheReadUsd());
        putAsString(rates, "per1mCacheWriteUsd", price.per1mCacheWriteUsd());
        return NODES.objectNode().set(ADMISSION, admission);
    }

    /** The upstream model the turn was admitted against; empty when the turn carries no admission. */
    static String readModel(@Nullable JsonNode metadata) {
        return admission(metadata).path("model").asString();
    }

    /** The rates the turn was admitted with, or an unpriced snapshot when they cannot be read. */
    static LlmPriceSnapshot readPrice(@Nullable JsonNode metadata) {
        JsonNode rates = admission(metadata).path("price");
        if (!rates.isObject()) {
            return LlmPriceSnapshot.unpricedInstance();
        }
        try {
            return new LlmPriceSnapshot(
                FundingSource.valueOf(rates.path("fundingSource").asString()),
                PricingState.valueOf(rates.path("pricingState").asString()),
                longOrNull(rates, "appliedPriceId"),
                longOrNull(rates, "appliedWorkspaceModelId"),
                decimalOrNull(rates, "per1mInputUsd"),
                decimalOrNull(rates, "per1mOutputUsd"),
                decimalOrNull(rates, "per1mCacheReadUsd"),
                decimalOrNull(rates, "per1mCacheWriteUsd")
            );
        } catch (RuntimeException e) {
            log.warn("Unreadable LLM admission on a mentor turn; billing it as unpriced rather than stranding it", e);
            return LlmPriceSnapshot.unpricedInstance();
        }
    }

    private static JsonNode admission(@Nullable JsonNode metadata) {
        return metadata == null ? NODES.missingNode() : metadata.path(ADMISSION);
    }

    private static void putAsString(ObjectNode node, String field, @Nullable Object value) {
        if (value == null) node.putNull(field);
        else node.put(field, value.toString());
    }

    private static @Nullable Long longOrNull(JsonNode node, String field) {
        String raw = textOrNull(node, field);
        return raw == null ? null : Long.valueOf(raw);
    }

    private static @Nullable BigDecimal decimalOrNull(JsonNode node, String field) {
        String raw = textOrNull(node, field);
        return raw == null ? null : new BigDecimal(raw);
    }

    /**
     * An absent field reads the same as an explicit null: both say this turn has no such rate. The
     * absent half is the one that is easy to lose — {@code path} yields a missing node, whose
     * {@code isNull()} is false and whose text is {@code ""}, so a null check alone sends the empty
     * string on to {@code Long.valueOf}.
     */
    private static @Nullable String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
