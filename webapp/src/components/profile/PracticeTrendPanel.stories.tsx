import type { Meta, StoryObj } from "@storybook/react";
import type { PracticeTrend, TrendOpportunity } from "@/api/types.gen";
import { PracticeTrendPanel } from "./PracticeTrendPanel";

const opportunities: TrendOpportunity[] = Array.from({ length: 8 }, (_, index) => ({
	index,
	occurredAt: new Date(`2026-08-${String(index + 1).padStart(2, "0")}T09:00:00Z`),
	workKind: "PULL_REQUEST",
	reviewedWorkId: index + 1,
	bundle: index < 4 ? "PREVIOUS" : "CURRENT",
	outcomes:
		index < 4
			? {
					demonstratedStrengths: 1,
					safeAvoidances: 0,
					commissionProblems: 1,
					omissionGaps: 1,
					notApplicable: 0,
				}
			: {
					demonstratedStrengths: 2,
					safeAvoidances: 1,
					commissionProblems: 0,
					omissionGaps: 1,
					notApplicable: 0,
				},
}));

const base: PracticeTrend = {
	slug: "maintainable-code",
	scope: "GROUP",
	direction: "IMPROVING",
	support: {
		currentOpportunities: 4,
		previousOpportunities: 4,
		opportunitiesUntilComparable: 0,
		comparablePractices: 3,
		eligiblePractices: 5,
		calendarSpanDays: 8,
		bundleSize: 4,
		ropeHalfWidth: 0.15,
		credibilityThreshold: 0.9,
	},
	previousOutcomes: {
		demonstratedStrengths: 2,
		safeAvoidances: 1,
		commissionProblems: 3,
		omissionGaps: 2,
		notApplicable: 0,
	},
	currentOutcomes: {
		demonstratedStrengths: 5,
		safeAvoidances: 1,
		commissionProblems: 1,
		omissionGaps: 1,
		notApplicable: 0,
	},
	opportunities,
};

const withDirection = (direction: PracticeTrend["direction"]): PracticeTrend => ({
	...base,
	direction,
});

const meta = {
	component: PracticeTrendPanel,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeTrendPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Improving: Story = { args: { trend: withDirection("IMPROVING") } };
export const Declining: Story = { args: { trend: withDirection("DECLINING") } };
/** The everyday case: eight reviewed work items are rarely enough to claim a direction either way. */
export const Uncertain: Story = { args: { trend: withDirection("UNCERTAIN") } };

const insufficient = (count: number): PracticeTrend => ({
	...base,
	direction: "INSUFFICIENT_EVIDENCE",
	currentOutcomes: undefined,
	previousOutcomes: undefined,
	opportunities: opportunities.slice(0, count).map((item) => ({ ...item, bundle: "OLDER" })),
	support: {
		...base.support,
		currentOpportunities: count,
		previousOpportunities: 0,
		opportunitiesUntilComparable: 3,
		calendarSpanDays: count === 0 ? undefined : count,
	},
});

export const InsufficientEvidenceNone: Story = { args: { trend: insufficient(0) } };
export const InsufficientEvidenceWithZeroOpportunities: Story = {
	args: { trend: insufficient(0) },
};
export const InsufficientEvidenceWithTwoOpportunities: Story = { args: { trend: insufficient(2) } };
export const UncertainWithFullBundle: Story = {
	args: { trend: withDirection("UNCERTAIN") },
};
