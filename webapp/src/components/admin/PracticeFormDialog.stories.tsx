import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { Practice } from "@/api/types.gen";
import { PracticeFormDialog } from "./PracticeFormDialog";

const mockPractice: Practice = {
	id: 1,
	slug: "pr-description-quality",
	name: "PR Description Quality",
	category: "code-quality",
	description:
		"Ensures PR descriptions are detailed and informative, including context, motivation, and testing steps.",
	triggerEvents: ["PullRequestCreated", "PullRequestReady"],
	detectionPrompt: "Evaluate whether the pull request description provides sufficient context...",
	active: true,
	createdAt: new Date("2025-06-01"),
	updatedAt: new Date("2025-06-15"),
};

/**
 * Dialog for creating or editing a practice definition.
 * Includes fields for name, slug, category, description, trigger events, and detection prompt.
 */
const meta = {
	component: PracticeFormDialog,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		mode: "create",
		open: true,
		onOpenChange: fn(),
		onSubmit: fn(),
		isPending: false,
	},
} satisfies Meta<typeof PracticeFormDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Empty form for creating a new practice. */
export const CreateMode: Story = {};

/** Pre-filled form for editing an existing practice. */
export const EditMode: Story = {
	args: {
		mode: "edit",
		initialData: mockPractice,
		onSubmit: fn(),
	},
};

/** Form in submitting state with spinner and disabled button. */
export const Submitting: Story = {
	args: {
		isPending: true,
	},
};
