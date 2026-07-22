import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { AdminWorkspaceLlmUsage } from "@/api/types.gen";
import { AdminInstanceLlmUsageTable } from "./AdminInstanceLlmUsageTable";

const rows: AdminWorkspaceLlmUsage[] = [
	{
		workspaceId: 1,
		workspaceSlug: "example-workspace",
		displayName: "Example Workspace",
		monthlyBudgetUsd: 25,
		pricedTotalCostUsd: 25.0142,
		byoTotalCostUsd: 0,
		events: 118,
		verdict: "EXHAUSTED",
	},
	{
		workspaceId: 2,
		workspaceSlug: "hephaestus-dev",
		displayName: "Hephaestus Dev",
		monthlyBudgetUsd: 100,
		pricedTotalCostUsd: 13.4821,
		byoTotalCostUsd: 2.1,
		events: 74,
		verdict: "WITHIN",
	},
	{
		workspaceId: 3,
		workspaceSlug: "sandbox",
		displayName: "Sandbox",
		pricedTotalCostUsd: 0.42,
		byoTotalCostUsd: 0,
		events: 3,
		verdict: "WITHIN",
	},
	{
		workspaceId: 4,
		workspaceSlug: "launchpad",
		displayName: "Launchpad",
		monthlyBudgetUsd: 50,
		pricedTotalCostUsd: 9.1,
		byoTotalCostUsd: 0,
		events: 22,
		verdict: "UNVERIFIABLE",
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
		error: null,
		onRetry: fn(),
		onEditBudget: fn(),
	},
} satisfies Meta<typeof AdminInstanceLlmUsageTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Current month with mixed statuses: over budget, capped and OK, uncapped, and unverifiable (some
 * usage has no price set — a warning line, never a badge; see the #1368 glossary).
 */
export const Default: Story = {};

/**
 * A past month. `verdict` is computed from the workspace's *current* cap, so it can't say
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

/** Rollup failed to load — a 5xx is retryable, so the alert offers a Retry. */
export const ErrorState: Story = {
	args: {
		rows: [],
		error: { status: 500, detail: "Failed to roll up LLM usage." },
	},
};

/** A 403 is not retryable, so the alert explains the block without offering a Retry. */
export const ForbiddenError: Story = {
	args: {
		rows: [],
		error: { status: 403, detail: "Instance admin access is required." },
	},
};
