package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * A malformed admission block is read inside the reaper's per-turn transaction, so anything thrown
 * there fails the same row on every later tick too — the turn is never billed and its thread never
 * takes another turn.
 */
class MentorAdmissionMetadataTest extends BaseUnitTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private static final LlmPriceSnapshot PRICED = new LlmPriceSnapshot(
        FundingSource.WORKSPACE,
        PricingState.PRICED,
        7L,
        11L,
        new BigDecimal("3.500000"),
        new BigDecimal("15.000000"),
        new BigDecimal("0.300000"),
        new BigDecimal("3.750000")
    );

    @Test
    @DisplayName("a written admission reads back as the same snapshot and model")
    void roundTripsEveryField() {
        ObjectNode metadata = MentorAdmissionMetadata.write("gpt-5-mini", PRICED);

        assertThat(MentorAdmissionMetadata.readModel(metadata)).isEqualTo("gpt-5-mini");
        assertThat(MentorAdmissionMetadata.readPrice(metadata)).isEqualTo(PRICED);
    }

    @Test
    @DisplayName("rates are written as strings, so the block cannot reach a JSON reader as a double")
    void writesRatesAsStrings() {
        ObjectNode rates = (ObjectNode) MentorAdmissionMetadata.write("gpt-5-mini", PRICED)
            .path("llmAdmission")
            .path("price");

        assertThat(rates.path("per1mInputUsd").isString()).isTrue();
        assertThat(rates.path("appliedPriceId").isString()).isTrue();
    }

    @Test
    @DisplayName("an unpriced snapshot round-trips as unpriced rather than as zero rates")
    void roundTripsUnpriced() {
        ObjectNode metadata = MentorAdmissionMetadata.write("gpt-5-mini", LlmPriceSnapshot.unpricedInstance());

        assertThat(MentorAdmissionMetadata.readPrice(metadata)).isEqualTo(LlmPriceSnapshot.unpricedInstance());
    }

    @Test
    @DisplayName("a rate field that is absent reads the same as one that is explicitly null")
    void treatsAnAbsentRateAsNoRate() {
        ObjectNode rates = NODES.objectNode();
        rates.put("fundingSource", "INSTANCE");
        rates.put("pricingState", "PRICED");
        rates.put("per1mInputUsd", "2.000000");

        LlmPriceSnapshot price = MentorAdmissionMetadata.readPrice(admissionWith(rates));

        assertThat(price.pricingState()).isEqualTo(PricingState.PRICED);
        assertThat(price.per1mInputUsd()).isEqualByComparingTo("2.000000");
        assertThat(price.appliedPriceId()).isNull();
        assertThat(price.per1mOutputUsd()).isNull();
    }

    @Test
    @DisplayName("an admission whose funding source is absent bills unpriced instead of throwing")
    void fallsBackToUnpricedWhenTheBlockCannotBeRead() {
        ObjectNode rates = NODES.objectNode();
        rates.put("pricingState", "PRICED");
        rates.put("per1mInputUsd", "2.000000");

        assertThatCode(() -> MentorAdmissionMetadata.readPrice(admissionWith(rates))).doesNotThrowAnyException();
        assertThat(MentorAdmissionMetadata.readPrice(admissionWith(rates))).isEqualTo(
            LlmPriceSnapshot.unpricedInstance()
        );
    }

    @Test
    @DisplayName("a turn with no metadata at all bills unpriced, and names no model")
    void toleratesAMissingAdmission() {
        assertThat(MentorAdmissionMetadata.readPrice(null)).isEqualTo(LlmPriceSnapshot.unpricedInstance());
        assertThat(MentorAdmissionMetadata.readPrice(NODES.objectNode())).isEqualTo(
            LlmPriceSnapshot.unpricedInstance()
        );
        assertThat(MentorAdmissionMetadata.readModel(null)).isEmpty();
    }

    private static ObjectNode admissionWith(ObjectNode rates) {
        ObjectNode admission = NODES.objectNode();
        admission.put("model", "gpt-5-mini");
        admission.set("price", rates);
        return NODES.objectNode().set("llmAdmission", admission);
    }
}
