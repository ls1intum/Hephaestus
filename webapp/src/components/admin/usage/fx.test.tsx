import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { describe, expect, it } from "vitest";
import type { FxRateInfo, LlmUsageByDay, WorkspaceLlmUsageReport } from "@/api/types.gen";
import {
	capConversion,
	type Fx,
	FxAmount,
	FxDisclosure,
	FxSpendLine,
	fxCapHint,
	spendConversion,
	spendOfCapConversion,
} from "./fx";
import { LlmUsageByDayTable } from "./LlmUsageBreakdownTables";

/** Already inverted by the server, so USD × rate is the display amount. */
const eur: FxRateInfo = {
	currencyCode: "EUR",
	ratePerUsd: 0.878966,
	rateDate: new Date("2026-07-24T00:00:00.000Z"),
	source: "ECB",
};

const eurClosed: FxRateInfo = {
	...eur,
	rateDate: new Date("2026-06-30T00:00:00.000Z"),
};

/**
 * The month's spend as the server computed it, alongside the day rows. The totals are arguments
 * rather than a sum of `byDay`, because that is exactly the coupling the table must not have: the
 * footer prints what the server sent, not what the client can re-derive.
 */
function dayReport(
	byDay: LlmUsageByDay[],
	instanceTotalCostUsd: number,
	ownProviderTotalCostUsd = 0,
): WorkspaceLlmUsageReport {
	return {
		month: "2026-07",
		byDay,
		byJobType: [],
		instanceTotalCostUsd,
		ownProviderTotalCostUsd,
		unpricedEventCount: 0,
		instanceBudgetVerdict: "WITHIN",
		instancePaused: false,
		ownProviderBudgetVerdict: "WITHIN",
		ownProviderPaused: false,
	};
}

/** `en-US` puts a non-breaking space between an ISO code and the number. */
const NBSP = "\u00a0";

describe("spend conversion", () => {
	it("converts a spend figure at the spend precision", () => {
		expect(spendConversion(4.5, eur)?.text).toBe("≈ €3.96");
	});

	it("leaves zero spend unconverted", () => {
		expect(spendConversion(0, eur)).toBeNull();
	});

	it("leaves a sub-cent bound unconverted", () => {
		// "<$0.01" is a bound, not an amount, so a converted bound would be false precision.
		expect(spendConversion(0.004, eur)).toBeNull();
	});

	it("converts the smallest amount that is still a figure rather than a bound", () => {
		// Half a cent is exactly where `formatCostUsd` stops printing "<$0.01" and starts printing a
		// number, so it is the first amount there is something honest to convert. A rate big enough
		// that the result clears the currency's own smallest unit, or the second guard would decide it.
		const jpy: FxRateInfo = { ...eur, currencyCode: "JPY", ratePerUsd: 157.2 };
		expect(spendConversion(0.005, jpy)).not.toBeNull();
	});

	it("converts nothing when there is no rate", () => {
		expect(spendConversion(4.5, undefined)).toBeNull();
		expect(spendConversion(undefined, eur)).toBeNull();
	});
});

describe("cap conversion", () => {
	it("rounds a converted cap to whole units", () => {
		// 50 × 0.878966 = 43.9483 — "€43.95" beside a round "$50" would claim precision this estimate
		// does not have.
		expect(capConversion(50, eur)?.text).toBe("≈ €44");
	});

	it("leaves a $0 cap unconverted", () => {
		expect(capConversion(0, eur)).toBeNull();
	});

	it.each<[string, number, string]>([
		["free", 0.4, "≈ €0"],
		["nearly double", 0.6, "≈ €1"],
	])("says nothing rather than call a small cap %s", (_name, capUsd, wrongText) => {
		// $0.40 is €0.35 and $0.60 is €0.53; at whole units those render as the amounts named here,
		// neither of which a reader can discount as a rounding wobble. Below the first whole unit there
		// is no honest figure at this precision, so there is no figure.
		expect(capConversion(capUsd, eur)?.text).not.toBe(wrongText);
		expect(capConversion(capUsd, eur)).toBeNull();
	});

	it("converts nothing when there is no rate", () => {
		expect(capConversion(50, undefined)).toBeNull();
		expect(capConversion(undefined, eur)).toBeNull();
	});
});

describe("ambiguous currency symbols", () => {
	it("falls back to the ISO code when the symbol could be read as dollars", () => {
		const cad: FxRateInfo = { ...eur, currencyCode: "CAD" };
		expect(capConversion(50, cad)?.text).toBe(`≈ CAD${NBSP}44`);
	});

	it("keeps a currency's own glyph when it cannot be confused with USD", () => {
		// The policy is "a symbol without `$` in it keeps the symbol"; which glyph ICU picks for GBP is
		// the platform's business, so what is asserted is that the code was not substituted for it.
		const gbp: FxRateInfo = { ...eur, currencyCode: "GBP" };
		expect(capConversion(50, gbp)?.text).not.toContain("GBP");
	});

	it("shows USD alone rather than failing on an unknown currency code", () => {
		const nonsense: FxRateInfo = { ...eur, currencyCode: "NOTACODE" };
		expect(spendConversion(4.5, nonsense)).toBeNull();
		const { container } = render(<FxDisclosure fx={nonsense} isCurrentMonth />);
		expect(container.innerHTML).toBe("");
	});

	it("shows USD alone rather than dividing by a broken rate", () => {
		expect(spendConversion(4.5, { ...eur, ratePerUsd: 0 })).toBeNull();
		expect(spendConversion(4.5, { ...eur, ratePerUsd: Number.NaN })).toBeNull();
	});
});

describe("X of Y lines", () => {
	it("converts both sides at their own precision, written and spoken", () => {
		const conversion = spendOfCapConversion(43.9, 50, eur);
		expect(conversion?.text).toBe("≈ €38.59 of €44");
		expect(conversion?.label).toBe("approximately 38.59 euros of 44 euros");
	});

	it("stays USD-only when either side cannot convert", () => {
		expect(spendOfCapConversion(0, 50, eur)).toBeNull();
		expect(spendOfCapConversion(43.9, undefined, eur)).toBeNull();
	});
});

describe("totals convert the USD sum", () => {
	/**
	 * Each row rounds up on its own, so `Σ convert(row)` is €2.04 while `convert(Σ USD)` is €2.02 —
	 * the cent that would make a footer disagree with the column above it.
	 */
	const rows = [0.575, 0.575, 0.575, 0.575];

	it("renders the breakdown footer from the USD total", () => {
		const day = (costUsd: number, iso: string): LlmUsageByDay => ({
			day: new Date(iso),
			instanceTotalCostUsd: costUsd,
			ownProviderTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 1,
		});
		const byDay = rows.map((value, index) => day(value, `2026-07-0${index + 1}T00:00:00.000Z`));
		render(<LlmUsageByDayTable report={dayReport(byDay, 2.3)} fx={eur} />);

		const footer = screen.getByRole("row", { name: /^Total/ });
		expect(footer.textContent).toContain("$2.30");
		expect(footer.textContent).toContain("≈ €2.02");
		expect(footer.textContent).not.toContain("€2.04");
	});
});

describe("page disclosure", () => {
	/** The sentence as a reader sees it, with the parenthetical rate spliced back in. */
	function disclosureText(fx: Fx, isCurrentMonth: boolean): string | null {
		const { container } = render(<FxDisclosure fx={fx} isCurrentMonth={isCurrentMonth} />);
		return container.textContent === "" ? null : container.textContent;
	}

	it("quotes the live rate for the current month, and names who published it", () => {
		expect(disclosureText(eur, true)).toBe(
			"EUR amounts are estimates at the European Central Bank reference rate published on Jul 24, 2026 (1 USD ≈ €0.879). Spend is metered and enforced in USD.",
		);
	});

	it("explains why a closed month never moves", () => {
		expect(disclosureText(eurClosed, false)).toBe(
			"EUR amounts are estimates at the European Central Bank reference rate published on Jun 30, 2026 (1 USD ≈ €0.879). That is the last rate published in the month shown, so these figures no longer change.",
		);
	});

	// The attribution is the payload's claim, not ours. A source this build has no name for — an older
	// client meeting a newer server — drops back to the unattributed wording rather than crediting the
	// ECB for someone else's rate.
	it("declines to name a publisher it does not recognise", () => {
		const unknownSource = { ...eur, source: "SNB" as FxRateInfo["source"] };
		expect(disclosureText(unknownSource, true)).toBe(
			"EUR amounts are estimates at the reference rate published on Jul 24, 2026 (1 USD ≈ €0.879). Spend is metered and enforced in USD.",
		);
	});

	it("says the rate in words", () => {
		render(<FxDisclosure fx={eur} isCurrentMonth />);
		expect(screen.getByLabelText("1 US dollar is approximately 0.879 euros")).not.toBeNull();
	});

	it("reports nothing without a rate", () => {
		expect(disclosureText(undefined, true)).toBeNull();
	});

	it("coerces the ISO date the SDK actually returns", () => {
		const asString = { ...eur, rateDate: "2026-07-24" as unknown as Date };
		expect(disclosureText(asString, true)).toContain("Jul 24, 2026");
	});
});

describe("nothing to convert renders no node", () => {
	it.each<[string, ReactElement]>([
		["an inline amount", <FxAmount key="a" conversion={spendConversion(0, eur)} />],
		["a table cell's second line", <FxSpendLine key="b" usd={0} fx={eur} />],
		["the page disclosure", <FxDisclosure key="c" fx={undefined} isCurrentMonth />],
	])("leaves no wrapper behind for %s", (_label, element) => {
		const { container } = render(element);
		expect(container.innerHTML).toBe("");
	});
});

describe("the live hint under a cap field", () => {
	it("rounds to whole units, like every other cap figure", () => {
		// `€43.95` beside a round `$50` claims a precision the estimate does not have.
		expect(fxCapHint(50, eur, true)).toEqual({
			conversion: { text: "≈ €44", label: "approximately 44 euros" },
			tail: " at today's rate.",
		});
	});

	it.each<[string, number | null | undefined]>([
		["the field is empty", null],
		["the field has not been touched", undefined],
		["the amount is zero", 0],
		["the amount is unparseable", Number.NaN],
	])("stays absent rather than flickering an estimate while %s", (_name, value) => {
		expect(fxCapHint(value, eur, true)).toBeNull();
	});

	it("stays absent when the instance configured no display currency", () => {
		expect(fxCapHint(50, undefined, true)).toBeNull();
	});

	it("withdraws the estimate on a closed month rather than dating it wrong", () => {
		expect(fxCapHint(50, eur, false)).toBeNull();
	});
});

describe("screen-reader wording", () => {
	it("speaks the estimate as words rather than symbols", () => {
		render(<FxAmount conversion={spendConversion(4.5, eur)} />);
		const amount = screen.getByLabelText("approximately 3.96 euros");
		expect(amount.textContent).toBe("(≈ €3.96)");
		// `aria-label` is dropped on a bare generic span, so the wrapper must carry a role.
		expect(amount.getAttribute("role")).toBe("img");
	});
});

describe("without a configured currency", () => {
	it("leaves no trace of the feature in the markup", () => {
		const rows: LlmUsageByDay[] = [
			{
				day: new Date("2026-07-05T00:00:00.000Z"),
				instanceTotalCostUsd: 4.25,
				ownProviderTotalCostUsd: 1.75,
				unpricedEventCount: 0,
				events: 3,
			},
		];
		const { container } = render(<LlmUsageByDayTable report={dayReport(rows, 4.25, 1.75)} />);

		// Nothing is announced as a converted figure, and nothing is shown as one.
		expect(screen.queryAllByRole("img")).toHaveLength(0);
		expect(container.textContent).not.toContain("€");
		expect(container.textContent).not.toContain("reference rate");
	});
});
