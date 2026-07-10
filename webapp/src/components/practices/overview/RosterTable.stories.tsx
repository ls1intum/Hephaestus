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

/** A needs-attention row (server-first) above a clean row. */
export const Default: Story = { args: { entries } };

/** Every developer flagged, each with plain-language attention reasons. */
export const NeedsAttention: Story = {
	args: {
		entries: entries.map((entry) => ({
			...entry,
			needsAttention: true,
			attentionReasons: ["Clear PR description: gaps to work on this cycle"],
			standings: entry.standings.map((cell) => ({ ...cell, standing: "DEVELOPING" as const })),
		})),
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
