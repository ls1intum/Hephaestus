import type { Meta, StoryObj } from "@storybook/react";
import { VerdictBadge } from "./VerdictBadge";

/**
 * Color-coded badge indicating the verdict of a practice finding.
 * Maps to existing provider color tokens for light/dark mode support.
 */
const meta = {
	component: VerdictBadge,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Displays the verdict of a practice finding as a color-coded pill badge.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		verdict: {
			control: "select",
			options: ["POSITIVE", "NEGATIVE", "NOT_APPLICABLE", "NEEDS_REVIEW"],
			description: "The finding verdict to display",
		},
	},
} satisfies Meta<typeof VerdictBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Positive: Story = {
	args: { verdict: "POSITIVE" },
};

export const Negative: Story = {
	args: { verdict: "NEGATIVE" },
};

export const NotApplicable: Story = {
	args: { verdict: "NOT_APPLICABLE" },
};

export const NeedsReview: Story = {
	args: { verdict: "NEEDS_REVIEW" },
};
