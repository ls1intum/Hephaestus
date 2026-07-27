import type { Meta, StoryObj } from "@storybook/react";
import { type ReactNode, useState } from "react";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";
import { ConfirmDialog, type ConfirmDialogProps } from "./ConfirmDialog";

interface Row {
	id: number;
	displayName: string;
}

const row: Row = { id: 7, displayName: "GPT-5" };

/**
 * The caller's half of the contract, for the stories that need the dialog to actually close: the row
 * awaiting confirmation lives in the caller's state, exactly as it does on every real surface.
 */
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
 * The confirm for a destructive row action on the AI/LLM surfaces.
 *
 * It names the row in its title, because a modal that says only "Are you sure?" is a modal nobody
 * can answer. It closes the moment it is confirmed — the request it starts is the row's business,
 * not the popup's — which is what keeps a second click from re-sending a delete, and what keeps a
 * pending request from ever holding a popup open with both of its buttons disabled.
 *
 * Dismissal (Escape, Cancel) is covered by `ConfirmDialog.test.tsx`, which asserts the same three
 * things this file could only restate: both footer buttons enabled, the popup gone, nothing acted on.
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

/**
 * Confirming hands the row back and closes in the same gesture.
 *
 * Rendered against caller state, because that is the only way the claim can be checked: `subject` is
 * the caller's, so "closes" happens when the caller clears it from `onClose` — a story holding
 * `subject` fixed would leave the popup on screen no matter what the component did.
 */
export const Confirming: Story = {
	render: (args) => <ConfirmHarness {...args} />,
	play: async ({ canvas }) => {
		const dialog = within(await screen.findByRole("alertdialog"));
		await userEvent.click(dialog.getByRole("button", { name: "Delete" }));

		// The popup outlives its own close by an exit animation, so "gone" is waited for.
		await waitFor(async () => await expect(screen.queryByRole("alertdialog")).toBeNull());
		// …and the row it handed back is the one it was opened on, read off the page rather than
		// off the callback: `onConfirm(shown)` instead of `onConfirm(subject)` would act on the row
		// the caller has already let go of.
		await expect(canvas.getByText("Deleted “GPT-5”")).toBeInTheDocument();
	},
};
