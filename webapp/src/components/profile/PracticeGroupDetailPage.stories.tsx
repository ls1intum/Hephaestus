import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { PracticeGroup, PracticeGroupStanding } from "@/api/types.gen";
import { PracticeGroupDetailPage } from "./PracticeGroupDetailPage";

const group: PracticeGroup = {
	id: 1,
	slug: "review-ready-work",
	name: "Packaging work for review",
	description: "Make changes easy to review before asking for feedback.",
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
	guidance: "Keep changes focused on one concern.",
	observations: [],
	sources: [],
};

const meta = {
	title: "Profile/PracticeGroupDetailPage",
	component: PracticeGroupDetailPage,
	args: {
		group,
		standing,
		practices: [
			{
				slug: "small-changes",
				name: "Keep changes focused",
				whyItMatters: "Focused changes are faster to understand.",
			},
		],
		practiceStandings: { "small-changes": "MIXED" },
		reviewRuns: [],
		isLoading: false,
		onBack: fn(),
		onSelectPractice: fn(),
	},
} satisfies Meta<typeof PracticeGroupDetailPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
export const Loading: Story = { args: { isLoading: true } };
export const Missing: Story = { args: { group: undefined } };
export const Failure: Story = { args: { error: new Error("Unavailable") } };
