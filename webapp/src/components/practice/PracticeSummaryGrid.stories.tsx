import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { ContributorPracticeSummary } from "@/api/types.gen";
import { PracticeSummaryGrid } from "./PracticeSummaryGrid";

const mockSummaries: ContributorPracticeSummary[] = [
	{
		practiceName: "Code Review Thoroughness",
		practiceSlug: "code-review-thoroughness",
		category: "Code Quality",
		positiveCount: 15,
		negativeCount: 3,
		totalFindings: 18,
		lastFindingAt: new Date(),
	},
	{
		practiceName: "Test Coverage",
		practiceSlug: "test-coverage",
		category: "Testing",
		positiveCount: 5,
		negativeCount: 8,
		totalFindings: 13,
		lastFindingAt: new Date(),
	},
	{
		practiceName: "Error Handling",
		practiceSlug: "error-handling",
		category: "Reliability",
		positiveCount: 7,
		negativeCount: 4,
		totalFindings: 11,
		lastFindingAt: new Date(),
	},
	{
		practiceName: "Documentation Standards",
		practiceSlug: "documentation-standards",
		category: "Documentation",
		positiveCount: 10,
		negativeCount: 2,
		totalFindings: 12,
		lastFindingAt: new Date(),
	},
	{
		practiceName: "Naming Conventions",
		practiceSlug: "naming-conventions",
		category: "Code Quality",
		positiveCount: 6,
		negativeCount: 6,
		totalFindings: 12,
		lastFindingAt: new Date(),
	},
];

/**
 * Responsive grid of practice summary cards.
 */
const meta = {
	component: PracticeSummaryGrid,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component: "Grid layout of PracticeSummaryCard components with selection support.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		onPracticeSelect: fn(),
		selectedPracticeSlug: null,
	},
	argTypes: {
		selectedPracticeSlug: {
			control: "text",
			description: "Currently selected practice slug for filtering",
		},
		isLoading: { control: "boolean", description: "Loading state" },
	},
} satisfies Meta<typeof PracticeSummaryGrid>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Multiple practices displayed in a responsive grid.
 */
export const Default: Story = {
	args: {
		summaries: mockSummaries,
	},
};

/**
 * Only one practice.
 */
export const SinglePractice: Story = {
	args: {
		summaries: [mockSummaries[0]],
	},
};

/**
 * Many practices filling the grid.
 */
export const ManyPractices: Story = {
	args: {
		summaries: [
			...mockSummaries,
			{
				practiceName: "Security Practices",
				practiceSlug: "security-practices",
				category: "Security",
				positiveCount: 9,
				negativeCount: 1,
				totalFindings: 10,
				lastFindingAt: new Date(),
			},
		],
	},
};

/**
 * Grid with one card selected.
 */
export const WithSelection: Story = {
	args: {
		summaries: mockSummaries,
		selectedPracticeSlug: "code-review-thoroughness",
	},
};

/**
 * Empty summaries array renders nothing.
 */
export const Empty: Story = {
	args: {
		summaries: [],
	},
};

/**
 * Loading skeleton state.
 */
export const Loading: Story = {
	args: {
		summaries: [],
		isLoading: true,
	},
};
