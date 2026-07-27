import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { expectAmountRejected } from "@/test/budget-amount-field";
import { BudgetAmountDialog } from "./BudgetAmountDialog";
import type { Fx } from "./fx";

const EUR: Fx = {
	currencyCode: "EUR",
	ratePerUsd: 0.878966,
	rateDate: new Date("2026-07-24T00:00:00.000Z"),
	source: "ECB",
};

/**
 * The money-cap editor behind both budget dialogs — `SetBudgetDialog` (the shared-model budget)
 * and `SetOwnProviderBudgetDialog` (the workspace's provider cap). It owns the rules both caps
 * share: USD, at least $0, at most two decimals, `null` to remove, `0` to pause immediately, and a
 * server rejection shown against the field rather than in a toast. Callers supply only copy.
 */
const meta = {
	component: BudgetAmountDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		title: "Set monthly cap",
		description: "At the cap, that work pauses until the month resets. $0 pauses now.",
		fieldLabel: "Monthly cap (USD)",
		currentValueUsd: 25,
		isPending: false,
		serverError: null,
		onOpenChange: fn(),
		onSubmit: fn(),
	},
} satisfies Meta<typeof BudgetAmountDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Editing an existing cap — "Remove cap" is offered. */
export const WithExistingCap: Story = {};

/** Uncapped — no "Remove cap" action, and the input starts empty. */
export const Uncapped: Story = {
	args: { currentValueUsd: null },
};

export const Pending: Story = {
	args: { isPending: true },
};

/** A server rejection, rendered as this field's error. */
export const ServerRejection: Story = {
	args: { serverError: "Monthly cap must not exceed 99999999.99." },
};

/**
 * Every amount this form refuses, and the reason it gives for each. The rules live here, on the
 * component that owns them — the two wrappers each keep one illustration to prove they pass their
 * own field and button labels through, and nothing more.
 */
const rejects =
	(typed: string, reason: RegExp): Story["play"] =>
	async ({ args }) =>
		await expectAmountRejected({
			fieldLabel: /monthly cap/i,
			submitLabel: /save cap/i,
			typed,
			reason,
			onSubmit: args.onSubmit,
		});

/** Submitting a cleared field surfaces *why* it was rejected instead of silently doing nothing. */
export const InvalidEmptyValue: Story = { play: rejects("", /enter an amount/i) };

/** Sub-cent precision is rejected in the field rather than by a native browser bubble. */
export const InvalidSubCentValue: Story = { play: rejects("25.005", /two decimal places/i) };

/** A local rejection wins over a stale server one, and nothing is submitted. */
export const InvalidNegativeValue: Story = { play: rejects("-5", /\$0 or more/i) };

/**
 * On an instance that displays EUR the field says what the amount being typed is worth, live and
 * rounded to whole units — an estimate to the cent beside a round `$50` would claim a precision it
 * does not have. The USD figure is what is stored and enforced, and the hint says so.
 */
export const WithLiveCurrencyHint: Story = {
	args: {
		currentValueUsd: 50,
		fx: EUR,
		// The rate quoted is the latest published one only while the surface behind the dialog is on
		// the current month; the story below says what happens when it is not.
		isCurrentMonth: true,
	},
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		await expect(await dialog.findByText(/at today's rate\./)).toBeInTheDocument();
		await expect(dialog.getByLabelText("approximately 44 euros")).toBeInTheDocument();

		// The hint tracks the field rather than the saved cap.
		const input = dialog.getByLabelText(/monthly cap/i);
		await userEvent.clear(input);
		await userEvent.type(input, "120");
		await expect(await dialog.findByLabelText("approximately 105 euros")).toBeInTheDocument();

		// Nothing to estimate about an empty field, so the hint leaves rather than showing "≈ €0".
		await userEvent.clear(input);
		await expect(dialog.queryByText(/at today's rate/)).toBeNull();
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};

/** The same dialog over a closed month, with the estimate withdrawn — see `fxCapHint` for why. */
export const OnAClosedMonthTheHintIsWithdrawn: Story = {
	args: { currentValueUsd: 50, fx: EUR, isCurrentMonth: false },
};

/** Without a display currency configured — the default — the field carries no estimate at all. */
export const WithoutCurrencyHint: Story = {
	play: async () => {
		const dialog = within(await screen.findByRole("dialog"));
		await expect(dialog.queryByText(/at today's rate/)).toBeNull();
	},
};

/** $0 is a legal cap — it pauses the capped work immediately rather than removing the limit. */
export const ZeroPausesImmediately: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		const input = dialog.getByLabelText(/monthly cap/i);
		await userEvent.clear(input);
		await userEvent.type(input, "0");
		await userEvent.click(dialog.getByRole("button", { name: /save cap/i }));

		// Legal means the field never turns red and never explains itself: `0` is treated as an amount,
		// not as the empty field it superficially resembles. "Remove cap" is still the separate action
		// beside it, and it is still offered — `0` did not become the way to remove one.
		await expect(input).toHaveAttribute("aria-invalid", "false");
		await expect(dialog.queryByRole("alert")).toBeNull();
		await expect(dialog.getByRole("button", { name: /remove cap/i })).toBeInTheDocument();
		await expect(args.onSubmit).toHaveBeenCalledWith(0);
	},
};
