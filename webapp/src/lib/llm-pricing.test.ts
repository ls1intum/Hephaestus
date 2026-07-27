import { describe, expect, it } from "vitest";
import { priceLabel } from "./llm-pricing";
import { formatRateUsd } from "./money";

describe("priceLabel", () => {
	/**
	 * A price is a *rate*, and the label an admin compares against their provider's published price
	 * list has to carry the digits that provider printed. The spend formatter clamps to cents, which
	 * is right for money spent and wrong for a rate: it would turn $0.075 into "$0.08" and $0.003 into
	 * the literal "<$0.01", which is not a number at all. What the digits look like is
	 * `formatRateUsd`'s to decide and `money.test.ts`'s to state; the claim here is that the
	 * label is composed from *that* formatter rather than the other one.
	 */
	it("renders a sub-cent rate as the number the provider publishes", () => {
		expect(priceLabel({ pricingMode: "PRICED", per1mInputUsd: 0.075 }, "instance")).toBe(
			`${formatRateUsd(0.075)} / 1M input tokens`,
		);
		expect(priceLabel({ pricingMode: "PRICED", per1mInputUsd: 0.003 }, "workspace")).toBe(
			`${formatRateUsd(0.003)} / 1M input tokens`,
		);
	});

	it("names both halves of a two-sided price, each at the rate precision", () => {
		expect(
			priceLabel({ pricingMode: "PRICED", per1mInputUsd: 3, per1mOutputUsd: 15 }, "instance"),
		).toBe(`${formatRateUsd(3)} input · ${formatRateUsd(15)} output / 1M tokens`);
		expect(
			priceLabel({ pricingMode: "PRICED", per1mInputUsd: 0.15, per1mOutputUsd: 0.6 }, "workspace"),
		).toBe(`${formatRateUsd(0.15)} input · ${formatRateUsd(0.6)} output / 1M tokens`);
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
