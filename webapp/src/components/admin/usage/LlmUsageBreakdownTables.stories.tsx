import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, within } from "storybook/test";
import type {
	FxRateInfo,
	LlmUsageByDay,
	LlmUsageByJobType,
	WorkspaceLlmUsageReport,
} from "@/api/types.gen";
import { LlmUsageByDayTable, LlmUsageByJobTypeTable } from "./LlmUsageBreakdownTables";

const eur: FxRateInfo = {
	currencyCode: "EUR",
	ratePerUsd: 0.88,
	rateDate: "2026-07-24" as unknown as Date,
	source: "ECB",
};

const jobTypeRows: LlmUsageByJobType[] = [
	{
		jobType: "PULL_REQUEST_REVIEW",
		events: 128,
		inputTokens: 4_210_000,
		outputTokens: 318_000,
		cacheReadTokens: 1_900_000,
		cacheWriteTokens: 210_000,
		totalCalls: 402,
		instanceTotalCostUsd: 12.4,
		ownProviderTotalCostUsd: 0,
		unpricedEventCount: 0,
	},
	{
		jobType: "MENTOR_TURN",
		events: 61,
		inputTokens: 890_000,
		outputTokens: 141_000,
		cacheReadTokens: 0,
		cacheWriteTokens: 0,
		totalCalls: 61,
		instanceTotalCostUsd: 0,
		ownProviderTotalCostUsd: 3.15,
		unpricedEventCount: 4,
	},
];

const dayRows: LlmUsageByDay[] = [
	{
		day: "2026-07-20" as unknown as Date,
		events: 42,
		instanceTotalCostUsd: 4.1,
		ownProviderTotalCostUsd: 0.9,
		unpricedEventCount: 0,
	},
	{
		day: "2026-07-21" as unknown as Date,
		events: 77,
		instanceTotalCostUsd: 6.3,
		ownProviderTotalCostUsd: 1.25,
		unpricedEventCount: 2,
	},
	{
		day: "2026-07-22" as unknown as Date,
		events: 70,
		instanceTotalCostUsd: 2.0,
		ownProviderTotalCostUsd: 1.0,
		unpricedEventCount: 0,
	},
];

/**
 * A month's report as the server sends it. The tables read their footer money straight off these two
 * fields rather than re-adding the rows — the client does no money arithmetic — so the fixture keeps
 * them consistent with the rows the same way the server does.
 */
function report(overrides: Partial<WorkspaceLlmUsageReport> = {}): WorkspaceLlmUsageReport {
	const byJobType = overrides.byJobType ?? jobTypeRows;
	const byDay = overrides.byDay ?? dayRows;
	const rows = byJobType.length > 0 ? byJobType : byDay;
	return {
		month: "2026-07",
		byJobType,
		byDay,
		instanceTotalCostUsd: rows.reduce((total, row) => total + row.instanceTotalCostUsd, 0),
		ownProviderTotalCostUsd: rows.reduce((total, row) => total + row.ownProviderTotalCostUsd, 0),
		unpricedEventCount: rows.reduce((total, row) => total + row.unpricedEventCount, 0),
		instanceBudgetVerdict: "WITHIN",
		instancePaused: false,
		ownProviderBudgetVerdict: "WITHIN",
		ownProviderPaused: false,
		...overrides,
	};
}

/**
 * Where a month's AI spend went, broken down by the kind of work that caused it. Spend is split by
 * *who pays* — shared instance models against the workspace's own connected provider — because those
 * are two separate purses with two separate caps, and a single merged number could not be acted on.
 */
const meta = {
	component: LlmUsageByJobTypeTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { report: report(), fx: eur },
} satisfies Meta<typeof LlmUsageByJobTypeTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Two job types, so a totals footer is worth showing. */
export const ByJobType: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("PR review")).toBeVisible();
		await expect(await canvas.findByText("Mentor turn")).toBeVisible();
	},
};

/**
 * One row needs no footer: a "total" there would restate the line directly above it. The table
 * deliberately drops it rather than printing the same number twice.
 */
export const SingleRowHasNoTotals: Story = {
	args: { report: report({ byJobType: [jobTypeRows[0]] }) },
};

/** Loading — the table shell stays put so the page doesn't jump when the rows land. */
export const Loading: Story = {
	args: { report: undefined },
};

export const Empty: Story = {
	args: { report: report({ byJobType: [] }) },
};

/** No display currency configured: USD only, and no estimate line under any figure. */
export const UsdOnly: Story = {
	args: { fx: null },
};

/** The per-day view of the same month — the shape of the spend over time. */
export const ByDay: StoryObj<typeof LlmUsageByDayTable> = {
	render: (args) => <LlmUsageByDayTable {...args} />,
	args: { report: report(), fx: eur },
};

export const ByDayLoading: StoryObj<typeof LlmUsageByDayTable> = {
	render: (args) => <LlmUsageByDayTable {...args} />,
	args: { report: undefined, fx: eur },
};

export const ByDayEmpty: StoryObj<typeof LlmUsageByDayTable> = {
	render: (args) => <LlmUsageByDayTable {...args} />,
	args: { report: report({ byDay: [] }), fx: eur },
};
