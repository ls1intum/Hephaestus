import type { Meta, StoryObj } from "@storybook/react";
import { subDays, subHours } from "date-fns";
import { fn } from "storybook/test";
import type { PracticeFindingList } from "@/api/types.gen";
import { FindingsList } from "./FindingsList";

const mockPracticeOptions = [
	{ value: "code-review-thoroughness", label: "Code Review Thoroughness" },
	{ value: "error-handling", label: "Error Handling" },
	{ value: "test-coverage", label: "Test Coverage" },
];

const mockFindings: PracticeFindingList[] = [
	{
		id: "f1",
		title: "Reviewer provided thorough inline feedback on error handling paths",
		verdict: "POSITIVE",
		severity: "MINOR",
		confidence: 0.92,
		detectedAt: subHours(new Date(), 3),
		practiceName: "Code Review Thoroughness",
		practiceSlug: "code-review-thoroughness",
		category: "Code Quality",
		targetId: 42,
		targetType: "pull_request",
	},
	{
		id: "f2",
		title: "Missing error handling in async database operation",
		verdict: "NEGATIVE",
		severity: "MAJOR",
		confidence: 0.88,
		detectedAt: subHours(new Date(), 8),
		practiceName: "Error Handling",
		practiceSlug: "error-handling",
		category: "Reliability",
		targetId: 43,
		targetType: "pull_request",
	},
	{
		id: "f3",
		title: "Comprehensive test suite added for authentication module",
		verdict: "POSITIVE",
		severity: "INFO",
		confidence: 0.95,
		detectedAt: subDays(new Date(), 1),
		practiceName: "Test Coverage",
		practiceSlug: "test-coverage",
		category: "Testing",
		targetId: 44,
		targetType: "pull_request",
	},
	{
		id: "f4",
		title: "Review comment addressed edge case in payment processing",
		verdict: "POSITIVE",
		severity: "MINOR",
		confidence: 0.85,
		detectedAt: subDays(new Date(), 2),
		practiceName: "Code Review Thoroughness",
		practiceSlug: "code-review-thoroughness",
		category: "Code Quality",
		targetId: 45,
		targetType: "review",
	},
	{
		id: "f5",
		title: "Unhandled promise rejection in event listener cleanup",
		verdict: "NEGATIVE",
		severity: "CRITICAL",
		confidence: 0.91,
		detectedAt: subDays(new Date(), 3),
		practiceName: "Error Handling",
		practiceSlug: "error-handling",
		category: "Reliability",
		targetId: 46,
		targetType: "pull_request",
	},
];

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
