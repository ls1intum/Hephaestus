package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the calls of one model kind of an attempt cost and which model served them, as the attempt froze it. A
 * later claim freezes a new configuration onto the job row, so a call that returns after its attempt was handed
 * on is priced from here and never from the row.
 *
 * @param model the upstream model id the ledger row names; {@code null} when the attempt froze no readable model
 */
public record FrozenCharge(@Nullable String model, LlmPriceSnapshot price) {

    /** The charge of a model whose price cannot be recovered: instance-funded and unpriced, so it reads unknown. */
    public static final FrozenCharge UNKNOWN = new FrozenCharge(null, LlmPriceSnapshot.unpricedInstance());

    /** The charge of one frozen model; a model admitted without a price stays unpriced. */
    public static FrozenCharge of(FrozenModel model) {
        LlmPriceSnapshot price = model.priceSnapshot();
        return new FrozenCharge(model.upstreamModelId(), price != null ? price : LlmPriceSnapshot.unpricedInstance());
    }

    /**
     * The charges of an attempt's own configuration, for an attempt that ends while the row still holds it.
     *
     * @param snapshot the attempt's configuration; {@code null} when it cannot be read, which leaves only the chat
     *     charge
     * @param chatPrice the price of the review's own model, as the terminal path admitted it
     */
    public static Map<ModelKind, FrozenCharge> admitted(@Nullable ConfigSnapshot snapshot, LlmPriceSnapshot chatPrice) {
        Map<ModelKind, FrozenCharge> charges = new EnumMap<>(ModelKind.class);
        charges.put(
                ModelKind.CHAT,
                new FrozenCharge(snapshot == null ? null : snapshot.model().upstreamModelId(), chatPrice));
        if (snapshot == null) return charges;
        for (ModelKind kind : ModelKind.values()) {
            FrozenModel model = kind == ModelKind.CHAT ? null : snapshot.practiceScriptSlot(kind);
            if (model != null) charges.put(kind, of(model));
        }
        return charges;
    }
}
