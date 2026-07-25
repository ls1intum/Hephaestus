import { cleanup, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { FxRateInfo, LlmUsageByDay } from "@/api/types.gen";
import { formatCapUsd, formatCostUsd } from "@/components/admin/ai/jobUtils";
import {
	capConversion,
	FxAmount,
	FxDisclosure,
	formatCapWithFx,
	formatSpendOfCapWithFx,
	formatSpendWithFx,
	fxCapHint,
	fxDisclosureText,
	spendConversion,
	spendOfCapConversion,
} from "./fx";
import { LlmUsageByDayTable } from "./LlmUsageBreakdownTables";

/** The rate the server sends: already inverted, so USD × rate is the display amount. */
const eur: FxRateInfo = {
	currencyCode: "EUR",
	ratePerUsd: 0.878966,
	rateDate: new Date("2026-07-24T00:00:00.000Z"),
};

/** A closed month resolves to a rate dated inside it, which freezes that month's figures. */
const eurClosed: FxRateInfo = {
	...eur,
	rateDate: new Date("2026-06-30T00:00:00.000Z"),
};

/** `en-US` puts a non-breaking space between an ISO code and the number. */
const NBSP = " ";

describe("spend formatting", () => {
	it("shows the estimate after the dollar amount", () => {
		expect(formatSpendWithFx(4.5, eur)).toBe("$4.50 (≈ €3.96)");
	});

	it("leaves zero spend unconverted", () => {
		expect(formatSpendWithFx(0, eur)).toBe("$0");
		expect(spendConversion(0, eur)).toBeNull();
	});

	it("leaves a sub-cent bound unconverted", () => {
		expect(formatSpendWithFx(0.004, eur)).toBe("<$0.01");
		expect(spendConversion(0.004, eur)).toBeNull();
	});

	it("returns exactly the USD string when there is no rate", () => {
		expect(formatSpendWithFx(4.5, undefined)).toBe(formatCostUsd(4.5));
		expect(formatSpendWithFx(0, undefined)).toBe(formatCostUsd(0));
		expect(formatSpendWithFx(undefined, undefined)).toBe(formatCostUsd(undefined));
		expect(spendConversion(4.5, undefined)).toBeNull();
	});
});

describe("cap formatting", () => {
	it("rounds a converted cap to whole units", () => {
		// 50 × 0.878966 = 43.9483 — "€43.95" beside a round "$50" would claim precision this estimate
		// does not have.
		expect(formatCapWithFx(50, eur)).toBe("$50 (≈ €44)");
	});

	it("keeps the cents the cap itself was typed with", () => {
		expect(formatCapWithFx(49.5, eur)).toBe("$49.50 (≈ €44)");
	});

	it("leaves a $0 cap unconverted", () => {
		expect(formatCapWithFx(0, eur)).toBe("$0");
	});

	it("returns exactly the USD string when there is no rate", () => {
		expect(formatCapWithFx(50, undefined)).toBe(formatCapUsd(50));
		expect(formatCapWithFx(undefined, undefined)).toBe(formatCapUsd(undefined));
		expect(capConversion(50, undefined)).toBeNull();
	});
});

describe("ambiguous currency symbols", () => {
	it("falls back to the ISO code when the symbol could be read as dollars", () => {
		const cad: FxRateInfo = { ...eur, currencyCode: "CAD" };
		expect(formatCapWithFx(50, cad)).toBe(`$50 (≈ CAD${NBSP}44)`);
	});

	it("keeps a currency's own glyph when it cannot be confused with USD", () => {
		const gbp: FxRateInfo = { ...eur, currencyCode: "GBP" };
		expect(formatCapWithFx(50, gbp)).toBe("$50 (≈ £44)");
	});

	it("shows USD alone rather than failing on an unknown currency code", () => {
		const nonsense: FxRateInfo = { ...eur, currencyCode: "NOTACODE" };
		expect(formatSpendWithFx(4.5, nonsense)).toBe("$4.50");
		expect(fxDisclosureText(nonsense, true)).toBeNull();
	});

	it("shows USD alone rather than dividing by a broken rate", () => {
		expect(formatSpendWithFx(4.5, { ...eur, ratePerUsd: 0 })).toBe("$4.50");
		expect(formatSpendWithFx(4.5, { ...eur, ratePerUsd: Number.NaN })).toBe("$4.50");
	});
});

describe("X of Y lines", () => {
	it("converts both sides at their own precision", () => {
		expect(formatSpendOfCapWithFx(43.9, 50, eur)).toBe("$43.90 of $50 (≈ €38.59 of €44)");
	});

	it("stays USD-only when either side cannot convert", () => {
		expect(formatSpendOfCapWithFx(0, 50, eur)).toBe("$0 of $50");
		expect(formatSpendOfCapWithFx(43.9, undefined, eur)).toBe("$43.90 of —");
		expect(spendOfCapConversion(0, 50, eur)).toBeNull();
	});
});

describe("never sums conversions", () => {
	/**
	 * Each row converts to a figure that rounds up on its own; the total must be `convert(Σ USD)`,
	 * not `Σ convert(row)`. The two differ by a cent here, which is exactly the discrepancy that
	 * makes a footer disagree with the column above it.
	 */
	const rows = [0.575, 0.575, 0.575, 0.575];

	it("converts the total rather than adding up converted rows", () => {
		const totalUsd = rows.reduce((sum, value) => sum + value, 0);
		const summedConversions = rows
			.map((value) => Number((value * eur.ratePerUsd).toFixed(2)))
			.reduce((sum, value) => sum + value, 0);

		// The naive path really is different — otherwise this test would pass by coincidence.
		expect(summedConversions).not.toBeCloseTo(totalUsd * eur.ratePerUsd, 2);
		expect(spendConversion(totalUsd, eur)?.text).toBe("≈ €2.02");
	});

	it("renders the breakdown footer from the USD total", () => {
		const day = (costUsd: number, iso: string): LlmUsageByDay => ({
			day: new Date(iso),
			pricedTotalCostUsd: costUsd,
			byoTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 1,
		});
		render(
			<LlmUsageByDayTable
				rows={rows.map((value, index) => day(value, `2026-07-0${index + 1}T00:00:00.000Z`))}
				fx={eur}
			/>,
		);

		const footer = screen.getByRole("row", { name: /^Total/ });
		expect(footer.textContent).toContain("$2.30");
		// convert(2.30) = €2.02, while Σ convert(0.575) would be €2.04.
		expect(footer.textContent).toContain("≈ €2.02");
		expect(footer.textContent).not.toContain("€2.04");
	});
});

describe("page disclosure", () => {
	it("quotes the live rate for the current month", () => {
		expect(fxDisclosureText(eur, true)).toBe(
			"EUR amounts are estimates at the ECB reference rate for Jul 24, 2026 (1 USD ≈ €0.879). Spend is metered and enforced in USD.",
		);
	});

	it("explains why a closed month never moves", () => {
		expect(fxDisclosureText(eurClosed, false)).toBe(
			"EUR amounts are estimates at the ECB reference rate for Jun 30, 2026 — the last rate published that month, so past figures don't change.",
		);
	});

	it("renders the sentence it reports", () => {
		const { container } = render(<FxDisclosure fx={eur} isCurrentMonth />);
		expect(container.textContent).toBe(fxDisclosureText(eur, true));
	});

	it("says the rate in words", () => {
		render(<FxDisclosure fx={eur} isCurrentMonth />);
		expect(screen.getByLabelText("1 US dollar is approximately 0.879 euros")).not.toBeNull();
	});

	it("renders nothing at all without a rate", () => {
		const { container } = render(<FxDisclosure fx={undefined} isCurrentMonth />);
		expect(container.innerHTML).toBe("");
		expect(fxDisclosureText(undefined, true)).toBeNull();
	});

	it("coerces the ISO date the SDK actually returns", () => {
		const asString = { ...eur, rateDate: "2026-07-24" as unknown as Date };
		expect(fxDisclosureText(asString, true)).toContain("Jul 24, 2026");
	});
});

describe("cap field hint", () => {
	it("estimates the amount being typed", () => {
		const hint = fxCapHint(50, eur);
		expect(hint?.conversion.text).toBe("≈ €44");
		expect(`${hint?.conversion.text}${hint?.tail}`).toBe(
			"≈ €44 at the ECB reference rate for Jul 24, 2026. The cap is stored and enforced in USD.",
		);
	});

	it("says nothing about an empty or zero amount", () => {
		expect(fxCapHint(null, eur)).toBeNull();
		expect(fxCapHint(0, eur)).toBeNull();
		expect(fxCapHint(50, undefined)).toBeNull();
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

	it("derives the currency's spoken name from Intl rather than a hand-written table", () => {
		const jpy: FxRateInfo = { ...eur, currencyCode: "JPY", ratePerUsd: 157 };
		expect(spendConversion(1, jpy)?.label).toBe("approximately 157.00 Japanese yen");
	});

	it("speaks both sides of an X of Y estimate", () => {
		expect(spendOfCapConversion(43.9, 50, eur)?.label).toBe(
			"approximately 38.59 euros of 44 euros",
		);
	});

	it("renders no node at all when there is nothing to convert", () => {
		const { container } = render(<FxAmount conversion={spendConversion(0, eur)} />);
		expect(container.innerHTML).toBe("");
	});
});

describe("without a configured currency", () => {
	const rows: LlmUsageByDay[] = [
		{
			day: new Date("2026-07-05T00:00:00.000Z"),
			pricedTotalCostUsd: 4.25,
			byoTotalCostUsd: 1.75,
			unpricedEventCount: 0,
			events: 3,
		},
	];

	it("renders byte-identically to the pre-conversion markup", () => {
		const withFxProp = render(<LlmUsageByDayTable rows={rows} fx={undefined} />).container
			.innerHTML;
		cleanup();
		const withoutFxProp = render(<LlmUsageByDayTable rows={rows} />).container.innerHTML;

		expect(withFxProp).toBe(withoutFxProp);
		// No estimate, no wrapper left behind for one, no caption promising one.
		expect(withFxProp).not.toContain('role="img"');
		expect(withFxProp).not.toContain("€");
		expect(withFxProp).not.toContain("ECB");
	});
});
