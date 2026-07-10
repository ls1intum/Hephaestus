import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { CohortPracticeStatus, PracticeReportSummary } from "@/api/types.gen";
import { PracticeOverviewPage } from "./PracticeOverviewPage";

const cohort: CohortPracticeStatus[] = [
	{
		name: "Clear PR description",
		slug: "clear-pr",
		strengthCount: 6,
		developingCount: 3,
		mixedCount: 2,
		noActivityCount: 4,
	},
	{
		name: "Small PRs",
		slug: "small-prs",
		strengthCount: 8,
		developingCount: 1,
		mixedCount: 0,
		noActivityCount: 6,
	},
	{
		name: "Reproduce before fixing",
		slug: "repro-first",
		suppressed: true,
	},
];

const roster: PracticeReportSummary[] = [
	{
		userId: 42,
		userLogin: "zoe",
		name: "Zoe Attention",
		avatarUrl: "",
		needsAttention: true,
		attentionReasons: ["Clear PR description: gaps to work on this cycle"],
		standings: [
			{ name: "Clear PR description", slug: "clear-pr", standing: "DEVELOPING" },
			{ name: "Small PRs", slug: "small-prs", standing: "NO_ACTIVITY" },
		],
	},
	{
		userId: 43,
		userLogin: "aaron",
		name: "Aaron Fine",
		avatarUrl: "",
		needsAttention: false,
		attentionReasons: [],
		standings: [
			{ name: "Clear PR description", slug: "clear-pr", standing: "STRENGTH" },
			{ name: "Small PRs", slug: "small-prs", standing: "MIXED" },
		],
	},
];

const meta = {
	component: PracticeOverviewPage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<QueryClientProvider client={new QueryClient()}>
				<Story />
			</QueryClientProvider>
		),
	],
	args: {
		workspaceSlug: "demo",
		isLoading: false,
		isForbidden: false,
		cohort,
		roster,
	},
} satisfies Meta<typeof PracticeOverviewPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CohortHealth: Story = {};

export const Roster: Story = {
	args: { cohort: [], roster },
};

export const Loading: Story = {
	args: { isLoading: true, cohort: undefined, roster: undefined },
};

export const Forbidden: Story = {
	args: { isForbidden: true, cohort: undefined, roster: undefined },
};
