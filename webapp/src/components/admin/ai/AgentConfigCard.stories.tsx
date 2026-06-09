import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { AgentConfigCard } from "./AgentConfigCard";
import { mockConfigApiKey, mockConfigDisabled, mockConfigProxy } from "./storyMockData";

/**
 * Read-only summary card for an agent runtime, showing its enabled state and how it is
 * wired into workspace AI features. Editing happens in the form; the card only deletes.
 */
const meta = {
	component: AgentConfigCard,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		config: mockConfigProxy,
		onEdit: fn(),
		onDelete: fn(),
	},
	decorators: [
		(Story) => (
			<div className="max-w-md">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof AgentConfigCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Unbound, enabled runtime — no designation badges. */
export const Default: Story = {};

/** Bound to practice detection. */
export const PracticeDesignation: Story = {
	args: { designation: "practice" },
};

/** Bound to the mentor. */
export const MentorDesignation: Story = {
	args: { config: mockConfigApiKey, designation: "mentor" },
};

/** Bound to both purposes. */
export const BothDesignation: Story = {
	args: { config: mockConfigApiKey, designation: "both" },
};

/** Currently selected in the form (highlighted border). */
export const Selected: Story = {
	args: { selected: true, designation: "practice" },
};

/** Disabled runtime (dimmed). */
export const Disabled: Story = {
	args: { config: mockConfigDisabled },
};

/** Opening the delete confirmation surfaces the destructive AlertDialog. */
export const DeleteConfirm: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /delete default reviewer/i }));
		// AlertDialog renders in a portal — query the whole document.
		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByText(/permanently removes this runtime/i)).toBeInTheDocument();
		await expect(
			within(dialog).getByRole("button", { name: /^delete runtime$/i }),
		).toBeInTheDocument();
	},
};
