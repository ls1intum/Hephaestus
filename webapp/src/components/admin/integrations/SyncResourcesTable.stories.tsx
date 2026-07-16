import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SyncResourceState } from "@/api/types.gen";
import { SyncResourcesTable } from "./SyncResourcesTable";

/** The six entity classes an SCM repository mirrors — the breakdown a repository row expands to reveal. */
function scmCounts(syncedAt: Date | undefined, scale = 1): SyncResourceState["counts"] {
	const at = syncedAt;
	return [
		{ key: "pullRequests", label: "Pull requests", count: 1204 * scale, lastSyncedAt: at },
		{ key: "issues", label: "Issues", count: 3410 * scale, lastSyncedAt: at },
		{ key: "issueComments", label: "Issue comments", count: 12882 * scale, lastSyncedAt: at },
		{ key: "reviews", label: "Reviews", count: 4120 * scale, lastSyncedAt: at },
		// A per-class watermark absence: reviews synced, review comments never tracked for this repo.
		{
			key: "reviewComments",
			label: "Review comments",
			count: 9004 * scale,
			lastSyncedAt: undefined,
		},
		{ key: "commits", label: "Commits", count: 28710 * scale, lastSyncedAt: at },
	];
}

const resources: SyncResourceState[] = [
	{
		id: 1,
		externalId: "ls1intum/Artemis",
		name: "ls1intum/Artemis",
		type: "REPOSITORY",
		counts: scmCounts(new Date("2026-07-14T09:00:00Z"), 3),
		state: "SYNCED",
		lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
		itemCount: 21_300,
		backfillCompletedThrough: new Date("2021-01-04T00:00:00Z"),
	},
	{
		id: 2,
		externalId: "ls1intum/Athena",
		name: "ls1intum/Athena",
		type: "REPOSITORY",
		counts: scmCounts(new Date("2026-07-14T09:00:00Z")),
		state: "SYNCED",
		lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
		itemCount: 4614,
		backfillPercent: 20,
	},
	{
		// Never synced: empty breakdown, not a row of zeros.
		id: 3,
		externalId: "ls1intum/new-repo",
		name: "ls1intum/new-repo",
		type: "REPOSITORY",
		counts: [],
		state: "PENDING",
	},
	{
		id: 4,
		externalId: "ls1intum/broken-repo",
		name: "ls1intum/broken-repo",
		type: "REPOSITORY",
		counts: scmCounts(new Date("2026-05-01T09:00:00Z")),
		state: "SYNCED",
		lastSyncedAt: new Date("2026-05-01T09:00:00Z"),
		itemCount: 88,
		lastError: "403: repository access revoked",
	},
];

/** Slack channels (and Outline collections) mirror ONE class — the row shows it inline, no chevron. */
const singleClassResources: SyncResourceState[] = [
	{
		id: 10,
		externalId: "C0123ABCD",
		name: "#engineering",
		type: "CHANNEL",
		counts: [
			{
				key: "messages",
				label: "Messages",
				count: 8421,
				lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
			},
		],
		state: "ACTIVE",
		lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
		itemCount: 8421,
	},
	{
		id: 11,
		externalId: "C0456EFGH",
		name: "#design",
		type: "CHANNEL",
		counts: [
			{
				key: "messages",
				label: "Messages",
				count: 213,
				lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
			},
		],
		state: "PAUSED",
		lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
		itemCount: 213,
	},
];

/**
 * One row per state the providers actually emit: SYNCED/PENDING from the two SCMs
 * (SyncResourceState.STATE_*), PENDING/ACTIVE/PAUSED/REVOKED from Slack's consent state, and
 * PENDING/COMPLETE/PAUSED from Outline's collections. Unknown wire strings are covered by
 * {@link UnknownState} rather than invented here.
 */
const ALL_STATES = ["PENDING", "SYNCED", "ACTIVE", "PAUSED", "REVOKED", "COMPLETE"] as const;

const allStates: SyncResourceState[] = ALL_STATES.map((state, index) => ({
	id: 100 + index,
	externalId: `octocat/repo-${state.toLowerCase()}`,
	name: `octocat/repo-${state.toLowerCase()}`,
	type: "REPOSITORY",
	counts: [],
	state,
	lastSyncedAt: state === "PENDING" ? undefined : new Date("2026-07-14T09:00:00Z"),
	itemCount: state === "PENDING" ? undefined : 20 + index,
	upstreamCount: state === "PENDING" ? undefined : 20 + index,
}));

const longNames: SyncResourceState[] = [
	{
		id: 200,
		externalId:
			"a-very-long-organisation-name/an-even-longer-repository-name-that-overflows-the-column",
		name: "a-very-long-organisation-name/an-even-longer-repository-name-that-overflows-the-column",
		type: "REPOSITORY",
		counts: [],
		state: "PENDING",
		lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
		lastError:
			"The upstream repository returned a 500 for three consecutive enumeration attempts; the last error body was 'internal server error while resolving the default branch tree', so the resource is parked until the next reconciliation.",
	},
];

/**
 * The per-resource sync plane: name/external id, a state badge (title-cased by `stateLabel`, so an
 * unknown wire string reads as words rather than a raw token), last-synced, an items count, an
 * optional backfill bar and an on-demand error popover. Container states — loading, error-with-retry
 * and empty — are covered, and the noun is configurable ("repository" / "collection" / "channel").
 */
const meta = {
	component: SyncResourcesTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { resourceNoun: "repository", resourceNounPlural: "repositories" },
} satisfies Meta<typeof SyncResourcesTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { resources } };

/**
 * The headline complaint this table answers: a repository is not "128 items" — it is pull requests,
 * issues, comments, reviews and commits, each with its own count and freshness. Expanding a row reveals
 * the per-class breakdown, which is the only place "PRs still sync but comments stopped" is visible.
 */
export const PerEntityBreakdown: Story = {
	args: { resources, syncIntervalSeconds: 21_600 },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The row's inline summary names the classes without expanding.
		await expect(canvas.getByText(/3,612 pull requests · 10,230 issues/i)).toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: /ls1intum\/Artemis/i }));
		await expect(await canvas.findByText("Pull requests")).toBeInTheDocument();
		await expect(canvas.getByText("Review comments")).toBeInTheDocument();
		// Reviews are watermarked, review comments are "not tracked" — different claims, both shown.
		await expect(canvas.getAllByText(/not tracked/i).length).toBeGreaterThan(0);
	},
};

/** Slack/Outline mirror a single class, so the row shows it inline with no disclosure chevron. */
export const SingleClass: Story = {
	args: {
		resources: singleClassResources,
		resourceNoun: "channel",
		resourceNounPlural: "channels",
	},
};

/** Freshness is judged against the cadence: a repo two-plus cadences stale is tinted, not just printed. */
export const StaleFreshness: Story = { args: { resources, syncIntervalSeconds: 3_600 } };

/** Every labelled state rendered at once, so a badge regression is visible at a glance. */
export const AllStates: Story = { args: { resources: allStates } };

/**
 * `state` is a free-form string on the wire, so a vendor can send one the UI has never heard of.
 * It is title-cased for display rather than shown as a raw SCREAMING_SNAKE token.
 */
export const UnknownState: Story = {
	args: {
		resources: [
			{
				id: 300,
				externalId: "octocat/rate-limited-repo",
				name: "octocat/rate-limited-repo",
				type: "REPOSITORY",
				counts: [],
				state: "RATE_LIMITED" as SyncResourceState["state"],
				lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
				itemCount: 12,
			},
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Rate Limited")).toBeInTheDocument();
		await expect(canvas.queryByText("RATE_LIMITED")).not.toBeInTheDocument();
	},
};

/** The error popover surfaces the resource's last error, keyed to the failing row. */
export const ErrorPopover: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /error for ls1intum\/broken-repo/i }));
		await expect(await screen.findByText(/repository access revoked/i)).toBeInTheDocument();
	},
};

/** Long org/repo names and a long error must truncate/wrap without breaking the layout. */
export const LongNames: Story = { args: { resources: longNames } };

export const Loading: Story = { args: { resources: [], isLoading: true } };
export const ErrorState: Story = {
	args: { resources: [], isError: true, error: new Error("Network error"), onRetry: fn() },
};
export const Empty: Story = { args: { resources: [] } };
