import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import type { FxRateInfo, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { expectPageReflows, expectTablesScrollInPlace } from "@/test/reflow";
import { AdminLlmUsagePage } from "./AdminLlmUsagePage";

/** Fixed "today" so the burn-rate projections render the same shot every time. */
const NOW = new Date("2026-07-10T12:00:00.000Z");

const baseReport: WorkspaceLlmUsageReport = {
	month: "2026-07",
	instanceMonthlyBudgetUsd: 25,
	ownProviderMonthlyBudgetUsd: undefined,
	instanceTotalCostUsd: 13.4821,
	ownProviderTotalCostUsd: 0,
	instanceBudgetVerdict: "WITHIN",
	ownProviderBudgetVerdict: "WITHIN",
	instancePaused: false,
	ownProviderPaused: false,
	unpricedEventCount: 0,
	byJobType: [
		{
			jobType: "PULL_REQUEST_REVIEW",
			instanceTotalCostUsd: 8.1034,
			ownProviderTotalCostUsd: 0,
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
			instanceTotalCostUsd: 3.9902,
			ownProviderTotalCostUsd: 0,
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
			instanceTotalCostUsd: 1.3885,
			ownProviderTotalCostUsd: 0,
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
			instanceTotalCostUsd: 2.1,
			ownProviderTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 14,
		},
		{
			day: new Date("2026-07-02"),
			instanceTotalCostUsd: 4.83,
			ownProviderTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 31,
		},
		{
			day: new Date("2026-07-03"),
			instanceTotalCostUsd: 0.92,
			ownProviderTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 6,
		},
		{
			day: new Date("2026-07-06"),
			instanceTotalCostUsd: 5.6321,
			ownProviderTotalCostUsd: 0,
			unpricedEventCount: 0,
			events: 66,
		},
	],
};

/** The same month with the workspace running part of its work on a provider of its own. */
const withOwnProvider: WorkspaceLlmUsageReport = {
	...baseReport,
	ownProviderMonthlyBudgetUsd: 10,
	ownProviderTotalCostUsd: 2.4,
	byJobType: baseReport.byJobType.map((row, index) =>
		index === 1 ? { ...row, ownProviderTotalCostUsd: 2.4 } : row,
	),
	byDay: baseReport.byDay.map((row, index) =>
		index === 3 ? { ...row, ownProviderTotalCostUsd: 2.4 } : row,
	),
};

/**
 * The rate an instance with a EUR display currency reports for the running month. Already
 * inverted: multiply a USD amount by it to get the estimate.
 */
const eurToday: FxRateInfo = {
	currencyCode: "EUR",
	ratePerUsd: 0.878966,
	rateDate: new Date("2026-07-24T00:00:00.000Z"),
};

/**
 * The workspace admin's cost-control page. Two independently owned caps — the shared-model budget
 * the host sets, and the provider cap the workspace sets for itself — each with its own meter, its
 * own pause banner, and its own pre-wall warning.
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
		onEditOwnProviderCap: fn(),
	},
} satisfies Meta<typeof AdminLlmUsagePage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Comfortably inside the shared-model budget, with no provider of the workspace's own yet.
 *
 * Also the default currency state: no instance has a display currency until someone configures
 * one, so every figure here is USD and nothing explains itself.
 */
export const WithinBudget: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.queryByText(/ECB reference rate/)).toBeNull();
		await expect(canvasElement.querySelectorAll('[role="img"]')).toHaveLength(0);
	},
};

/**
 * The same month on an instance that displays EUR. USD stays the headline figure and the estimate
 * trails it, marked `≈` at every occurrence; the caption underneath explains all of them at once
 * and names the rate.
 */
export const DisplayCurrencyThisMonth: Story = {
	args: {
		report: { ...withOwnProvider, fx: eurToday },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(/EUR amounts are estimates at the ECB reference rate for Jul 24, 2026/),
		).toBeVisible();
		// Symbols would be announced as "tilde operator" or dropped — every estimate says words.
		await expect(canvas.getAllByLabelText(/^approximately /).length).toBeGreaterThan(0);
	},
};

/**
 * A closed month. Its rate is the last one published inside that month, so the figures are frozen
 * — the caption says so instead of quoting a live rate that would drift under a past total.
 */
export const DisplayCurrencyClosedMonth: Story = {
	args: {
		month: "2026-06",
		isCurrentMonth: false,
		report: {
			...withOwnProvider,
			month: "2026-06",
			fx: {
				currencyCode: "EUR",
				ratePerUsd: 0.874312,
				rateDate: new Date("2026-06-30T00:00:00.000Z"),
			},
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(/The last rate published that month, so past figures don't change/),
		).toBeVisible();
	},
};

/**
 * A currency whose symbol would read as dollars beside the USD figure it estimates — CAD, AUD and
 * friends — falls back to the ISO code rather than putting two `$` in one line.
 */
export const DisplayCurrencyWithAmbiguousSymbol: Story = {
	args: {
		report: {
			...withOwnProvider,
			fx: {
				currencyCode: "CAD",
				ratePerUsd: 1.3642,
				rateDate: new Date("2026-07-24T00:00:00.000Z"),
			},
		},
	},
};

/** Both sides live and under their caps — the two meters never merge into one number. */
export const BothCapsHealthy: Story = {
	args: { report: withOwnProvider },
};

/** 84% of the provider cap is gone — warned as a status, with the date the pace reaches it. */
export const ApproachingProviderCap: Story = {
	args: {
		report: { ...withOwnProvider, ownProviderTotalCostUsd: 8.4 },
	},
};

/** 88% of the shared-model budget is gone. Same shape, different owner — and a different remedy. */
export const ApproachingSharedBudget: Story = {
	args: {
		report: { ...withOwnProvider, instanceTotalCostUsd: 22 },
	},
};

/**
 * Day 2 of the month: the same warning without a projection. One busy afternoon is not a pace, so
 * the page says nothing rather than guessing.
 */
export const ApproachingWithoutProjection: Story = {
	args: {
		now: new Date("2026-07-02T12:00:00.000Z"),
		report: { ...withOwnProvider, ownProviderTotalCostUsd: 8.4 },
	},
};

/** The workspace spent its provider cap — the one pause its admin can lift themselves. */
export const ProviderCapReached: Story = {
	args: {
		report: {
			...withOwnProvider,
			ownProviderTotalCostUsd: 10.12,
			ownProviderBudgetVerdict: "EXHAUSTED",
			ownProviderPaused: true,
		},
	},
};

/** Calls on the workspace's own models with no price set: the cap can't be checked, so work stops. */
export const ProviderCapUnenforceable: Story = {
	args: {
		report: {
			...withOwnProvider,
			ownProviderBudgetVerdict: "UNVERIFIABLE",
			ownProviderPaused: true,
			unpricedEventCount: 7,
		},
	},
};

/** The shared-model budget is spent. A warning, not a catastrophe — their provider runs on. */
export const SharedBudgetReached: Story = {
	args: {
		report: {
			...withOwnProvider,
			instanceTotalCostUsd: 25.0142,
			instanceBudgetVerdict: "EXHAUSTED",
			instancePaused: true,
		},
	},
};

/** Shared-model calls with no price set — only the host can fix this, and the copy says so. */
export const SharedBudgetUnverifiable: Story = {
	args: {
		report: {
			...withOwnProvider,
			instanceBudgetVerdict: "UNVERIFIABLE",
			instancePaused: true,
			unpricedEventCount: 7,
		},
	},
};

/** Both caps spent: the actionable one leads. */
export const BothPaused: Story = {
	args: {
		report: {
			...withOwnProvider,
			instanceTotalCostUsd: 25.0142,
			ownProviderTotalCostUsd: 10.12,
			instanceBudgetVerdict: "EXHAUSTED",
			instancePaused: true,
			ownProviderBudgetVerdict: "EXHAUSTED",
			ownProviderPaused: true,
		},
	},
};

/** No provider of their own — a quiet offer rather than an empty meter. */
export const NoProviderConnected: Story = {
	args: { report: baseReport },
};

/** Provider spend with no cap on it yet: the meter is absent, the offer to cap it is not. */
export const ProviderUncapped: Story = {
	args: {
		report: { ...withOwnProvider, ownProviderMonthlyBudgetUsd: undefined },
	},
};

/** A $0 provider cap pauses immediately — it reads 100% used, not "—". */
export const ZeroProviderCap: Story = {
	args: {
		report: {
			...withOwnProvider,
			ownProviderMonthlyBudgetUsd: 0,
			ownProviderTotalCostUsd: 0,
			ownProviderBudgetVerdict: "EXHAUSTED",
			ownProviderPaused: true,
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
			instanceTotalCostUsd: 25.0142,
			instanceBudgetVerdict: "EXHAUSTED",
		},
	},
};

/** No usage recorded in the selected month. */
export const Empty: Story = {
	args: {
		report: {
			...baseReport,
			instanceTotalCostUsd: 0,
			ownProviderTotalCostUsd: 0,
			byJobType: [],
			byDay: [],
		},
	},
};

/**
 * Some runs used a model with no price set, so both reported totals — and the caps that read
 * them — under-count. A secondary callout explains the gap and who can close it.
 */
export const CallsWithNoPriceSet: Story = {
	args: {
		report: { ...withOwnProvider, unpricedEventCount: 42 },
	},
};

/** A single call with no price set — the callout reads "1 call", not "1 calls". */
export const SingleCallWithNoPriceSet: Story = {
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
		error: { status: 500, detail: "Couldn't build the usage report." },
	},
};

/** A 403 is not retryable, so the alert explains the block without offering a Retry. */
export const ForbiddenError: Story = {
	args: {
		report: undefined,
		error: { status: 403, detail: "Workspace admin access is required." },
	},
};

/**
 * The whole page at the WCAG 2.2 SC 1.4.10 reflow width (320 CSS px ≙ 1280 px at 400 % zoom).
 *
 * The page must reflow to a single column with no horizontal scrolling of its own. The two
 * breakdown tables are the documented data-table exception: they may scroll sideways, but only
 * inside their own container — which is what `expectTablesScrollInPlace` pins down.
 */
export const MobileReflow: Story = {
	// The densest state the page has: two pause banners, both cap cards, and both breakdown tables.
	args: {
		report: {
			...withOwnProvider,
			instanceTotalCostUsd: 25.0142,
			ownProviderTotalCostUsd: 10.12,
			instanceBudgetVerdict: "EXHAUSTED",
			instancePaused: true,
			ownProviderBudgetVerdict: "EXHAUSTED",
			ownProviderPaused: true,
		},
	},
	parameters: {
		layout: "fullscreen",
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 768, 1024] },
	},
	play: async ({ canvasElement }) => {
		await expectPageReflows();
		await expectTablesScrollInPlace(canvasElement);
	},
};
