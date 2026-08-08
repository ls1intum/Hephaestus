import type { LlmModel } from "@/api/types.gen";
import { formatRateUsd } from "./money";

export type PricingMode = "PRICED" | "NO_CHARGE" | "UNPRICED";

export type LlmAudience = "instance" | "workspace";

export interface PriceFields {
	pricingMode: PricingMode;
	per1mInputUsd?: number;
	per1mOutputUsd?: number;
}

export function priceFieldsOf(model: Pick<LlmModel, "currentPrice">): PriceFields {
	return {
		pricingMode: model.currentPrice?.pricingMode ?? "UNPRICED",
		per1mInputUsd: model.currentPrice?.per1mInputUsd,
		per1mOutputUsd: model.currentPrice?.per1mOutputUsd,
	};
}

/**
 * The `PricingMode` names are never rendered: a price shows as the number itself, and a missing one
 * reads differently to an instance operator than to a workspace admin.
 */
export function priceLabel(model: PriceFields, audience: LlmAudience): string {
	if (model.pricingMode === "NO_CHARGE") {
		return "No metered API cost";
	}
	if (model.pricingMode === "UNPRICED" || model.per1mInputUsd == null) {
		return audience === "instance" ? "No price set" : "Price not set";
	}
	if (model.per1mOutputUsd == null) {
		return `${formatRateUsd(model.per1mInputUsd)} / 1M input tokens`;
	}
	return `${formatRateUsd(model.per1mInputUsd)} input · ${formatRateUsd(model.per1mOutputUsd)} output / 1M tokens`;
}
