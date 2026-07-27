import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { FxRateInfo } from "@/api/types.gen";
import { BudgetAmountDialog, type BudgetAmountDialogProps } from "./BudgetAmountDialog";

const eur: FxRateInfo = {
	currencyCode: "EUR",
	ratePerUsd: 0.879,
	rateDate: new Date("2026-07-24T00:00:00.000Z"),
	source: "ECB",
};

function renderDialog(overrides: Partial<BudgetAmountDialogProps> = {}) {
	return render(
		<BudgetAmountDialog
			open
			title="Set monthly cap"
			description="At the cap, that work pauses until the month resets."
			fieldLabel="Monthly cap (USD)"
			currentValueUsd={50}
			isPending={false}
			fx={eur}
			isCurrentMonth
			onOpenChange={vi.fn()}
			onSubmit={vi.fn()}
			{...overrides}
		/>,
	);
}

/**
 * The hint's shape on an open current-month dialog — the rounding, the wording, and the way it
 * tracks what is being typed — belongs to `fx.test.tsx` (`fxCapHint`) and to this component's
 * `WithLiveCurrencyHint` story. What is left here is the one thing neither can reach: the dialog
 * outliving the month it was opened over.
 */
describe("the cap editor's currency hint", () => {
	it("withdraws the estimate when the month behind the open dialog closes", () => {
		// See `fxCapHint`: the dialog outlives the check that opened it.
		const { rerender } = renderDialog();
		expect(screen.getByText(/at today's rate/i)).toBeTruthy();

		rerender(
			<BudgetAmountDialog
				open
				title="Set monthly cap"
				description="At the cap, that work pauses until the month resets."
				fieldLabel="Monthly cap (USD)"
				currentValueUsd={50}
				isPending={false}
				fx={eur}
				isCurrentMonth={false}
				onOpenChange={vi.fn()}
				onSubmit={vi.fn()}
			/>,
		);

		expect(screen.queryByText(/at today's rate/i)).toBeNull();
		expect(screen.queryByLabelText(/approximately 44 euros/i)).toBeNull();
	});

	it("says nothing about a rate a caller never claimed a month for", () => {
		// The prop defaults to `false`, so a surface that has not thought about which month it is
		// showing gets no estimate rather than a possibly frozen one.
		renderDialog({ isCurrentMonth: undefined });

		expect(screen.queryByText(/at today's rate/i)).toBeNull();
	});
});
