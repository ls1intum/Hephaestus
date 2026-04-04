import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { mockSummaries } from "./__fixtures__/mock-data";
import { PracticeSummaryCard } from "./PracticeSummaryCard";

const baseSummary = mockSummaries[0];

/**
 * Summary card for a single practice, showing positive/negative counts
 * and a visual ratio bar.
 */
const meta = {
	component: PracticeSummaryCard,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Displays a per-practice summary with positive/negative finding counts and a visual ratio indicator.",
			},
		},
	},
	tags: ["autodocs"],
	args: {
		onSelect: fn(),
	},
	argTypes: {
		isSelected: { control: "boolean", description: "Whether the card is selected" },
		isLoading: { control: "boolean", description: "Whether the card is loading" },
	},
	decorators: [
		(Story) => (
			<div className="w-64">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof PracticeSummaryCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Mostly positive findings for this practice.
 */
export const PositiveHeavy: Story = {
	args: {
		summary: baseSummary,
	},
};

/**
 * Mostly negative findings for this practice.
 */
export const NegativeHeavy: Story = {
	args: {
		summary: {
			...baseSummary,
			practiceName: "Test Coverage",
			practiceSlug: "test-coverage",
			category: "Testing",
			positiveCount: 3,
			negativeCount: 12,
			totalFindings: 15,
		},
	},
};

/**
 * Roughly equal positive and negative findings.
 */
export const Mixed: Story = {
	args: {
		summary: {
			...baseSummary,
			practiceName: "Documentation Standards",
			practiceSlug: "documentation-standards",
			category: "Documentation",
			positiveCount: 8,
			negativeCount: 7,
			totalFindings: 15,
		},
	},
};

/**
 * Card in selected state with ring highlight.
 */
export const Selected: Story = {
	args: {
		summary: baseSummary,
		isSelected: true,
	},
};

/**
 * Loading skeleton state.
 */
export const Loading: Story = {
	args: {
		summary: baseSummary,
		isLoading: true,
	},
};

/**
 * Practice with a very long name to test text wrapping.
 */
export const LongName: Story = {
	args: {
		summary: {
			...baseSummary,
			practiceName: "Comprehensive Authentication and Authorization Security Review Standards",
			category: "Security & Compliance",
		},
	},
};
