import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { Practice } from "@/api/types.gen";
import { AdminPracticesPage } from "./AdminPracticesPage";

const mockPractices: Practice[] = [
	{
		id: 1,
		slug: "pr-description-quality",
		name: "PR Description Quality",
		category: "code-quality",
		description: "Ensures PR descriptions are detailed and informative.",
		triggerEvents: ["PullRequestCreated", "PullRequestReady"],
		detectionPrompt: "Evaluate whether the pull request description provides sufficient context...",
		active: true,
		createdAt: new Date("2025-06-01"),
		updatedAt: new Date("2025-06-15"),
	},
	{
		id: 2,
		slug: "code-review-thoroughness",
		name: "Code Review Thoroughness",
		category: "code-quality",
		description: "Evaluates depth and quality of code reviews.",
		triggerEvents: ["ReviewSubmitted"],
		active: true,
		createdAt: new Date("2025-06-02"),
		updatedAt: new Date("2025-06-14"),
	},
	{
		id: 3,
		slug: "test-coverage",
		name: "Test Coverage",
		description: "Checks that new code includes appropriate test coverage.",
		triggerEvents: ["PullRequestCreated", "PullRequestSynchronized"],
		active: false,
		createdAt: new Date("2025-06-03"),
		updatedAt: new Date("2025-06-10"),
	},
];

/**
 * Full admin page for managing practice definitions.
 * Includes table, create/edit dialogs, and delete confirmation.
 */
const meta = {
	component: AdminPracticesPage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		practices: mockPractices,
		isLoading: false,
		isCreating: false,
		isUpdating: false,
		isDeleting: false,
		togglingPractices: new Set<string>(),
		onCreatePractice: fn().mockResolvedValue(undefined),
		onUpdatePractice: fn().mockResolvedValue(undefined),
		onDeletePractice: fn().mockResolvedValue(undefined),
		onSetActive: fn(),
	},
} satisfies Meta<typeof AdminPracticesPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Default view with practices in the table. */
export const Default: Story = {};

/** Loading state while fetching practices. */
export const Loading: Story = {
	args: {
		practices: [],
		isLoading: true,
	},
};

/** Empty state when no practices have been configured. */
export const Empty: Story = {
	args: {
		practices: [],
	},
};
