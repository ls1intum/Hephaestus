import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { formatCapUsd, formatCostUsd, formatRateUsd, MoneyCell } from "./job-utils";

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
		expect(formatCostUsd(0.85)).toBe("$0.85");
		expect(formatCostUsd(0.005)).toBe("$0.01");
		expect(formatCostUsd(42)).toBe("$42.00");
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
		// A rate is not a spend figure. Clamped to cents, a real $0.075 / 1M rate reads as "$0.08" —
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

	it("leaves a figure that already prints its cents exactly as it is", () => {
		// The pad is `visibility: hidden` and `aria-hidden`, so it is neither seen nor announced — it
		// reserves column width and nothing else, which is a Chromatic matter and not assertable here.
		// What *is* assertable is the failure the guard prevents: padding a figure that already ends
		// in cents renders "$4.50.00", which is visible and wrong.
		render(
			<table>
				<tbody>
					<tr>
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

		const [cents, bound] = screen.getAllByRole("cell");
		expect(cents.textContent).toBe("$4.50");
		// Already ends in cents, so its decimal point already lands where "$4.50"'s does.
		expect(bound.textContent).toBe("<$0.01");
	});
});
