import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import type { WorkspaceLlmUsageReport } from "@/api/types.gen";
import {
	AdminInstanceLlmUsageTable,
	type AdminWorkspaceLlmUsageRow,
} from "./AdminInstanceLlmUsageTable";

/**
 * Mixed cap ownership, in the container's sort order (shared-model spend desc): both caps set,
 * provider cap only, instance cap only, and neither.
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
		// Provider cap only, and spent: this workspace pauses itself without the instance admin ever
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
 * Instance-admin table of every workspace's AI spend for one month, against both caps: the
 * instance cap the host pays for (editable here, via `onEditBudget`) and the workspace's own
 * provider cap (read-only — it is the workspace's money). Pure/presentational.
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
 * Current month with every cap combination: both caps, instance only, provider only, neither —
 * plus a paused instance cap, a paused provider cap, a near-cap warning, and a total that can't be
 * verified.
 */
export const Default: Story = {
	// Also the default currency state: no display currency configured, so every figure is USD.
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByText(/ECB reference rate/)).toBeNull();
	},
};

/** One workspace expanded to show the existing by-job and daily usage rollups. */
export const Expanded: Story = {
	args: {
		expandedWorkspaceId: rows[0].workspaceId,
		detailReport,
	},
};

/**
 * The same month on an instance that displays EUR. One month resolves to exactly one rate, so
 * every row carries the same block and the estimate sits under the shared-model spend — the column
 * the instance admin is accountable for — where a second line costs no width.
 */
export const DisplayCurrencyThisMonth: Story = {
	args: {
		rows: rows.map((row) => ({
			...row,
			fx: {
				currencyCode: "EUR",
				ratePerUsd: 0.878966,
				rateDate: new Date("2026-07-24T00:00:00.000Z"),
			},
		})),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(/EUR amounts are estimates at the ECB reference rate for Jul 24, 2026/),
		).toBeVisible();
		await expect(canvas.getByLabelText("approximately 21.99 euros")).toBeInTheDocument();
	},
};

/** A closed month on a EUR instance: the rate is dated inside it, so the figures never move. */
export const DisplayCurrencyClosedMonth: Story = {
	args: {
		isCurrentMonth: false,
		rows: rows.map((row) => ({
			...row,
			fx: {
				currencyCode: "EUR",
				ratePerUsd: 0.874312,
				rateDate: new Date("2026-06-30T00:00:00.000Z"),
			},
		})),
	},
	play: async ({ canvasElement }) => {
		await expect(
			within(canvasElement).getByText(
				/The last rate published that month, so past figures don't change/,
			),
		).toBeVisible();
	},
};

/**
 * The expanded breakdown at the WCAG 2.2 SC 1.4.10 reflow width (320 px).
 *
 * The eight-column rollup is the documented data-table exception: it may scroll horizontally inside
 * its own container. The breakdown that opens underneath it may not inherit that — nested in a
 * `colSpan` row it took the table's ~1100 px width and opened a second horizontal scroller inside
 * the first, which is two-dimensional scrolling to read a number. This asserts the panel is a
 * sibling of the scroll container rather than a descendant, and that it fits the page width.
 */
export const ExpandedMobileReflow: Story = {
	args: {
		expandedWorkspaceId: rows[0].workspaceId,
		detailReport,
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 1024] },
	},
	play: async ({ canvasElement }) => {
		const scroller = canvasElement.querySelector<HTMLElement>('[data-slot="table-container"]');
		const panel = canvasElement.querySelector<HTMLElement>(
			`#workspace-usage-details-${rows[0].workspaceId}`,
		);
		await expect(scroller).not.toBeNull();
		await expect(panel).not.toBeNull();
		if (scroller == null || panel == null) return;

		// The defect in one assertion: the breakdown must not live inside the table's scroller.
		await expect(scroller.contains(panel)).toBe(false);
		// And it reflows to the page rather than to the table's intrinsic width.
		await expect(panel.scrollWidth).toBeLessThanOrEqual(canvasElement.clientWidth + 1);

		// The toggle still owns the panel for assistive tech even though they are no longer adjacent.
		const toggle = await within(canvasElement).findByRole("button", {
			name: /hide usage details for Example Workspace/i,
		});
		await expect(toggle).toHaveAttribute(
			"aria-controls",
			`workspace-usage-details-${rows[0].workspaceId}`,
		);
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
export const PausedByProviderCap: Story = {
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
 * Workspaces that have not set a cap of their own: the provider-cap column reads as unset, which is
 * the signal that nobody is bounding that workspace's provider spend.
 */
export const NoProviderCapsSet: Story = {
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
		error: { status: 500, detail: "Couldn't roll up AI usage." },
	},
};

/** A 403 is not retryable, so the alert explains the block without offering a Retry. */
export const ForbiddenError: Story = {
	args: {
		rows: [],
		error: { status: 403, detail: "Instance admin access is required." },
	},
};
