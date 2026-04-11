import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { PracticeFormSheet } from "./PracticeFormSheet";
import { mockPractices, mockPracticeWithAllTriggers } from "./storyMockData";

/**
 * Sheet panel for creating or editing a practice definition.
 * Slides in from the right, keeping the card list visible behind.
 */
const meta = {
	component: PracticeFormSheet,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		mode: "create",
		open: true,
		onOpenChange: fn(),
		onSubmit: fn(),
		isPending: false,
	},
} satisfies Meta<typeof PracticeFormSheet>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Empty form for creating a new practice. */
export const CreateMode: Story = {};

/** Pre-filled form for editing an existing practice with criteria. */
export const EditMode: Story = {
	args: {
		mode: "edit",
		initialData: mockPractices[0],
	},
};

/** Edit mode with all 4 trigger events and long criteria text. */
export const EditWithAllTriggers: Story = {
	args: {
		mode: "edit",
		initialData: mockPracticeWithAllTriggers,
	},
};

/** Create mode in submitting state with "Creating..." spinner. */
export const CreateSubmitting: Story = {
	args: {
		isPending: true,
	},
};

/** Edit mode in submitting state with "Saving..." spinner. */
export const EditSubmitting: Story = {
	args: {
		mode: "edit",
		initialData: mockPractices[0],
		isPending: true,
	},
};

/** Edit mode with a practice that has no criteria — empty textarea. */
export const EditNoCriteria: Story = {
	args: {
		mode: "edit",
		initialData: mockPractices[1],
	},
};
