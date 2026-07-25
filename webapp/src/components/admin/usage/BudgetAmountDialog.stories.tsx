import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { BudgetAmountDialog } from "./BudgetAmountDialog";

/**
 * The money-cap editor behind both budget dialogs — `SetBudgetDialog` (the shared-model budget)
 * and `SetByoBudgetDialog` (the workspace's provider cap). It owns the rules both caps
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
		fieldDescription: "Reaching this amount pauses the capped work until the month resets.",
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

/** Save in flight — inputs and actions disabled. */
export const Pending: Story = {
	args: { isPending: true },
};

/** A server rejection, rendered as this field's error. */
export const ServerRejection: Story = {
	args: { serverError: "Monthly cap must not exceed 99999999.99." },
};

/** A local rejection wins over a stale server one, and nothing is submitted. */
export const InvalidNegativeValue: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		const input = dialog.getByLabelText(/monthly cap/i);
		await userEvent.clear(input);
		await userEvent.type(input, "-5");
		await userEvent.click(dialog.getByRole("button", { name: /save cap/i }));

		await expect(dialog.getByRole("alert")).toHaveTextContent(/\$0 or more/i);
		await expect(args.onSubmit).not.toHaveBeenCalled();
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

		await expect(args.onSubmit).toHaveBeenCalledWith(0);
	},
};
