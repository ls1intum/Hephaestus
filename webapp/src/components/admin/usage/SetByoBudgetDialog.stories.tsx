import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { SetByoBudgetDialog } from "./SetByoBudgetDialog";

/**
 * The workspace admin's cap on spend through their own provider — their money, so unlike the
 * shared-model budget this one is theirs to set, change, and remove.
 */
const meta = {
	component: SetByoBudgetDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		currentCapUsd: 25,
		isPending: false,
		serverError: null,
		onOpenChange: fn(),
		onSubmit: fn(),
	},
} satisfies Meta<typeof SetByoBudgetDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Changing an existing cap — "Remove cap" is offered alongside it. */
export const WithExistingCap: Story = {};

/** No cap yet — nothing to remove. */
export const Uncapped: Story = {
	args: { currentCapUsd: null },
};

/** Save in flight — inputs and actions disabled. */
export const Pending: Story = {
	args: { isPending: true },
};

/**
 * The server rejected the amount. It lands next to the value that caused it, not in a toast that
 * evaporates before the two can be compared.
 */
export const ServerRejection: Story = {
	args: { serverError: "Monthly cap must not exceed 99999999.99." },
	play: async () => {
		const dialog = within(await screen.findByRole("dialog"));
		await expect(dialog.getByRole("alert")).toHaveTextContent(/must not exceed/i);
	},
};

/** Submitting a cleared field surfaces *why* it was rejected instead of silently doing nothing. */
export const InvalidEmptyValue: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.clear(dialog.getByLabelText(/monthly cap/i));
		await userEvent.click(dialog.getByRole("button", { name: /save cap/i }));

		await expect(dialog.getByRole("alert")).toHaveTextContent(/enter an amount/i);
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};

/** Sub-cent precision is rejected in the field rather than by a native browser bubble. */
export const InvalidSubCentValue: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		const input = dialog.getByLabelText(/monthly cap/i);
		await userEvent.clear(input);
		await userEvent.type(input, "25.005");
		await userEvent.click(dialog.getByRole("button", { name: /save cap/i }));

		await expect(dialog.getByRole("alert")).toHaveTextContent(/two decimal places/i);
		await expect(args.onSubmit).not.toHaveBeenCalled();
	},
};

/** Removing the cap submits `null` — uncapped, not zero (which would pause everything). */
export const RemoveCap: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.click(dialog.getByRole("button", { name: /remove cap/i }));

		await expect(args.onSubmit).toHaveBeenCalledWith(null);
	},
};
