import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { PracticeReportSummary } from "@/api/types.gen";
import { RosterTable } from "./RosterTable";

/** Mentor roster: criterion-referenced standing chips per developer in server order — no score, rank, or position column. */
const meta = {
	component: RosterTable,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: { onSelectDeveloper: fn() },
} satisfies Meta<typeof RosterTable>;

export default meta;
type Story = StoryObj<typeof meta>;

const entries: PracticeReportSummary[] = [
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

/** A needs-attention row (server-first) above a clean row. */
export const Default: Story = {
	args: { entries },
	play: async ({ canvasElement }) => {
		// One row per developer, in the given (server) order — the table never re-sorts.
		const canvas = within(canvasElement);
		const buttons = canvas.getAllByRole("button");
		await expect(buttons).toHaveLength(2);
		await expect(within(buttons[0]).getByText("Zoe Attention")).toBeVisible();
		await expect(within(buttons[1]).getByText("Aaron Fine")).toBeVisible();
	},
};

/** Every developer flagged, each with plain-language attention reasons. */
export const NeedsAttention: Story = {
	args: {
		entries: entries.map((entry) => ({
			...entry,
			needsAttention: true,
			attentionReasons: ["Clear PR description: gaps to work on this cycle"],
			standings: entry.standings.map((cell) => ({ ...cell, status: "DEVELOPING" as const })),
		})),
	},
	play: async ({ canvasElement }) => {
		// Attention reasons render as badges on every flagged row.
		const canvas = within(canvasElement);
		await expect(
			canvas.getAllByText("Clear PR description: gaps to work on this cycle"),
		).toHaveLength(2);
	},
};

/** No developers with activity yet — a header-only table. */
export const Empty: Story = { args: { entries: [] } };

/** Activating a developer opens the drill-down. */
export const OpensDrillDown: Story = {
	args: { entries },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getAllByRole("button")[0]);
		await expect(args.onSelectDeveloper).toHaveBeenCalledWith(entries[0]);
	},
};

/**
 * A realistic ~12-area roster row: many area chips wrap onto multiple lines per developer, with
 * DEVELOPING/MIXED areas (the ones that need attention) ordered first. Also exercises a mix of
 * trend glyphs (IMPROVING, WORSENING, NEW, STEADY) so all four render distinctly.
 */
export const ManyAreasWithTrends: Story = {
	args: {
		entries: [
			{
				userId: 44,
				userLogin: "priya",
				name: "Priya Chandra",
				avatarUrl: "",
				needsAttention: true,
				attentionReasons: ["Test coverage: gaps to work on this cycle"],
				standings: [
					{
						areaName: "Clear PR description",
						areaSlug: "clear-pr",
						status: "STRENGTH",
						trend: "STEADY",
					},
					{ areaName: "Small PRs", areaSlug: "small-prs", status: "STRENGTH", trend: "IMPROVING" },
					{
						areaName: "Test coverage",
						areaSlug: "test-coverage",
						status: "DEVELOPING",
						trend: "WORSENING",
					},
					{
						areaName: "Reproduce before fixing",
						areaSlug: "repro-first",
						status: "MIXED",
						trend: "STEADY",
					},
					{
						areaName: "Reviewer craft",
						areaSlug: "reviewer-craft",
						status: "STRENGTH",
						trend: "STEADY",
					},
					{
						areaName: "Commit hygiene",
						areaSlug: "commit-hygiene",
						status: "NO_ACTIVITY",
						trend: "STEADY",
					},
					{ areaName: "Issue triage", areaSlug: "issue-triage", status: "STRENGTH", trend: "NEW" },
					{ areaName: "API design", areaSlug: "api-design", status: "MIXED", trend: "STEADY" },
					{
						areaName: "Documentation",
						areaSlug: "documentation",
						status: "STRENGTH",
						trend: "STEADY",
					},
					{
						areaName: "Refactoring discipline",
						areaSlug: "refactoring",
						status: "DEVELOPING",
						trend: "STEADY",
					},
					{
						areaName: "CI hygiene",
						areaSlug: "ci-hygiene",
						status: "NO_ACTIVITY",
						trend: "STEADY",
					},
					{
						areaName: "Security awareness",
						areaSlug: "security",
						status: "STRENGTH",
						trend: "STEADY",
					},
				],
			},
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// All 12 area names render, wrapped in one row for the developer.
		await expect(canvas.getByText("Clear PR description")).toBeVisible();
		await expect(canvas.getByText("Security awareness")).toBeVisible();
		// Each trend glyph is present with its own accessible label.
		await expect(canvas.getByRole("img", { name: "improving" })).toBeVisible();
		await expect(canvas.getByRole("img", { name: "worsening" })).toBeVisible();
		await expect(canvas.getByRole("img", { name: "new since last cycle" })).toBeVisible();
		// STEADY areas render no glyph, so there are exactly three trend glyphs, not twelve.
		await expect(
			canvas.getAllByRole("img", { name: /improving|worsening|new since last cycle/ }),
		).toHaveLength(3);
	},
};
