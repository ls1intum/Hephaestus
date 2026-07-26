import { describe, expect, it } from "vitest";
import { priceLabel } from "./llm-pricing";

describe("priceLabel", () => {
	/**
	 * A price is a *rate*, and these strings are what an admin compares against their provider's
	 * published price list. Rendered through the spend formatter they were quietly wrong: a real
	 * $0.075 / 1M rate rounded up to "$0.08" (a 6.7% misstatement) and $0.003 collapsed to the
	 * literal string "<$0.01", which is not a number at all.
	 */
	it("renders a sub-cent rate as the number the provider publishes", () => {
		expect(priceLabel({ pricingMode: "PRICED", per1mInputUsd: 0.075 }, "instance")).toBe(
			"$0.075 / 1M input tokens",
		);
		expect(priceLabel({ pricingMode: "PRICED", per1mInputUsd: 0.003 }, "workspace")).toBe(
			"$0.003 / 1M input tokens",
		);
	});

	it("keeps cents on a whole-dollar rate so a price column stays a price column", () => {
		expect(
			priceLabel({ pricingMode: "PRICED", per1mInputUsd: 3, per1mOutputUsd: 15 }, "instance"),
		).toBe("$3.00 input · $15.00 output / 1M tokens");
	});

	it("names both halves of a two-sided price", () => {
		expect(
			priceLabel({ pricingMode: "PRICED", per1mInputUsd: 0.15, per1mOutputUsd: 0.6 }, "workspace"),
		).toBe("$0.15 input · $0.60 output / 1M tokens");
	});

	it("says who can fix a missing price", () => {
		expect(priceLabel({ pricingMode: "UNPRICED" }, "instance")).toBe("No price set");
		expect(priceLabel({ pricingMode: "UNPRICED" }, "workspace")).toBe("Price not set");
		// PRICED with no amount on record is the same hole, whatever the mode claims.
		expect(priceLabel({ pricingMode: "PRICED" }, "instance")).toBe("No price set");
	});

	it("never says the word 'unpriced' to either audience", () => {
		expect(priceLabel({ pricingMode: "NO_CHARGE" }, "instance")).toBe("No metered API cost");
		expect(priceLabel({ pricingMode: "NO_CHARGE" }, "workspace")).toBe("No metered API cost");
	});
});
