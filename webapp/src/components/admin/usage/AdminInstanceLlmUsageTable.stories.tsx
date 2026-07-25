import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import {
	AdminInstanceLlmUsageTable,
	type AdminWorkspaceLlmUsageRow,
} from "./AdminInstanceLlmUsageTable";

/**
 * Mixed cap ownership, in the container's sort order (instance-funded cost desc): both caps set,
 * self cap only, instance cap only, and neither.
 */
const rows: AdminWorkspaceLlmUsageRow[] = [
	{
		// Both caps set; the instance cap is spent, so host-funded work is paused — the workspace's
		// own provider keeps running, because that cap is nowhere near.
		workspaceId: 1,
		workspaceSlug: "example-workspace",
		displayName: "Example Workspace",
		instanceMonthlyBudgetUsd: 25,
		pricedTotalCostUsd: 25.0142,
		instanceBudgetVerdict: "EXHAUSTED",
		instanceFundedPaused: true,
		byoMonthlyBudgetUsd: 40,
		byoTotalCostUsd: 6.5,
		byoBudgetVerdict: "WITHIN",
		byoPaused: false,
		events: 118,
	},
	{
		// Both caps set; the workspace is closing in on its own cap (86%), which is its own admins'
		// problem to solve — the instance admin can see it but cannot raise it.
		workspaceId: 2,
		workspaceSlug: "hephaestus-dev",
		displayName: "Hephaestus Dev",
		instanceMonthlyBudgetUsd: 100,
		pricedTotalCostUsd: 13.4821,
		instanceBudgetVerdict: "WITHIN",
		instanceFundedPaused: false,
		byoMonthlyBudgetUsd: 25,
		byoTotalCostUsd: 21.4,
		byoBudgetVerdict: "WITHIN",
		byoPaused: false,
		events: 74,
	},
	{
		// Instance cap only, three quarters spent, and some usage has no price on record — so the
		// meter is a floor, which the warning line says out loud.
		workspaceId: 4,
		workspaceSlug: "launchpad",
		displayName: "Launchpad",
		instanceMonthlyBudgetUsd: 50,
		pricedTotalCostUsd: 38.2,
		instanceBudgetVerdict: "UNVERIFIABLE",
		instanceFundedPaused: false,
		byoTotalCostUsd: 0,
		events: 22,
		byoBudgetVerdict: "WITHIN" as const,
		byoPaused: false,
	},
	{
		// Neither cap — nobody has taken responsibility for this workspace's spend yet.
		workspaceId: 3,
		workspaceSlug: "sandbox",
		displayName: "Sandbox",
		pricedTotalCostUsd: 0.42,
		instanceBudgetVerdict: "WITHIN",
		byoTotalCostUsd: 0,
		events: 3,
		byoBudgetVerdict: "WITHIN" as const,
		byoPaused: false,
		instanceFundedPaused: false,
	},
	{
		// Self cap only, and spent: this workspace pauses itself without the instance admin ever
		// setting a cap — the case that decides whether they bother setting one at all.
		workspaceId: 5,
		workspaceSlug: "atelier",
		displayName: "Atelier",
		pricedTotalCostUsd: 0,
		instanceBudgetVerdict: "WITHIN",
		byoMonthlyBudgetUsd: 12,
		byoTotalCostUsd: 12.4,
		byoBudgetVerdict: "EXHAUSTED",
		byoPaused: true,
		events: 40,
		instanceFundedPaused: false,
	},
];

const detailReport: WorkspaceLlmUsageReport = {
	month: "2026-07",
	instanceMonthlyBudgetUsd: 25,
	pricedTotalCostUsd: 25.0142,
	instanceBudgetVerdict: "EXHAUSTED",
	instanceFundedPaused: true,
	byoMonthlyBudgetUsd: 40,
	byoTotalCostUsd: 1.5,
	byoBudgetVerdict: "WITHIN",
	byoPaused: false,
	unpricedEventCount: 0,
	byJobType: [
		{
			jobType: "PULL_REQUEST_REVIEW",
			pricedTotalCostUsd: 25.0142,
			byoTotalCostUsd: 1.5,
			unpricedEventCount: 0,
			inputTokens: 80_000,
			outputTokens: 12_000,
			cacheReadTokens: 10_000,
			cacheWriteTokens: 2_000,
			totalCalls: 42,
			events: 18,
		},
	],
	byDay: [
		{
			day: new Date("2026-07-05T00:00:00.000Z"),
			pricedTotalCostUsd: 25.0142,
			byoTotalCostUsd: 1.5,
			unpricedEventCount: 0,
			events: 18,
		},
	],
};

/**
 * Instance-admin table of every workspace's LLM spend for one month, against both caps: the
 * instance cap the host funds (editable here, via `onEditBudget`) and the workspace's own self cap
 * (read-only — it is the workspace's money). Pure/presentational.
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
		expandedWorkspaceId: null,
		isDetailLoading: false,
		detailError: null,
		onRetryDetail: fn(),
		onToggleDetails: fn(),
		onEditBudget: fn(),
	},
} satisfies Meta<typeof AdminInstanceLlmUsageTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Current month with every cap combination: both caps, instance only, self only, neither — plus a
 * paused instance cap, a paused self cap, a near-cap warning and an unverifiable total.
 */
export const Default: Story = {};

/** One workspace expanded to show the existing by-job and daily usage rollups. */
export const Expanded: Story = {
	args: {
		expandedWorkspaceId: rows[0].workspaceId,
		detailReport,
	},
};

/**
 * Trouble that has not arrived yet: both caps past 80%, still running. This is the state a binary
 * in-budget pill could never show, and the one an admin can still act on.
 */
export const NearCap: Story = {
	args: {
		rows: [
			{
				workspaceId: 10,
				workspaceSlug: "close-call",
				displayName: "Close Call",
				instanceMonthlyBudgetUsd: 50,
				pricedTotalCostUsd: 41,
				instanceBudgetVerdict: "WITHIN",
				instanceFundedPaused: false,
				byoMonthlyBudgetUsd: 30,
				byoTotalCostUsd: 27.6,
				byoBudgetVerdict: "WITHIN",
				byoPaused: false,
				events: 210,
			},
		],
	},
};

/** Paused on the host's money — the one cap this admin can actually raise. */
export const PausedByInstanceCap: Story = {
	args: { rows: [rows[0]] },
};

/** Paused on the workspace's own money — raising the instance cap would change nothing. */
export const PausedBySelfCap: Story = {
	args: { rows: [rows[4]] },
};

/** Both caps spent: two badges, because raising only one would leave the workspace stopped. */
export const PausedByBothCaps: Story = {
	args: {
		rows: [
			{
				workspaceId: 11,
				workspaceSlug: "full-stop",
				displayName: "Full Stop",
				instanceMonthlyBudgetUsd: 20,
				pricedTotalCostUsd: 20.5,
				instanceBudgetVerdict: "EXHAUSTED",
				instanceFundedPaused: true,
				byoMonthlyBudgetUsd: 15,
				byoTotalCostUsd: 15,
				byoBudgetVerdict: "EXHAUSTED",
				byoPaused: true,
				events: 96,
			},
		],
	},
};

/**
 * A $0 instance cap is a supported state — it reads as 100% used and pauses immediately, rather
 * than as "no cap".
 */
export const ZeroInstanceCap: Story = {
	args: {
		rows: [
			{
				workspaceId: 12,
				workspaceSlug: "frozen",
				displayName: "Frozen",
				instanceMonthlyBudgetUsd: 0,
				pricedTotalCostUsd: 0,
				instanceBudgetVerdict: "EXHAUSTED",
				instanceFundedPaused: true,
				byoTotalCostUsd: 0,
				byoBudgetVerdict: "WITHIN",
				byoPaused: false,
				events: 0,
			},
		],
	},
};

/**
 * Workspaces that have not set a cap of their own: the self-cap column reads as unset, which is the
 * signal that nobody is bounding that workspace's own-provider spend.
 */
export const NoSelfCapsSet: Story = {
	args: {
		rows: rows.map((row) => ({
			...row,
			byoMonthlyBudgetUsd: undefined,
			byoBudgetVerdict: "WITHIN" as const,
			byoPaused: false,
		})),
	},
};

/**
 * A past month. The verdicts are computed from the workspace's *current* caps, so they can't say
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
