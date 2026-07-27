import type { LlmModel } from "@/api/types.gen";
import { formatRateUsd } from "./money";

export type PricingMode = "PRICED" | "NO_CHARGE" | "UNPRICED";

/** Who is being addressed. The instance catalog and a workspace's own provider word things differently. */
export type LlmAudience = "instance" | "workspace";

export interface PriceFields {
	pricingMode: PricingMode;
	per1mInputUsd?: number;
	per1mOutputUsd?: number;
}

/** An instance catalog model's price lives in `currentPrice`, absent until one has ever been set. */
export function priceFieldsOf(model: Pick<LlmModel, "currentPrice">): PriceFields {
	return {
		pricingMode: model.currentPrice?.pricingMode ?? "UNPRICED",
		per1mInputUsd: model.currentPrice?.per1mInputUsd,
		per1mOutputUsd: model.currentPrice?.per1mOutputUsd,
	};
}

/**
 * The only place the price wording lives (`docs/contributor/llm-cost-vocabulary.md`, rule 4). Never render the words
 * "Priced" / "Unpriced" / "Unverifiable": PRICED always shows the number itself, and
 * NO_CHARGE/UNPRICED read differently depending on who is looking.
 */
export function priceLabel(model: PriceFields, audience: "instance" | "workspace"): string {
	if (model.pricingMode === "NO_CHARGE") {
		return "No metered API cost";
	}
	if (model.pricingMode === "UNPRICED" || model.per1mInputUsd == null) {
		return audience === "instance" ? "No price set" : "Price not set";
	}
	// A published price, not spend: `formatRateUsd`, never `formatCostUsd`. The spend formatter
	// clamps to cents, so a real $0.075 rate would render as "$0.08" and $0.003 as "<$0.01" — a
	// misstated number in the one place an admin checks ours against their provider's price list.
	if (model.per1mOutputUsd == null) {
		return `${formatRateUsd(model.per1mInputUsd)} / 1M input tokens`;
	}
	return `${formatRateUsd(model.per1mInputUsd)} input · ${formatRateUsd(model.per1mOutputUsd)} output / 1M tokens`;
}
