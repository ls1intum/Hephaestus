import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { formatCapUsd, formatCostUsd, formatRateUsd, MoneyCell } from "./jobUtils";

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

describe("formatRateUsd", () => {
	it("keeps the decimals a published price actually has", () => {
		// The spend formatter clamps to cents, which turned a real $0.075 / 1M rate into "$0.08" —
		// 6.7% off the number the admin is asked to check against their provider's price list.
		expect(formatRateUsd(0.075)).toBe("$0.075");
		expect(formatRateUsd(0.003)).toBe("$0.003");
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

describe("MoneyCell", () => {
	/** One rendered cell, scoped to its own container so a test can render several. */
	function cellText(value: string) {
		const { container } = render(
			<span data-testid="cell">
				<MoneyCell>{value}</MoneyCell>
			</span>,
		);
		return container.firstElementChild as HTMLElement;
	}

	it("reserves the width of the cents a whole-dollar figure doesn't print", () => {
		// `tabular-nums` equalises glyph width but not a missing ".00", so "$0" and "$4.50" would
		// right-align with their decimal points in different places.
		const cell = cellText("$0");
		const pad = cell.querySelector("span");
		expect(pad?.textContent).toBe(".00");
		expect(pad?.className).toContain("invisible");
		expect(pad?.getAttribute("aria-hidden")).toBe("true");
	});

	it("adds nothing to a figure that already prints its decimals", () => {
		expect(cellText("$4.50").querySelector("span")).toBeNull();
		// "<$0.01" already ends in cents, so its decimal point lands where "$4.50"'s does.
		expect(cellText("<$0.01").querySelector("span")).toBeNull();
	});

	it("keeps the padding out of what is read and announced", () => {
		const cell = cellText("$0");
		// Testing Library reads direct text nodes, so the cell still answers to its visible figure.
		expect(screen.getByText("$0")).toBe(cell);
	});
});
