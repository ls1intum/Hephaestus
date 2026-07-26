import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import type { AdminWorkspaceLlmUsage, WorkspaceLlmUsageReport } from "@/api/types.gen";
import { expectTargetSize } from "@/test/reflow";
import { AdminInstanceLlmUsageTable } from "./AdminInstanceLlmUsageTable";

/**
 * Mixed cap ownership, in the container's sort order (shared-model spend desc): both caps set,
 * provider cap only, shared-model budget only, and neither.
 */
const rows: AdminWorkspaceLlmUsage[] = [
	{
		// Both caps set; the shared-model budget is spent, so host-funded work is paused — the
		// workspace's own provider keeps running, because that cap is nowhere near.
		workspaceSlug: "example-workspace",
		displayName: "Example Workspace",
		instanceMonthlyBudgetUsd: 25,
		instanceTotalCostUsd: 25.0142,
		instanceBudgetVerdict: "EXHAUSTED",
		instancePaused: true,
		ownProviderMonthlyBudgetUsd: 40,
		ownProviderTotalCostUsd: 6.5,
		ownProviderBudgetVerdict: "WITHIN",
		ownProviderPaused: false,
		events: 118,
	},
	{
		// Both caps set; the workspace is closing in on its own cap (86%), which is its own admins'
		// problem to solve — the instance admin can see it but cannot raise it.
		workspaceSlug: "hephaestus-dev",
		displayName: "Hephaestus Dev",
		instanceMonthlyBudgetUsd: 100,
		instanceTotalCostUsd: 13.4821,
		instanceBudgetVerdict: "WITHIN",
		instancePaused: false,
		ownProviderMonthlyBudgetUsd: 25,
		ownProviderTotalCostUsd: 21.4,
		ownProviderBudgetVerdict: "WITHIN",
		ownProviderPaused: false,
		events: 74,
	},
	{
		// Shared-model budget only, three quarters spent, and some usage has no price on record — so
		// the meter is a floor, which the warning line says out loud.
		workspaceSlug: "launchpad",
		displayName: "Launchpad",
		instanceMonthlyBudgetUsd: 50,
		instanceTotalCostUsd: 38.2,
		instanceBudgetVerdict: "UNVERIFIABLE",
		instancePaused: false,
		ownProviderTotalCostUsd: 0,
		events: 22,
		ownProviderBudgetVerdict: "WITHIN" as const,
		ownProviderPaused: false,
	},
	{
		// Neither cap — nobody has taken responsibility for this workspace's spend yet.
		workspaceSlug: "sandbox",
		displayName: "Sandbox",
		instanceTotalCostUsd: 0.42,
		instanceBudgetVerdict: "WITHIN",
		ownProviderTotalCostUsd: 0,
		events: 3,
		ownProviderBudgetVerdict: "WITHIN" as const,
		ownProviderPaused: false,
		instancePaused: false,
	},
	{
		// Provider cap only, and spent: this workspace pauses itself without the instance admin ever
		// setting a cap — the case that decides whether they bother setting one at all.
		workspaceSlug: "atelier",
		displayName: "Atelier",
		instanceTotalCostUsd: 0,
		instanceBudgetVerdict: "WITHIN",
		ownProviderMonthlyBudgetUsd: 12,
		ownProviderTotalCostUsd: 12.4,
		ownProviderBudgetVerdict: "EXHAUSTED",
		ownProviderPaused: true,
		events: 40,
		instancePaused: false,
	},
];

const detailReport: WorkspaceLlmUsageReport = {
	month: "2026-07",
	instanceMonthlyBudgetUsd: 25,
	instanceTotalCostUsd: 25.0142,
	instanceBudgetVerdict: "EXHAUSTED",
	instancePaused: true,
	ownProviderMonthlyBudgetUsd: 40,
	ownProviderTotalCostUsd: 1.5,
	ownProviderBudgetVerdict: "WITHIN",
	ownProviderPaused: false,
	unpricedEventCount: 0,
	byJobType: [
		{
			jobType: "PULL_REQUEST_REVIEW",
			instanceTotalCostUsd: 25.0142,
			ownProviderTotalCostUsd: 1.5,
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
			instanceTotalCostUsd: 25.0142,
			ownProviderTotalCostUsd: 1.5,
			unpricedEventCount: 0,
			events: 18,
		},
	],
};

/**
 * Instance-admin table of every workspace's AI spend for one month, against both caps: the
 * shared-model budget the host grants (editable here, via `onEditBudget`) and the workspace's own
 * provider cap (read-only — it is the workspace's money). Pure/presentational.
 */
const meta = {
	component: AdminInstanceLlmUsageTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		rows,
		month: "2026-07",
		// Pinned so the burn-rate projections in the expanded panel are the same in every snapshot.
		now: new Date("2026-07-10T12:00:00.000Z"),
		isCurrentMonth: true,
		isLoading: false,
		error: null,
		onRetry: fn(),
		fx: undefined,
		expandedWorkspaceSlug: null,
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
 * Current month with every cap combination: both caps, shared-model budget only, provider only,
 * neither — plus a paused budget, a paused provider cap, a near-cap warning, and a total that
 * can't be verified.
 */
export const Default: Story = {
	// Also the default currency state: no display currency configured, so every figure is USD.
	play: async ({ canvasElement }) => {
		await expect(within(canvasElement).queryByText(/reference rate published on/)).toBeNull();
	},
};

/** One workspace expanded to show the existing by-run-type and daily usage rollups. */
export const Expanded: Story = {
	args: {
		expandedWorkspaceSlug: rows[0].workspaceSlug,
		detailReport,
	},
};

/**
 * The projection the rollup row can't carry. A budget at 84% is only alarming once you know this
 * month's pace reaches it, which is exactly what the workspace's own console has always said and
 * this one used to stop short of.
 */
export const ExpandedNearCap: Story = {
	args: {
		expandedWorkspaceSlug: rows[1].workspaceSlug,
		detailReport: {
			...detailReport,
			instanceMonthlyBudgetUsd: 50,
			instanceTotalCostUsd: 42,
			instanceBudgetVerdict: "WITHIN",
			instancePaused: false,
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText("Hephaestus Dev has used 84% of its shared-model budget"),
		).toBeVisible();
		await expect(canvas.getByText(/At this pace you'll hit it around July 12\./)).toBeVisible();
	},
};

/**
 * The same month on an instance that displays EUR. One month resolves to exactly one rate, which
 * arrives once with the report rather than on every row, and the estimate sits under each spend
 * figure — a second line costs no column width.
 */
export const DisplayCurrencyThisMonth: Story = {
	args: {
		fx: {
			currencyCode: "EUR",
			ratePerUsd: 0.878966,
			rateDate: new Date("2026-07-24T00:00:00.000Z"),
			source: "ECB",
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(
				/EUR amounts are estimates at the European Central Bank reference rate published on Jul 24, 2026/,
			),
		).toBeVisible();
		await expect(canvas.getByLabelText("approximately 21.99 euros")).toBeInTheDocument();
	},
};

/** A closed month on a EUR instance: the rate is dated inside it, so the figures never move. */
export const DisplayCurrencyClosedMonth: Story = {
	args: {
		isCurrentMonth: false,
		fx: {
			currencyCode: "EUR",
			ratePerUsd: 0.874312,
			rateDate: new Date("2026-06-30T00:00:00.000Z"),
			source: "ECB",
		},
	},
	play: async ({ canvasElement }) => {
		await expect(
			within(canvasElement).getByText(
				/last rate published in the month shown, so these figures no longer change/,
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
		expandedWorkspaceSlug: rows[0].workspaceSlug,
		detailReport,
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320, 375, 1024] },
	},
	play: async ({ canvasElement }) => {
		const scroller = canvasElement.querySelector<HTMLElement>('[data-slot="table-container"]');
		const panel = canvasElement.querySelector<HTMLElement>(
			`#workspace-usage-details-${rows[0].workspaceSlug}`,
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
			`workspace-usage-details-${rows[0].workspaceSlug}`,
		);
	},
};

/**
 * The two "whose money is this" column headers are the only interactive targets in the header row,
 * and they sit at the header's ~20 px line height.
 *
 * WCAG 2.2 SC 2.5.8 wants 24 x 24 px. Conforming through the Spacing exception instead would rest
 * on how far apart these two columns happen to render, which nothing here controls — so they carry
 * their own height, and this measures it.
 */
export const HelpHeaderTargetSize: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		for (const name of ["Shared-model budget", "Provider cap"]) {
			const header = canvas.getByRole("columnheader", { name });
			const trigger = header.querySelector<HTMLElement>("button");
			await expect(trigger).not.toBeNull();
			if (trigger != null) await expectTargetSize(trigger);
		}
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
				workspaceSlug: "close-call",
				displayName: "Close Call",
				instanceMonthlyBudgetUsd: 50,
				instanceTotalCostUsd: 41,
				instanceBudgetVerdict: "WITHIN",
				instancePaused: false,
				ownProviderMonthlyBudgetUsd: 30,
				ownProviderTotalCostUsd: 27.6,
				ownProviderBudgetVerdict: "WITHIN",
				ownProviderPaused: false,
				events: 210,
			},
		],
	},
};

/** Paused on the host's money — the one cap this admin can actually raise. */
export const PausedByInstanceCap: Story = {
	args: { rows: [rows[0]] },
};

/** Paused on the workspace's own money — raising the shared-model budget would change nothing. */
export const PausedByProviderCap: Story = {
	args: { rows: [rows[4]] },
};

/** Both caps spent: two badges, because raising only one would leave the workspace stopped. */
export const PausedByBothCaps: Story = {
	args: {
		rows: [
			{
				workspaceSlug: "full-stop",
				displayName: "Full Stop",
				instanceMonthlyBudgetUsd: 20,
				instanceTotalCostUsd: 20.5,
				instanceBudgetVerdict: "EXHAUSTED",
				instancePaused: true,
				ownProviderMonthlyBudgetUsd: 15,
				ownProviderTotalCostUsd: 15,
				ownProviderBudgetVerdict: "EXHAUSTED",
				ownProviderPaused: true,
				events: 96,
			},
		],
	},
};

/**
 * A $0 shared-model budget is a supported state — it reads as 100% used and pauses immediately,
 * rather than as "no budget".
 */
export const ZeroInstanceCap: Story = {
	args: {
		rows: [
			{
				workspaceSlug: "frozen",
				displayName: "Frozen",
				instanceMonthlyBudgetUsd: 0,
				instanceTotalCostUsd: 0,
				instanceBudgetVerdict: "EXHAUSTED",
				instancePaused: true,
				ownProviderTotalCostUsd: 0,
				ownProviderBudgetVerdict: "WITHIN",
				ownProviderPaused: false,
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
			ownProviderMonthlyBudgetUsd: undefined,
			ownProviderBudgetVerdict: "WITHIN" as const,
			ownProviderPaused: false,
		})),
	},
};

/**
 * A past month. The verdicts are computed from the workspace's *current* caps, so they can't say
 * anything about a finished month — every status reads as a neutral dash, and the budget editor is
 * withdrawn along with them: a budget is not month-scoped, so saving one from here would change what
 * runs today.
 */
export const PastMonth: Story = {
	args: { isCurrentMonth: false },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button", { name: /^Set shared-model budget/ })).toBeNull();
		// Every row is otherwise unchanged — the month is still fully readable.
		await expect(canvas.getAllByRole("button", { name: /^View usage details for/ })).toHaveLength(
			rows.length,
		);
	},
};

/** The rollup left-joins from workspace, so zero rows means the instance has no workspaces. */
export const Empty: Story = {
	args: { rows: [] },
};

/**
 * A EUR instance whose month has no workspaces in it. The rate belongs to the month, so it is still
 * known here — it comes from the report envelope rather than from `rows[0]`, which this month does
 * not have. Nothing on screen converted, so nothing is disclosed.
 */
export const EmptyOnDisplayCurrencyInstance: Story = {
	args: {
		rows: [],
		fx: {
			currencyCode: "EUR",
			ratePerUsd: 0.878966,
			rateDate: new Date("2026-07-24T00:00:00.000Z"),
			source: "ECB",
		},
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("No workspaces on this instance yet")).toBeVisible();
		await expect(canvas.queryByText(/reference rate published on/)).toBeNull();
	},
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
