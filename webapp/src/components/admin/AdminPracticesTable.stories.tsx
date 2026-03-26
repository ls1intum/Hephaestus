import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { Practice } from "@/api/types.gen";
import { AdminPracticesTable } from "./AdminPracticesTable";

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
 * Table component for displaying and managing practice definitions.
 * Supports sorting, filtering, pagination, and inline active toggle.
 */
const meta = {
	component: AdminPracticesTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		practices: mockPractices,
		isLoading: false,
		togglingPractices: new Set<string>(),
		onEdit: fn(),
		onDelete: fn(),
		onSetActive: fn(),
		onCreateClick: fn(),
	},
} satisfies Meta<typeof AdminPracticesTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Table with multiple practices showing sorting, active toggles, and actions. */
export const Default: Story = {};

/** Empty state with no practices, showing CTA to create one. */
export const Empty: Story = {
	args: {
		practices: [],
	},
};

/** Loading state with spinner. */
export const Loading: Story = {
	args: {
		practices: [],
		isLoading: true,
	},
};

/** Single practice in the table. */
export const SinglePractice: Story = {
	args: {
		practices: [mockPractices[0]],
	},
};

/** Many practices to test pagination. */
export const ManyItems: Story = {
	args: {
		practices: Array.from({ length: 50 }, (_, i) => ({
			id: i + 1,
			slug: `practice-${i + 1}`,
			name: `Practice ${i + 1}`,
			category: i % 3 === 0 ? "code-quality" : i % 3 === 1 ? "collaboration" : undefined,
			description: `Description for practice ${i + 1}.`,
			triggerEvents: i % 2 === 0 ? ["PullRequestCreated"] : ["ReviewSubmitted"],
			active: i % 4 !== 0,
			createdAt: new Date("2025-06-01"),
			updatedAt: new Date("2025-06-15"),
		})),
	},
};
