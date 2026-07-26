import type { Meta, StoryObj } from "@storybook/react";
import type { ReactNode } from "react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { ConfirmDialog, type ConfirmDialogProps } from "./ConfirmDialog";

interface Row {
	id: number;
	displayName: string;
}

const row: Row = { id: 7, displayName: "GPT-5" };

/**
 * The one confirm for a destructive row action, wherever a table offers one.
 *
 * It names the row in its title, because a modal that says only "Are you sure?" is a modal nobody
 * can answer. It closes the moment it is confirmed — the request it starts is the row's business,
 * not the popup's — which is what keeps a second click from re-sending a delete, and what keeps a
 * pending request from ever holding a popup open with both of its buttons disabled.
 */
const meta = {
	// The component is generic; the stories pin it to one row type so the render props are typed.
	component: ConfirmDialog as (props: ConfirmDialogProps<Row>) => ReactNode,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		subject: row,
		title: (subject: Row) => `Delete “${subject.displayName}”?`,
		description:
			"A model still bound to a workspace's agent can't be deleted. This cannot be undone.",
		confirmLabel: "Delete",
		onConfirm: fn(),
		onClose: fn(),
	},
} satisfies Meta<ConfirmDialogProps<Row>>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The default shape: the row's name in the title, the consequence under it. */
export const Default: Story = {};

/**
 * "Cancel" is not the opposite of every verb. Turning a connection off is refused by keeping it
 * active, and the description reads the row it was opened on.
 */
export const CustomVerbs: Story = {
	args: {
		title: (subject: Row) => `Turn off “${subject.displayName}”?`,
		description: (subject: Row) =>
			`This immediately stops requests through every model on ${subject.displayName}.`,
		confirmLabel: "Turn off connection",
		cancelLabel: "Keep active",
	},
};

/** A name long enough to wrap still has to leave both actions reachable. */
export const LongName: Story = {
	args: {
		subject: { id: 8, displayName: "gpt-5-turbo-preview-2026-07-01-eu-central-fallback" },
	},
};

/** Confirming hands the row back and closes in the same gesture. */
export const Confirming: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("alertdialog"));
		await userEvent.click(dialog.getByRole("button", { name: "Delete" }));
		await expect(args.onConfirm).toHaveBeenCalledWith(row);
		await expect(args.onClose).toHaveBeenCalled();
	},
};

/**
 * Escape always gets out. Nothing in this popup is ever disabled, so it can never be the modal with
 * no operable control and no exit that WCAG 2.2 SC 2.1.2 is about.
 */
export const EscapeDismisses: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("alertdialog"));
		await expect(dialog.getByRole("button", { name: "Cancel" })).toBeEnabled();
		await expect(dialog.getByRole("button", { name: "Delete" })).toBeEnabled();
		await userEvent.keyboard("{Escape}");
		await expect(args.onClose).toHaveBeenCalled();
		await expect(args.onConfirm).not.toHaveBeenCalled();
	},
};
