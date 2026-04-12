import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { PracticeForm } from "./PracticeForm";
import { mockPractices, mockPracticeWithAllTriggers } from "./storyMockData";

/**
 * Full-page form for creating or editing a practice definition.
 * Includes sections for general info, trigger events, criteria, and precompute script.
 */
const meta = {
	component: PracticeForm,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		mode: "create",
		onSubmit: fn(),
		onCancel: fn(),
		isPending: false,
	},
} satisfies Meta<typeof PracticeForm>;

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

/** Edit mode with all triggers and a precompute script. */
export const EditWithScript: Story = {
	args: {
		mode: "edit",
		initialData: mockPracticeWithAllTriggers,
	},
};

/** Create mode in submitting state. */
export const CreateSubmitting: Story = {
	args: {
		isPending: true,
	},
};

/** Edit mode in submitting state. */
export const EditSubmitting: Story = {
	args: {
		mode: "edit",
		initialData: mockPractices[0],
		isPending: true,
	},
};
