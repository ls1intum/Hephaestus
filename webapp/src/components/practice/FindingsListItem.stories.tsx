import type { Meta, StoryObj } from "@storybook/react";
import { subDays, subHours } from "date-fns";
import { mockFindings } from "./__fixtures__/mock-data";
import { FindingsListItem } from "./FindingsListItem";

const baseFinding = mockFindings[0];

/**
 * Individual finding row with left border accent indicating verdict.
 */
const meta = {
	component: FindingsListItem,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Expandable practice finding card with verdict-colored left border. Click to reveal guidance, evidence, reasoning, and feedback controls.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		isLoading: { control: "boolean", description: "Loading skeleton state" },
	},
	decorators: [
		(Story) => (
			<ul className="max-w-xl">
				<Story />
			</ul>
		),
	],
} satisfies Meta<typeof FindingsListItem>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Positive finding with green left border and verdict badge.
 */
export const Positive: Story = {
	args: {
		finding: baseFinding,
	},
};

/**
 * Negative finding with red left border and verdict badge.
 */
export const Negative: Story = {
	args: {
		finding: {
			...baseFinding,
			id: "neg-1",
			title: "Missing error handling in async operation",
			verdict: "NEGATIVE",
			severity: "MAJOR",
			detectedAt: subHours(new Date(), 5),
			practiceName: "Error Handling",
			practiceSlug: "error-handling",
		},
	},
};

/**
 * Needs review finding with amber left border.
 */
export const NeedsReview: Story = {
	args: {
		finding: {
			...baseFinding,
			id: "nr-1",
			title: "Complex logic pattern detected — review recommended",
			verdict: "NEEDS_REVIEW",
			severity: "INFO",
			detectedAt: subDays(new Date(), 1),
			practiceName: "Code Complexity",
			practiceSlug: "code-complexity",
		},
	},
};

/**
 * Not applicable finding with muted left border.
 */
export const NotApplicable: Story = {
	args: {
		finding: {
			...baseFinding,
			id: "na-1",
			title: "Practice not applicable to this repository configuration",
			verdict: "NOT_APPLICABLE",
			severity: "INFO",
			detectedAt: subDays(new Date(), 4),
			practiceName: "Naming Conventions",
			practiceSlug: "naming-conventions",
		},
	},
};

/**
 * Critical severity with negative verdict — the most alarming combination.
 */
export const CriticalNegative: Story = {
	args: {
		finding: {
			...baseFinding,
			id: "crit-neg-1",
			title: "Unhandled promise rejection in payment gateway causing silent data loss",
			verdict: "NEGATIVE",
			severity: "CRITICAL",
			confidence: 0.97,
			detectedAt: subHours(new Date(), 1),
			practiceName: "Error Handling",
			practiceSlug: "error-handling",
			category: "Reliability",
		},
	},
};

/**
 * Finding with a very long title to test text wrapping.
 */
export const LongTitle: Story = {
	args: {
		finding: {
			...baseFinding,
			id: "long-1",
			title:
				"Reviewer identified a critical edge case in the payment processing pipeline where concurrent transactions could result in duplicate charge entries when the database connection pool is exhausted under high load conditions",
		},
	},
};

/**
 * Loading skeleton state.
 */
export const Loading: Story = {
	args: {
		finding: baseFinding,
		isLoading: true,
	},
};
