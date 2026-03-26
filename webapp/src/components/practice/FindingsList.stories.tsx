import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { mockFindings, mockPracticeOptions } from "./__fixtures__/mock-data";
import { FindingsList } from "./FindingsList";

/**
 * Paginated list of practice findings with filter controls.
 */
const meta = {
	component: FindingsList,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Displays a filterable, paginated list of practice findings with practice and verdict filters.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		onPracticeSelect: fn(),
		onVerdictChange: fn(),
		onLoadMore: fn(),
		selectedPracticeSlug: null,
		selectedVerdict: "ALL",
	},
	argTypes: {
		selectedPracticeSlug: { control: "text", description: "Active practice filter" },
		selectedVerdict: {
			control: "select",
			options: ["ALL", "POSITIVE", "NEGATIVE"],
			description: "Active verdict filter",
		},
		hasMore: { control: "boolean", description: "Whether more pages are available" },
		isLoading: { control: "boolean", description: "Initial loading state" },
		isLoadingMore: { control: "boolean", description: "Loading more items" },
	},
} satisfies Meta<typeof FindingsList>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view with findings and filters.
 */
export const Default: Story = {
	args: {
		findings: mockFindings,
		practiceOptions: mockPracticeOptions,
		hasMore: true,
	},
};

/**
 * Empty state when no findings exist.
 */
export const Empty: Story = {
	args: {
		findings: [],
		practiceOptions: [],
	},
};

/**
 * Loading skeleton state.
 */
export const Loading: Story = {
	args: {
		findings: [],
		practiceOptions: [],
		isLoading: true,
	},
};

/**
 * Filtered to a specific practice.
 */
export const PracticeFiltered: Story = {
	args: {
		findings: mockFindings.filter((f) => f.practiceSlug === "error-handling"),
		practiceOptions: mockPracticeOptions,
		selectedPracticeSlug: "error-handling",
		hasMore: false,
	},
};

/**
 * Filtered by verdict (Negative only).
 */
export const VerdictFiltered: Story = {
	args: {
		findings: mockFindings.filter((f) => f.verdict === "NEGATIVE"),
		practiceOptions: mockPracticeOptions,
		selectedVerdict: "NEGATIVE",
		hasMore: false,
	},
};

/**
 * Filtered state with no matching results.
 */
export const FilteredEmpty: Story = {
	args: {
		findings: [],
		practiceOptions: mockPracticeOptions,
		selectedPracticeSlug: "test-coverage",
		selectedVerdict: "NEGATIVE",
	},
};

/**
 * Loading more results after clicking "Show more".
 */
export const LoadingMore: Story = {
	args: {
		findings: mockFindings,
		practiceOptions: mockPracticeOptions,
		hasMore: true,
		isLoadingMore: true,
	},
};
