import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { SetBudgetDialog } from "./SetBudgetDialog";

/**
 * Instance-admin dialog to set or remove a workspace's monthly LLM budget cap.
 * Open whenever a workspace is passed; `null` keeps it closed.
 */
const meta = {
	component: SetBudgetDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		workspace: {
			workspaceId: 1,
			workspaceSlug: "obsphera",
			displayName: "Obsphera",
			monthlyBudgetUsd: 25,
			costUsd: 25.0142,
			events: 118,
			overBudget: true,
		},
		isPending: false,
		onOpenChange: fn(),
		onSubmit: fn(),
	},
} satisfies Meta<typeof SetBudgetDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Editing an existing cap — the "Remove cap" action is offered. */
export const WithExistingCap: Story = {};

/** Uncapped workspace — no "Remove cap" action, input starts empty. */
export const Uncapped: Story = {
	args: {
		workspace: {
			workspaceId: 3,
			workspaceSlug: "sandbox",
			displayName: "Sandbox",
			costUsd: 0.42,
			events: 3,
			overBudget: false,
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
		await userEvent.click(dialog.getByRole("button", { name: /save cap/i }));

		await expect(dialog.getByRole("alert")).toHaveTextContent(/enter a budget amount/i);
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
		await userEvent.click(dialog.getByRole("button", { name: /save cap/i }));

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
		await userEvent.click(dialog.getByRole("button", { name: /save cap/i }));

		await expect(dialog.getByRole("alert")).toHaveTextContent(/\$0 or more/i);
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};
