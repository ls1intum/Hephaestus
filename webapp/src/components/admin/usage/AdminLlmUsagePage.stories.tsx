import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import { AdminLlmUsagePage } from "./AdminLlmUsagePage";

const baseReport: WorkspaceLlmUsageReport = {
	month: "2026-07",
	monthlyBudgetUsd: 25,
	totalCostUsd: 13.4821,
	overBudget: false,
	uncostedEvents: 0,
	byJobType: [
		{
			jobType: "PULL_REQUEST_REVIEW",
			costUsd: 8.1034,
			inputTokens: 1_204_331,
			outputTokens: 88_412,
			cacheReadTokens: 640_112,
			cacheWriteTokens: 120_034,
			totalCalls: 312,
			events: 41,
		},
		{
			jobType: "MENTOR_TURN",
			costUsd: 3.9902,
			inputTokens: 402_118,
			outputTokens: 61_240,
			cacheReadTokens: 210_400,
			cacheWriteTokens: 44_020,
			totalCalls: 128,
			events: 64,
		},
		{
			jobType: "ISSUE_REVIEW",
			costUsd: 1.3885,
			inputTokens: 150_221,
			outputTokens: 20_114,
			cacheReadTokens: 80_010,
			cacheWriteTokens: 12_450,
			totalCalls: 54,
			events: 12,
		},
	],
	byDay: [
		{ day: new Date("2026-07-01"), costUsd: 2.1, events: 14 },
		{ day: new Date("2026-07-02"), costUsd: 4.83, events: 31 },
		{ day: new Date("2026-07-03"), costUsd: 0.92, events: 6 },
		{ day: new Date("2026-07-06"), costUsd: 5.6321, events: 66 },
	],
};

/**
 * Workspace-admin view of one month of LLM spend: stat cards, over-budget banner,
 * by-job-type and by-day breakdowns. Pure/presentational.
 */
const meta = {
	component: AdminLlmUsagePage,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	args: {
		month: "2026-07",
		isCurrentMonth: true,
		report: baseReport,
		isLoading: false,
		error: null,
		onRetry: fn(),
		onPrevMonth: fn(),
		onNextMonth: fn(),
	},
} satisfies Meta<typeof AdminLlmUsagePage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Current month with a cap set and spend well under it. */
export const Default: Story = {};

/** Spend reached the cap in the current month — the destructive pause banner shows. */
export const OverBudget: Story = {
	args: {
		report: {
			...baseReport,
			totalCostUsd: 25.0142,
			overBudget: true,
		},
	},
};

/** A past month that ended over budget — no banner, since only the current month is paused. */
export const OverBudgetPastMonth: Story = {
	args: {
		month: "2026-06",
		isCurrentMonth: false,
		report: {
			...baseReport,
			month: "2026-06",
			totalCostUsd: 25.0142,
			overBudget: true,
		},
	},
};

/** A $0 cap pauses the workspace immediately — budget used reads 100%, not "—". */
export const ZeroCap: Story = {
	args: {
		report: {
			...baseReport,
			monthlyBudgetUsd: 0,
			totalCostUsd: 0,
			overBudget: true,
			byJobType: [],
			byDay: [],
		},
	},
};

/** No cap configured — spend is shown but budget progress is not applicable. */
export const NoCap: Story = {
	args: {
		report: {
			...baseReport,
			monthlyBudgetUsd: undefined,
		},
	},
};

/** No usage recorded in the selected month. */
export const Empty: Story = {
	args: {
		report: {
			month: "2026-07",
			monthlyBudgetUsd: 25,
			totalCostUsd: 0,
			overBudget: false,
			uncostedEvents: 0,
			byJobType: [],
			byDay: [],
		},
	},
};

/**
 * Some calls ran on a model with no pricing row, so the reported spend — and the cap that reads
 * it — under-count. A secondary warning callout explains the data-quality gap.
 */
export const UncostedUsage: Story = {
	args: {
		report: {
			...baseReport,
			uncostedEvents: 42,
		},
	},
};

/** A single uncosted call — the callout reads "1 call", not "1 calls". */
export const SingleUncostedCall: Story = {
	args: {
		report: {
			...baseReport,
			uncostedEvents: 1,
		},
	},
};

/**
 * A past month with uncosted calls — the callout still shows (it is a fact about that month's
 * data), even though the over-budget pause banner does not.
 */
export const UncostedUsagePastMonth: Story = {
	args: {
		month: "2026-06",
		isCurrentMonth: false,
		report: {
			...baseReport,
			month: "2026-06",
			uncostedEvents: 42,
		},
	},
};

/** Both notices at once — the destructive pause banner stays above the warning callout. */
export const OverBudgetWithUncostedUsage: Story = {
	args: {
		report: {
			...baseReport,
			totalCostUsd: 25.0142,
			overBudget: true,
			uncostedEvents: 42,
		},
	},
};

/** Report still loading — the stat cards and the by-job-type table shell are skeletoned in place. */
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
