import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { SetBudgetDialog } from "./SetBudgetDialog";

/**
 * Instance-admin dialog to set or remove a workspace's monthly shared-model budget.
 * Open whenever a workspace is passed; `null` keeps it closed.
 */
const meta = {
	component: SetBudgetDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		workspace: {
			workspaceSlug: "example-workspace",
			displayName: "Example Workspace",
			instanceMonthlyBudgetUsd: 25,
			instanceTotalCostUsd: 25.0142,
			ownProviderTotalCostUsd: 0,
			events: 118,
			instanceBudgetVerdict: "EXHAUSTED",
			ownProviderBudgetVerdict: "WITHIN" as const,
			ownProviderPaused: false,
			instancePaused: false,
		},
		isPending: false,
		onOpenChange: fn(),
		onSubmit: fn(),
	},
} satisfies Meta<typeof SetBudgetDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Editing an existing budget — the "Remove budget" action is offered. */
export const WithExistingCap: Story = {};

/** Workspace with no budget — no "Remove budget" action, input starts empty. */
export const Uncapped: Story = {
	args: {
		workspace: {
			workspaceSlug: "sandbox",
			displayName: "Sandbox",
			instanceTotalCostUsd: 0.42,
			ownProviderTotalCostUsd: 0,
			events: 3,
			instanceBudgetVerdict: "WITHIN",
			ownProviderBudgetVerdict: "WITHIN" as const,
			ownProviderPaused: false,
			instancePaused: false,
		},
	},
};

/** Save in flight — inputs and actions disabled. */
export const Pending: Story = {
	args: { isPending: true },
};

/**
 * Submitting a cleared field surfaces *why* it was rejected instead of silently doing nothing.
 * The dialog is portalled, so the play queries the document rather than the story canvas.
 */
export const InvalidEmptyValue: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.clear(dialog.getByLabelText(/monthly budget/i));
		await userEvent.click(dialog.getByRole("button", { name: /save budget/i }));

		await expect(dialog.getByRole("alert")).toHaveTextContent(/enter an amount/i);
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};

/** Sub-cent precision is rejected in the field rather than by a native browser bubble. */
export const InvalidSubCentValue: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		const input = dialog.getByLabelText(/monthly budget/i);
		await userEvent.clear(input);
		await userEvent.type(input, "25.005");
		await userEvent.click(dialog.getByRole("button", { name: /save budget/i }));

		await expect(dialog.getByRole("alert")).toHaveTextContent(/two decimal places/i);
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};

/** A negative amount is rejected with its own reason, and nothing is submitted. */
export const InvalidNegativeValue: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		const input = dialog.getByLabelText(/monthly budget/i);
		await userEvent.clear(input);
		await userEvent.type(input, "-5");
		await userEvent.click(dialog.getByRole("button", { name: /save budget/i }));

		await expect(dialog.getByRole("alert")).toHaveTextContent(/\$0 or more/i);
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};
