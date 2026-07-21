import type { LlmModel } from "@/api/types.gen";
import { formatCostUsd } from "@/components/admin/ai/jobUtils";

export type PricingMode = "PRICED" | "FREE" | "UNPRICED";

export interface PriceFields {
	pricingMode: PricingMode;
	per1mInputUsd?: number;
}

/** An instance catalog model's price lives in `currentPrice`, absent until one has ever been set. */
export function priceFieldsOf(model: Pick<LlmModel, "currentPrice">): PriceFields {
	return {
		pricingMode: model.currentPrice?.pricingMode ?? "UNPRICED",
		per1mInputUsd: model.currentPrice?.per1mInputUsd,
	};
}

/**
 * The #1368 glossary's price framing — the *only* place this word choice may live. Never render the
 * words "Priced" / "Unpriced" / "Unverifiable"; PRICED always shows the number itself, and FREE/UNPRICED
 * read differently depending on who's looking (an instance admin owns the price; a workspace admin only
 * ever sees the outcome).
 */
export function priceLabel(model: PriceFields, audience: "instance" | "workspace"): string {
	if (model.pricingMode === "FREE") {
		return audience === "instance" ? "Free" : "No budget cost";
	}
	if (model.pricingMode === "UNPRICED" || model.per1mInputUsd == null) {
		return audience === "instance" ? "No price set" : "Price not set";
	}
	return `${formatCostUsd(model.per1mInputUsd)} / 1M input tokens`;
}
