import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";

import { InstanceResearchAttestationDialog } from "./InstanceResearchAttestationDialog";

const FIRST_KEY = "p_abcdefghijklmnopqrstuvwxyz";
const SECOND_KEY = "p_zyxwvutsrqponmlkjihgfedcb";

// Only the props both modes share live here; each story names its own mode in full, so its args
// type-check against that branch of the union.
const meta = {
	component: InstanceResearchAttestationDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		open: true,
		onOpenChange: fn(),
		datasetLabel: "Intro Course, released 20 October 2026",
		datasetId: "6f1c2b8e-4d3a-4f6b-9a1e-2c7d8e9f0a1b",
	},
} satisfies Meta<typeof InstanceResearchAttestationDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing is recorded until the operator states what the research team confirmed. */
export const RemovalNeedsAStatement: Story = {
	args: { mode: "removal", participantKeys: [FIRST_KEY, SECOND_KEY], onConfirm: fn() },
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("alertdialog"));
		// The dialog renders in a portal and animates in, so wait until it is actually shown.
		await waitFor(async () =>
			expect(dialog.getByRole("checkbox", { name: SECOND_KEY })).toBeVisible(),
		);
		await userEvent.click(dialog.getByRole("checkbox", { name: SECOND_KEY }));
		await userEvent.click(dialog.getByRole("button", { name: "Record removal" }));
		await expect(args.onConfirm).not.toHaveBeenCalled();
		await waitFor(async () =>
			expect(
				dialog.getByRole("checkbox", { name: /The research team confirmed/u }),
			).toHaveAccessibleDescription("Confirm this before you record it."),
		);

		await userEvent.click(dialog.getByRole("checkbox", { name: /The research team confirmed/u }));
		await userEvent.click(dialog.getByRole("button", { name: "Record removal" }));
		await expect(args.onConfirm).toHaveBeenCalledWith([FIRST_KEY]);
		await expect(args.onOpenChange).toHaveBeenCalledWith(false);
	},
};

/** Closing asks what happened to every copy, and says that Hephaestus does not check it. */
export const ClosureCoversOwedRemovals: Story = {
	args: { mode: "closure", owedCount: 2, onConfirm: fn() },
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("alertdialog"));
		// The dialog renders in a portal and animates in, so wait until it is actually shown.
		await waitFor(async () =>
			expect(dialog.getByText(/records your statement, not a check/u)).toBeVisible(),
		);
		await expect(dialog.getByText(/2 removals are still owed/u)).toBeVisible();
		await userEvent.click(dialog.getByRole("checkbox", { name: /The research team confirmed/u }));
		await userEvent.click(dialog.getByRole("button", { name: "Close dataset" }));
		await expect(args.onConfirm).not.toHaveBeenCalled();
		await waitFor(async () =>
			expect(dialog.getByText("Choose what happened to every copy.")).toBeVisible(),
		);

		await userEvent.click(dialog.getByRole("radio", { name: "Every copy was anonymized" }));
		await userEvent.click(dialog.getByRole("button", { name: "Close dataset" }));
		await expect(args.onConfirm).toHaveBeenCalledWith("ANONYMIZED");
	},
};
