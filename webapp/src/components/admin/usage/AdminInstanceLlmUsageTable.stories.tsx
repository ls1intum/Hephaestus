import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
import { AdminInstanceLlmUsageTable } from "./AdminInstanceLlmUsageTable";

const rows: AdminWorkspaceLlmUsage[] = [
	{
		workspaceId: 1,
		workspaceSlug: "obsphera",
		displayName: "Obsphera",
		monthlyBudgetUsd: 25,
		costUsd: 25.0142,
		events: 118,
		overBudget: true,
	},
	{
		workspaceId: 2,
		workspaceSlug: "hephaestus-dev",
		displayName: "Hephaestus Dev",
		monthlyBudgetUsd: 100,
		costUsd: 13.4821,
		events: 74,
		overBudget: false,
	},
	{
		workspaceId: 3,
		workspaceSlug: "sandbox",
		displayName: "Sandbox",
		costUsd: 0.42,
		events: 3,
		overBudget: false,
	},
];

/**
 * Instance-admin table of every workspace's LLM spend for one month, with per-row budget
 * cap editing raised via `onEditBudget`. Pure/presentational.
 */
const meta = {
	component: AdminInstanceLlmUsageTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		rows,
		isCurrentMonth: true,
		isLoading: false,
		isError: false,
		onEditBudget: fn(),
	},
} satisfies Meta<typeof AdminInstanceLlmUsageTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Current month with mixed statuses: over budget, capped and OK, and uncapped. */
export const Default: Story = {};

/**
 * A past month. `overBudget` is computed from the workspace's *current* cap, so it can't say
 * anything about a finished month — every status reads as a neutral dash.
 */
export const PastMonth: Story = {
	args: { isCurrentMonth: false },
};

/** The rollup left-joins from workspace, so zero rows means the instance has no workspaces. */
export const Empty: Story = {
	args: { rows: [] },
};

/** Rollup still loading. */
export const Loading: Story = {
	args: { rows: [], isLoading: true },
};

/** Rollup failed to load. */
export const ErrorState: Story = {
	args: { rows: [], isError: true },
};
