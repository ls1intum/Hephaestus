import { describe, expect, it } from "vitest";
import { formatCapUsd, formatCostUsd, formatRateUsd } from "./money";

describe("formatCostUsd", () => {
	it("renders nothing spent as $0, with no decimals at all", () => {
		// Zero is the one amount worth stating flat: "$0.00" reads as a measurement that came back
		// zero, "$0" as nothing having happened, which is what it means on a spend column.
		expect(formatCostUsd(0)).toBe("$0");
	});

	it("renders an amount too small for cents as <$0.01 rather than claiming $0.00", () => {
		expect(formatCostUsd(0.0004)).toBe("<$0.01");
		expect(formatCostUsd(0.004)).toBe("<$0.01");
	});

	it("renders everything else in cents", () => {
		expect(formatCostUsd(0.005)).toBe("$0.01");
		expect(formatCostUsd(0.85)).toBe("$0.85");
		expect(formatCostUsd(43)).toBe("$43.00");
	});

	it("renders an absent amount as an em dash", () => {
		expect(formatCostUsd(undefined)).toBe("—");
	});
});

describe("formatCapUsd", () => {
	it("drops the cents a round cap does not have, and keeps the ones it does", () => {
		expect(formatCapUsd(50)).toBe("$50");
		expect(formatCapUsd(0)).toBe("$0");
		expect(formatCapUsd(49.5)).toBe("$49.50");
	});

	it("renders no cap as an em dash", () => {
		expect(formatCapUsd(undefined)).toBe("—");
	});
});

describe("formatRateUsd", () => {
	it("keeps the digits the provider published rather than clamping to cents", () => {
		// A rate is not a spend figure. Clamped to cents, a real $0.075 / 1M rate reads as "$0.08" —
		// 6.7% off the number the admin is asked to check against their provider's price list.
		expect(formatRateUsd(0.075)).toBe("$0.075");
		expect(formatRateUsd(0.003)).toBe("$0.003");
		expect(formatCostUsd(0.003)).toBe("<$0.01");
	});

	it("never floors a rate to the sub-cent bound", () => {
		expect(formatRateUsd(0.0004)).toBe("$0.0004");
		expect(formatRateUsd(0.00004)).not.toContain("<");
	});

	it("still shows cents on a round rate", () => {
		expect(formatRateUsd(3)).toBe("$3.00");
		expect(formatRateUsd(0)).toBe("$0.00");
	});

	it("renders an absent rate as an em dash", () => {
		expect(formatRateUsd(undefined)).toBe("—");
	});
});
