import type { Meta, StoryObj } from "@storybook/react";
import { type ReactNode, useState } from "react";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";
import { ConfirmDialog, type ConfirmDialogProps } from "./ConfirmDialog";

interface Row {
	id: number;
	displayName: string;
}

const row: Row = { id: 7, displayName: "GPT-5" };

/** Holds `subject` in caller state, as every real surface does, so the dialog can actually close. */
function ConfirmHarness(props: ConfirmDialogProps<Row>) {
	const [subject, setSubject] = useState<Row | null>(props.subject);
	const [deleted, setDeleted] = useState<Row | null>(null);
	return (
		<>
			<ConfirmDialog
				{...props}
				subject={subject}
				onConfirm={(confirmed) => {
					props.onConfirm(confirmed);
					setDeleted(confirmed);
				}}
				onClose={() => {
					props.onClose();
					setSubject(null);
				}}
			/>
			{deleted != null && <p>Deleted “{deleted.displayName}”</p>}
		</>
	);
}

/**
 * The confirm for a destructive row action. It names the row in its title, because a modal that
 * says only "Are you sure?" is a modal nobody can answer, and it closes the moment it is confirmed.
 */
const meta = {
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

export const Default: Story = {};

/** "Cancel" is not the opposite of every verb: turning a connection off is refused by keeping it active. */
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
	render: (args) => <ConfirmHarness {...args} />,
	play: async ({ canvas }) => {
		const dialog = within(await screen.findByRole("alertdialog"));
		await userEvent.click(dialog.getByRole("button", { name: "Delete" }));

		// The popup outlives its own close by an exit animation, so "gone" is waited for.
		await waitFor(async () => await expect(screen.queryByRole("alertdialog")).toBeNull());
		await expect(canvas.getByText("Deleted “GPT-5”")).toBeInTheDocument();
	},
};
