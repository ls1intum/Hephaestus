import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { formatCapUsd, formatCostUsd, formatRateUsd, MoneyCell } from "./job-utils";

describe("formatCostUsd", () => {
	it("renders nothing spent as $0, not a stray third decimal", () => {
		// Three decimals below $1 would render zero as "$0.000", which reads as a measured amount.
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
	it("announces the figure without the cents it pads the column with", () => {
		render(
			<table>
				<tbody>
					<tr>
						<td>
							<MoneyCell>$0</MoneyCell>
						</td>
					</tr>
				</tbody>
			</table>,
		);

		// The pad is `aria-hidden`, so it is absent from the accessible name — announcing "$0.00"
		// would be a different amount of money.
		expect(screen.getByRole("row", { name: "$0" })).toBeTruthy();
	});

	it("pads only the figures that are missing their cents", () => {
		// The column alignment this exists for: "$0" is widened to "$4.50"'s width, and a figure that
		// already prints its decimals is left alone rather than rendered "$4.50.00".
		render(
			<table>
				<tbody>
					<tr>
						<td>
							<MoneyCell>$0</MoneyCell>
						</td>
						<td>
							<MoneyCell>$4.50</MoneyCell>
						</td>
						<td>
							<MoneyCell>{"<$0.01"}</MoneyCell>
						</td>
					</tr>
				</tbody>
			</table>,
		);

		// The three cells in the order they were written. Read by role rather than by test id, and by
		// `textContent` rather than by accessible name, because the pad is `aria-hidden` — it is exactly
		// the part of the cell that is seen and not announced.
		const [whole, cents, bound] = screen.getAllByRole("cell");
		expect(whole.textContent).toBe("$0.00");
		expect(cents.textContent).toBe("$4.50");
		// Already ends in cents, so its decimal point already lands where "$4.50"'s does.
		expect(bound.textContent).toBe("<$0.01");
	});
});
