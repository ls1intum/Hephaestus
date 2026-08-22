import type { Meta, StoryObj } from "@storybook/react";
import type { PracticeTrend, TrendOpportunity } from "@/api/types.gen";
import { PracticeTrendPanel } from "./PracticeTrendPanel";

const opportunities: TrendOpportunity[] = Array.from({ length: 8 }, (_, index) => ({
	index,
	occurredAt: new Date(`2026-08-${String(index + 1).padStart(2, "0")}T09:00:00Z`),
	artifactKind: "PULL_REQUEST",
	artifactId: index + 1,
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
	scope: "AREA",
	direction: "IMPROVING",
	support: {
		level: "WELL_SUPPORTED",
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

const withState = (
	direction: PracticeTrend["direction"],
	level: PracticeTrend["support"]["level"],
): PracticeTrend => ({ ...base, direction, support: { ...base.support, level } });

const meta = {
	component: PracticeTrendPanel,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeTrendPanel>;

export default meta;
type Story = StoryObj<typeof meta>;

export const ImprovingWellSupported: Story = {
	args: { trend: withState("IMPROVING", "WELL_SUPPORTED") },
};
export const ImprovingTentative: Story = { args: { trend: withState("IMPROVING", "TENTATIVE") } };
export const DecliningWellSupported: Story = {
	args: { trend: withState("DECLINING", "WELL_SUPPORTED") },
};
export const DecliningTentative: Story = { args: { trend: withState("DECLINING", "TENTATIVE") } };
export const StableWellSupported: Story = {
	args: { trend: withState("STABLE", "WELL_SUPPORTED") },
};
export const StableTentative: Story = { args: { trend: withState("STABLE", "TENTATIVE") } };
export const UncertainWellSupported: Story = {
	args: { trend: withState("UNCERTAIN", "WELL_SUPPORTED") },
};
export const UncertainTentative: Story = { args: { trend: withState("UNCERTAIN", "TENTATIVE") } };

const insufficient = (count: number): PracticeTrend => ({
	...base,
	direction: "INSUFFICIENT_EVIDENCE",
	currentOutcomes: undefined,
	previousOutcomes: undefined,
	opportunities: opportunities.slice(0, count).map((item) => ({ ...item, bundle: "OLDER" })),
	support: {
		...base.support,
		level: "NONE",
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
	args: { trend: withState("UNCERTAIN", "WELL_SUPPORTED") },
};
export const ImprovingAtTentativeSupport: Story = {
	args: { trend: withState("IMPROVING", "TENTATIVE") },
};
export const ImprovingAtWellSupported: Story = {
	args: { trend: withState("IMPROVING", "WELL_SUPPORTED") },
};
