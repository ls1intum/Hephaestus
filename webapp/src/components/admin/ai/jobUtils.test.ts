import { describe, expect, it } from "vitest";
import { formatCapUsd, formatCostUsd } from "./jobUtils";

describe("formatCostUsd", () => {
	it("renders nothing spent as $0, not a stray third decimal", () => {
		// Regression: the old formatter used 3 decimals below $1, so zero rendered as "$0.000".
		expect(formatCostUsd(0)).toBe("$0");
	});

	it("renders an amount too small for cents as <$0.01 rather than claiming $0.00", () => {
		expect(formatCostUsd(0.0004)).toBe("<$0.01");
		expect(formatCostUsd(0.004)).toBe("<$0.01");
	});

	it("renders everything else in cents", () => {
		expect(formatCostUsd(0.85)).toBe("$0.85");
		expect(formatCostUsd(0.005)).toBe("$0.01");
		expect(formatCostUsd(42)).toBe("$42.00");
	});

	it("groups thousands so large spend stays readable", () => {
		expect(formatCostUsd(1234.5)).toBe("$1,234.50");
	});

	it("renders an absent amount as an em dash", () => {
		expect(formatCostUsd(undefined)).toBe("—");
	});
});

describe("formatCapUsd", () => {
	it("drops trailing cents from a round cap", () => {
		expect(formatCapUsd(50)).toBe("$50");
		expect(formatCapUsd(0)).toBe("$0");
	});

	it("keeps cents when the cap actually has them", () => {
		expect(formatCapUsd(49.5)).toBe("$49.50");
	});

	it("renders no cap as an em dash", () => {
		expect(formatCapUsd(undefined)).toBe("—");
	});
});
