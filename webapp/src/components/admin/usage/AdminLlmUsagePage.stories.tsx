import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { AdminLlmUsagePage } from "./AdminLlmUsagePage";

/** Fixed "today" so the burn-rate projections render the same shot every time. */
const NOW = new Date("2026-07-10T12:00:00.000Z");

const baseReport: WorkspaceLlmUsageReport = {
	month: "2026-07",
	instanceMonthlyBudgetUsd: 25,
	byoMonthlyBudgetUsd: undefined,
	pricedTotalCostUsd: 13.4821,
	byoTotalCostUsd: 0,
	instanceBudgetVerdict: "WITHIN",
	byoBudgetVerdict: "WITHIN",
	instanceFundedPaused: false,
	byoPaused: false,
	unpricedEventCount: 0,
	byJobType: [
		{
			jobType: "PULL_REQUEST_REVIEW",
			pricedTotalCostUsd: 8.1034,
			byoTotalCostUsd: 0,
			unpricedEventCount: 0,
			inputTokens: 1_204_331,
			outputTokens: 88_412,
			cacheReadTokens: 640_112,
			cacheWriteTokens: 120_034,
			totalCalls: 312,
			events: 41,
		},
		{
			jobType: "MENTOR_TURN",
			pricedTotalCostUsd: 3.9902,
			byoTotalCostUsd: 0,
			unpricedEventCount: 0,
			inputTokens: 402_118,
			outputTokens: 61_240,
			cacheReadTokens: 210_400,
			cacheWriteTokens: 44_020,
			totalCalls: 128,
			events: 64,
		},
		{
			jobType: "ISSUE_REVIEW",
			pricedTotalCostUsd: 1.3885,
			byoTotalCostUsd: 0,
			unpricedEventCount: 0,
			inputTokens: 150_221,
			outputTokens: 20_114,
			cacheReadTokens: 80_010,
			cacheWriteTokens: 12_450,
			totalCalls: 54,
			events: 12,
		},
	],
	byDay: [
		{
			day: new Date("2026-07-01"),
			pricedTotalCostUsd: 2.1,
			byoTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 14,
		},
		{
			day: new Date("2026-07-02"),
			pricedTotalCostUsd: 4.83,
			byoTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 31,
		},
		{
			day: new Date("2026-07-03"),
			pricedTotalCostUsd: 0.92,
			byoTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 6,
		},
		{
			day: new Date("2026-07-06"),
			pricedTotalCostUsd: 5.6321,
			byoTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 66,
		},
	],
};

/** The same month with the workspace running part of its work on its own connected provider. */
const withOwnProvider: WorkspaceLlmUsageReport = {
	...baseReport,
	byoMonthlyBudgetUsd: 10,
	byoTotalCostUsd: 2.4,
	byJobType: baseReport.byJobType.map((row, index) =>
		index === 1 ? { ...row, byoTotalCostUsd: 2.4 } : row,
	),
	byDay: baseReport.byDay.map((row, index) =>
		index === 3 ? { ...row, byoTotalCostUsd: 2.4 } : row,
	),
};

/**
 * The workspace admin's cost-control page. Two independently owned caps — the shared-model budget
 * the host sets, and the own-provider cap the workspace sets for itself — each with its own meter,
 * its own pause banner, and its own pre-wall warning.
 */
const meta = {
	component: AdminLlmUsagePage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		month: "2026-07",
		isCurrentMonth: true,
		workspaceSlug: "acme",
		report: baseReport,
		isLoading: false,
		error: null,
		now: NOW,
		onRetry: fn(),
		onPrevMonth: fn(),
		onNextMonth: fn(),
		onEditByoCap: fn(),
	},
} satisfies Meta<typeof AdminLlmUsagePage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Comfortably inside the host's budget, with no provider of the workspace's own connected yet. */
export const WithinBudget: Story = {};

/** Both sides live and under their caps — the two meters never merge into one number. */
export const BothCapsHealthy: Story = {
	args: { report: withOwnProvider },
};

/** 84% of the workspace's own cap is gone — warned as a status, with the date the pace reaches it. */
export const ApproachingOwnCap: Story = {
	args: {
		report: { ...withOwnProvider, byoTotalCostUsd: 8.4 },
	},
};

/** 88% of the host's budget is gone. Same shape, different owner — and a different remedy. */
export const ApproachingSharedBudget: Story = {
	args: {
		report: { ...withOwnProvider, pricedTotalCostUsd: 22 },
	},
};

/**
 * Day 2 of the month: the same warning without a projection. One busy afternoon is not a pace, so
 * the page says nothing rather than guessing.
 */
export const ApproachingWithoutProjection: Story = {
	args: {
		now: new Date("2026-07-02T12:00:00.000Z"),
		report: { ...withOwnProvider, byoTotalCostUsd: 8.4 },
	},
};

/** The workspace spent its own cap — the one pause its admin can lift themselves. */
export const OwnCapExhausted: Story = {
	args: {
		report: {
			...withOwnProvider,
			byoTotalCostUsd: 10.12,
			byoBudgetVerdict: "EXHAUSTED",
			byoPaused: true,
		},
	},
};

/** Unpriced calls on the workspace's own models: the cap can't be checked, so work stops. */
export const OwnCapUnverifiable: Story = {
	args: {
		report: {
			...withOwnProvider,
			byoBudgetVerdict: "UNVERIFIABLE",
			byoPaused: true,
			unpricedEventCount: 7,
		},
	},
};

/** The host's budget is spent. A warning, not a catastrophe — the workspace's own provider runs on. */
export const SharedBudgetExhausted: Story = {
	args: {
		report: {
			...withOwnProvider,
			pricedTotalCostUsd: 25.0142,
			instanceBudgetVerdict: "EXHAUSTED",
			instanceFundedPaused: true,
		},
	},
};

/** Unpriced shared-model calls — only the host can fix this, and the copy never pretends otherwise. */
export const SharedBudgetUnverifiable: Story = {
	args: {
		report: {
			...withOwnProvider,
			instanceBudgetVerdict: "UNVERIFIABLE",
			instanceFundedPaused: true,
			unpricedEventCount: 7,
		},
	},
};

/** Both caps spent: the actionable one leads. */
export const BothPaused: Story = {
	args: {
		report: {
			...withOwnProvider,
			pricedTotalCostUsd: 25.0142,
			byoTotalCostUsd: 10.12,
			instanceBudgetVerdict: "EXHAUSTED",
			instanceFundedPaused: true,
			byoBudgetVerdict: "EXHAUSTED",
			byoPaused: true,
		},
	},
};

/** No provider of their own connected — a quiet offer rather than an empty meter. */
export const NoOwnProvider: Story = {
	args: { report: baseReport },
};

/** Own-provider spend with no cap on it yet: the meter is absent, the offer to cap it is not. */
export const OwnProviderUncapped: Story = {
	args: {
		report: { ...withOwnProvider, byoMonthlyBudgetUsd: undefined },
	},
};

/** A $0 own cap pauses immediately — it reads 100% used, not "—". */
export const ZeroOwnCap: Story = {
	args: {
		report: {
			...withOwnProvider,
			byoMonthlyBudgetUsd: 0,
			byoTotalCostUsd: 0,
			byoBudgetVerdict: "EXHAUSTED",
			byoPaused: true,
		},
	},
};

/** The host set no budget at all — spend is still reported, there is simply nothing to fill. */
export const NoSharedBudget: Story = {
	args: {
		report: { ...withOwnProvider, instanceMonthlyBudgetUsd: undefined },
	},
};

/** A past month that ended over budget — nothing is paused in a month that has already closed. */
export const PastMonth: Story = {
	args: {
		month: "2026-06",
		isCurrentMonth: false,
		report: {
			...withOwnProvider,
			month: "2026-06",
			pricedTotalCostUsd: 25.0142,
			instanceBudgetVerdict: "EXHAUSTED",
		},
	},
};

/** No usage recorded in the selected month. */
export const Empty: Story = {
	args: {
		report: {
			...baseReport,
			pricedTotalCostUsd: 0,
			byoTotalCostUsd: 0,
			byJobType: [],
			byDay: [],
		},
	},
};

/**
 * Some calls ran on a model with no pricing row, so both reported totals — and the caps that read
 * them — under-count. A secondary callout explains the data-quality gap and who can close it.
 */
export const UncostedUsage: Story = {
	args: {
		report: { ...withOwnProvider, unpricedEventCount: 42 },
	},
};

/** A single uncosted call — the callout reads "1 call", not "1 calls". */
export const SingleUncostedCall: Story = {
	args: {
		report: { ...withOwnProvider, unpricedEventCount: 1 },
	},
};

/** Report still loading — both cap cards and the by-job-type table shell are skeletoned in place. */
export const Loading: Story = {
	args: {
		report: undefined,
		isLoading: true,
	},
};

/** Report failed to load — a 5xx is retryable, so the alert offers a Retry. */
export const ErrorState: Story = {
	args: {
		report: undefined,
		error: { status: 500, detail: "Failed to build the usage report." },
	},
};

/** A 403 is not retryable, so the alert explains the block without offering a Retry. */
export const ForbiddenError: Story = {
	args: {
		report: undefined,
		error: { status: 403, detail: "Workspace admin access is required." },
	},
};
