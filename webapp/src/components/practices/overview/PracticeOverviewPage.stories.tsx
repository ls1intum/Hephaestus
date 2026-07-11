import type { Meta, StoryObj } from "@storybook/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { expect, fn, userEvent, within } from "storybook/test";
import type { CohortAreaStatus, PracticeReportSummary } from "@/api/types.gen";
import { PracticeOverviewPage } from "./PracticeOverviewPage";

const cohort: CohortAreaStatus[] = [
	{
		areaName: "Clear PR description",
		areaSlug: "clear-pr",
		strengthCount: 6,
		developingCount: 3,
		mixedCount: 2,
		noActivityCount: 4,
	},
	{
		areaName: "Small PRs",
		areaSlug: "small-prs",
		strengthCount: 8,
		developingCount: 1,
		mixedCount: 0,
		noActivityCount: 6,
	},
	{
		areaName: "Reproduce before fixing",
		areaSlug: "repro-first",
		suppressed: true,
	},
	{
		areaName: "Security awareness",
		areaSlug: "security",
		noData: true,
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
			{
				areaName: "Clear PR description",
				areaSlug: "clear-pr",
				status: "DEVELOPING",
				trend: "STEADY",
			},
			{ areaName: "Small PRs", areaSlug: "small-prs", status: "NO_ACTIVITY", trend: "STEADY" },
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
			{
				areaName: "Clear PR description",
				areaSlug: "clear-pr",
				status: "STRENGTH",
				trend: "IMPROVING",
			},
			{ areaName: "Small PRs", areaSlug: "small-prs", status: "MIXED", trend: "STEADY" },
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

export const CohortHealth: Story = {
	play: async ({ canvasElement }) => {
		// The suppressed area and the no-data area render distinct, honest messages side by side.
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Not enough recent activity to show this safely.")).toBeVisible();
		await expect(canvas.getByText("No activity in this area yet.")).toBeVisible();
	},
};

export const Roster: Story = {
	args: { cohort: [], roster },
	play: async ({ canvasElement }) => {
		// Switch to the Roster tab, then confirm a trend glyph renders for Aaron's improving area.
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("tab", { name: "Roster" }));
		await expect(canvas.getByRole("img", { name: "improving" })).toBeVisible();
	},
};

export const Loading: Story = {
	args: { isLoading: true, cohort: undefined, roster: undefined },
};

export const Forbidden: Story = {
	args: { isForbidden: true, cohort: undefined, roster: undefined },
};

export const ErrorState: Story = {
	args: { isError: true, onRetry: fn(), cohort: undefined, roster: undefined },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Couldn't load the practice overview")).toBeVisible();

		const retry = canvas.getByRole("button", { name: /retry/i });
		await userEvent.click(retry);
		await expect(args.onRetry).toHaveBeenCalledOnce();
	},
};

export const MemberView: Story = {
	args: { showRoster: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByRole("tab", { name: /roster/i })).not.toBeInTheDocument();
	},
};
