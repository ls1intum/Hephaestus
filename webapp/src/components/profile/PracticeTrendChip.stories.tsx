import type { Meta, StoryObj } from "@storybook/react";
import type { TrendSupport } from "@/api/types.gen";
import { PracticeTrendChip } from "./PracticeTrendChip";

const wellSupported: TrendSupport = {
	currentOpportunities: 4,
	previousOpportunities: 4,
	opportunitiesUntilComparable: 0,
	calendarSpanDays: 9,
	bundleSize: 4,
	ropeHalfWidth: 0.15,
	credibilityThreshold: 0.9,
};

const none: TrendSupport = {
	...wellSupported,
	currentOpportunities: 2,
	previousOpportunities: 0,
	opportunitiesUntilComparable: 3,
};

const meta = {
	component: PracticeTrendChip,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { scope: "practice" },
} satisfies Meta<typeof PracticeTrendChip>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Improving: Story = { args: { direction: "IMPROVING", support: wellSupported } };
export const Declining: Story = { args: { direction: "DECLINING", support: wellSupported } };
/** The everyday case: eight pieces of reviewed work rarely separate far enough to claim a direction. */
export const Uncertain: Story = { args: { direction: "UNCERTAIN", support: wellSupported } };
export const InsufficientEvidence: Story = {
	args: { direction: "INSUFFICIENT_EVIDENCE", support: none },
};

/**
 * A group trend pools its practices' finished comparisons, so its tooltip never claims to have held
 * one bundle against another — hover to read the difference.
 */
export const GroupScope: Story = {
	args: {
		direction: "IMPROVING",
		scope: "group",
		support: { ...wellSupported, comparablePractices: 3, eligiblePractices: 5 },
	},
};
