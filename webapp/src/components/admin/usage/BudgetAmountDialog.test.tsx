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

describe("the cap editor's currency hint", () => {
	it("dates the estimate to today while the month behind it is the current one", () => {
		renderDialog();

		expect(screen.getByText(/at today's rate/i)).toBeTruthy();
		expect(screen.getByLabelText(/approximately 44 euros/i)).toBeTruthy();
	});

	it("withdraws the estimate when the month behind the open dialog closes", () => {
		// The dialog outlives the check that opened it: `open` is plain React state, so stepping the
		// month behind it — browser Back after Previous/Next, or a UTC month rolling over while it
		// sits there — leaves it on screen against a closed month's frozen rate. "at today's rate"
		// would then quote a rate the page behind it says no longer changes.
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
