import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { mockSummaries } from "./__fixtures__/mock-data";
import { PracticeSummaryGrid } from "./PracticeSummaryGrid";

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
				lastFindingAt: new Date("2025-06-15T10:00:00Z"),
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
