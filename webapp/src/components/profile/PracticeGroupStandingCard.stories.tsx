import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { PracticeGroup, PracticeGroupStanding, PracticeStanding } from "@/api/types.gen";
import { PracticeGroupStandingCard } from "./PracticeGroupStandingCard";

const group: PracticeGroup = {
	id: 1,
	slug: "review-ready-work",
	name: "Packaging work for review",
	description: "Make changes easy to review.",
	displayOrder: 0,
	visibleInPracticeDashboards: true,
	autonomy: { effective: "AUTOMATIC", inherited: true, source: "WORKSPACE" },
	icon: "Package",
	color: "blue",
	createdAt: new Date("2026-01-01T00:00:00Z"),
};

const standing: PracticeGroupStanding = {
	groupSlug: group.slug,
	groupName: group.name,
	standing: "MIXED",
	observations: [],
	sources: [{ workKind: "scm.pull_request", count: 4 }],
};

const practices: PracticeStanding[] = [
	{
		slug: "small-changes",
		name: "Keep changes focused",
		standing: "STRENGTH",
		strengths: [],
		toWorkOn: [],
	},
	{ slug: "explain-why", name: "Explain why", standing: "DEVELOPING", strengths: [], toWorkOn: [] },
];

const meta = {
	title: "Profile/PracticeGroupStandingCard",
	component: PracticeGroupStandingCard,
	tags: ["autodocs"],
	args: {
		groups: [group],
		standings: { [group.slug]: standing },
		practicesByGroup: { [group.slug]: practices },
		isLoading: false,
		onOpenDetails: fn(),
	},
} satisfies Meta<typeof PracticeGroupStandingCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One card per standing the server can report, in the order the registry ranks them. */
const withStanding = (
	value: PracticeGroupStanding["standing"],
	practiceStandings: PracticeStanding["standing"][],
): Story["args"] => ({
	groups: [group],
	standings: { [group.slug]: { ...standing, standing: value } },
	practicesByGroup: {
		[group.slug]: practiceStandings.map((practiceStanding, index) => ({
			slug: `practice-${index}`,
			name: `Practice ${index + 1}`,
			standing: practiceStanding,
			strengths: [],
			toWorkOn: [],
		})),
	},
});

export const Default: Story = {};
export const Loading: Story = { args: { isLoading: true } };
export const Empty: Story = { args: { groups: [], standings: {}, practicesByGroup: {} } };
export const Failure: Story = { args: { error: new Error("Unavailable") } };

/** Problems predominate — the group a developer should look at first. */
export const NeedsAttention: Story = {
	args: withStanding("DEVELOPING", ["DEVELOPING", "DEVELOPING", "MIXED"]),
};

/** Strengths and problems in comparable measure. */
export const MixedFeedback: Story = {
	args: withStanding("MIXED", ["DEVELOPING", "MIXED", "STRENGTH"]),
};

/** Almost entirely positive — a confirmed habit rather than a clean slate. */
export const GoingWell: Story = {
	args: withStanding("STRENGTH", ["STRENGTH", "STRENGTH", "MIXED"]),
};

/** Reviewed, but nothing could be judged: the practices did not apply, or the evidence did not settle. */
export const NothingToReport: Story = {
	args: withStanding("NO_OPPORTUNITY", ["NO_OPPORTUNITY", "NO_OPPORTUNITY"]),
};

/** No practice in the group carries a current verdict yet. */
export const NotObserved: Story = {
	args: withStanding("NOT_OBSERVED", ["NOT_OBSERVED", "NOT_OBSERVED"]),
};

/** With a trend: the chip sits under the badge and explains its evidence on hover. */
export const WithTrend: Story = {
	args: {
		...withStanding("MIXED", ["DEVELOPING", "STRENGTH"]),
		standings: {
			[group.slug]: {
				...standing,
				direction: "IMPROVING",
				trendSupport: {
					currentOpportunities: 4,
					previousOpportunities: 4,
					opportunitiesUntilComparable: 0,
					bundleSize: 4,
					ropeHalfWidth: 0.15,
					credibilityThreshold: 0.9,
					calendarSpanDays: 12,
					comparablePractices: 2,
					eligiblePractices: 3,
				},
			},
		},
	},
};

/** More groups than the card shows at once, so the collapse and its count appear. */
export const ManyGroups: Story = {
	args: {
		groups: Array.from({ length: 5 }, (_, index) => ({
			...group,
			id: index + 1,
			slug: `group-${index}`,
			name: `Practice group ${index + 1}`,
		})),
		standings: {},
		practicesByGroup: {},
	},
};
