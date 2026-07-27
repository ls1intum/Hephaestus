import { describe, expect, it } from "vitest";
import { priceLabel } from "./llm-pricing";
import { formatRateUsd } from "./money";

describe("priceLabel", () => {
	it("composes a sub-cent price from the rate formatter, not the spend formatter", () => {
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

	it("says who can fix a missing price, including a PRICED model with no amount on record", () => {
		expect(priceLabel({ pricingMode: "UNPRICED" }, "instance")).toBe("No price set");
		expect(priceLabel({ pricingMode: "UNPRICED" }, "workspace")).toBe("Price not set");
		expect(priceLabel({ pricingMode: "PRICED" }, "instance")).toBe("No price set");
	});

	it("never says the word 'unpriced' to either audience", () => {
		expect(priceLabel({ pricingMode: "NO_CHARGE" }, "instance")).toBe("No metered API cost");
		expect(priceLabel({ pricingMode: "NO_CHARGE" }, "workspace")).toBe("No metered API cost");
	});
});
