import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SyncResourceState } from "@/api/types.gen";
import { SCM_CLASS_KEYS, SyncResourcesTable } from "./SyncResourcesTable";

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);

/** Hourly reconciliation: 2x = stale, 6x = very stale. Every fixture below is timed against it. */
const SYNC_INTERVAL_SECONDS = 3_600;

/**
 * The six entity classes an SCM repository mirrors, folded by the table into five columns.
 *
 * `reviewComments` deliberately carries no watermark: the integration keeps none for it, which is
 * *not* the same claim as "never synced" and is the distinction the Comments column has to preserve
 * while merging.
 */
function scmCounts(syncedAt: Date | undefined, scale = 1): SyncResourceState["counts"] {
	return [
		{ key: "pullRequests", label: "Pull requests", count: 1204 * scale, lastSyncedAt: syncedAt },
		{ key: "issues", label: "Issues", count: 3410 * scale, lastSyncedAt: syncedAt },
		{ key: "issueComments", label: "Issue comments", count: 12882 * scale, lastSyncedAt: syncedAt },
		{ key: "reviews", label: "Reviews", count: 4120 * scale, lastSyncedAt: syncedAt },
		{
			key: "reviewComments",
			label: "Review comments",
			count: 9004 * scale,
			lastSyncedAt: undefined,
		},
		{ key: "commits", label: "Commits", count: 28710 * scale, lastSyncedAt: syncedAt },
	];
}

const resources: SyncResourceState[] = [
	{
		id: 1,
		externalId: "ls1intum/Artemis",
		name: "ls1intum/Artemis",
		type: "REPOSITORY",
		counts: scmCounts(minutesAgo(4), 3),
		state: "SYNCED",
		lastSyncedAt: minutesAgo(4),
		itemCount: 21_300,
		upstreamCount: 21_480,
		backfillCompletedThrough: new Date("2021-01-04T00:00:00Z"),
	},
	{
		// The failure this whole matrix exists to catch: five classes current, comments stopped days
		// ago. The old table showed one row-level "4 minutes ago" and hid this behind a chevron.
		id: 2,
		externalId: "ls1intum/Athena",
		name: "ls1intum/Athena",
		type: "REPOSITORY",
		counts: [
			{ key: "pullRequests", label: "Pull requests", count: 1204, lastSyncedAt: minutesAgo(6) },
			{ key: "issues", label: "Issues", count: 3410, lastSyncedAt: minutesAgo(6) },
			{
				key: "issueComments",
				label: "Issue comments",
				count: 12882,
				lastSyncedAt: minutesAgo(4600),
			},
			{ key: "reviews", label: "Reviews", count: 4120, lastSyncedAt: minutesAgo(6) },
			{ key: "reviewComments", label: "Review comments", count: 9004, lastSyncedAt: undefined },
			{ key: "commits", label: "Commits", count: 28710, lastSyncedAt: minutesAgo(6) },
		],
		state: "SYNCED",
		lastSyncedAt: minutesAgo(6),
		itemCount: 4614,
	},
	{
		// A backfill mid-flight — the bar lives under the name, where the eye already is.
		id: 3,
		externalId: "ls1intum/Aeolus",
		name: "ls1intum/Aeolus",
		type: "REPOSITORY",
		counts: scmCounts(minutesAgo(150)),
		state: "SYNCED",
		lastSyncedAt: minutesAgo(150),
		itemCount: 8_902,
		backfillPercent: 62,
	},
	{
		// Never synced: empty breakdown, not a row of zeros. Every class cell is a dash.
		id: 4,
		externalId: "ls1intum/new-repo",
		name: "ls1intum/new-repo",
		type: "REPOSITORY",
		counts: [],
		state: "PENDING",
	},
	{
		id: 5,
		externalId: "hephaestustest/broken-repo",
		name: "hephaestustest/broken-repo",
		type: "REPOSITORY",
		counts: scmCounts(minutesAgo(60 * 24 * 11)),
		state: "ERROR",
		lastSyncedAt: minutesAgo(60 * 24 * 11),
		itemCount: 88,
		lastError: "403: repository access revoked",
	},
];

/** Slack channels (and Outline collections) mirror ONE class, so the matrix is one column wide. */
const channels: SyncResourceState[] = [
	{
		id: 10,
		externalId: "C0123ABCD",
		name: "#engineering",
		type: "CHANNEL",
		counts: [{ key: "messages", label: "Messages", count: 8421, lastSyncedAt: minutesAgo(5) }],
		state: "ACTIVE",
		lastSyncedAt: minutesAgo(5),
		itemCount: 8421,
	},
	{
		id: 11,
		externalId: "C0456EFGH",
		name: "#design",
		type: "CHANNEL",
		counts: [{ key: "messages", label: "Messages", count: 213, lastSyncedAt: minutesAgo(400) }],
		state: "PAUSED",
		lastSyncedAt: minutesAgo(400),
		itemCount: 213,
	},
];

const longNames: SyncResourceState[] = [
	{
		id: 200,
		externalId:
			"a-very-long-organisation-name/an-even-longer-repository-name-that-overflows-the-column",
		name: "a-very-long-organisation-name/an-even-longer-repository-name-that-overflows-the-column",
		type: "REPOSITORY",
		counts: scmCounts(minutesAgo(30)),
		state: "SYNCED",
		lastSyncedAt: minutesAgo(30),
		itemCount: 1,
		lastError:
			"The upstream repository returned a 500 for three consecutive enumeration attempts; the last error body was 'internal server error while resolving the default branch tree', so the resource is parked until the next reconciliation.",
	},
];

/**
 * Per-resource, per-class freshness — which repositories are current, and which *classes* inside them
 * have quietly stopped.
 *
 * A repository is not "21,300 items, synced 4 minutes ago". It is issues, PRs, reviews, comments and
 * commits, each with its own watermark, and the failure worth catching is the one where five are
 * current and the sixth died a week ago. That fact used to live behind a per-row disclosure — never
 * comparable across repositories — while the columns it should have occupied went to a `State` badge
 * that repeated the timestamps and a `Synced through` that meant a percentage in some rows and a date
 * in others.
 *
 * Every cell is tinted only because the route passes `syncIntervalSeconds`: an age is not stale except
 * against a schedule, and the server sends null rather than invent one for an irregular cron.
 */
const meta = {
	component: SyncResourcesTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		resourceNoun: "repository",
		resourceNounPlural: "repositories",
		syncIntervalSeconds: SYNC_INTERVAL_SECONDS,
		expectedClassKeys: SCM_CLASS_KEYS,
	},
} satisfies Meta<typeof SyncResourcesTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { resources } };

/**
 * The headline claim: one column per class, so "PRs still sync but comments stopped" is visible across
 * the whole fleet at once, without a click. Athena's Comments cell is days stale while its PRs are
 * minutes fresh — two facts that the old row-level "last synced" collapsed into one reassuring lie.
 */
export const PerClassMatrix: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// A column per class the data actually reports — the two comment classes merged into one.
		await expect(canvas.getByRole("columnheader", { name: "Issues" })).toBeInTheDocument();
		await expect(canvas.getByRole("columnheader", { name: "PRs" })).toBeInTheDocument();
		await expect(canvas.getByRole("columnheader", { name: "Comments" })).toBeInTheDocument();
		await expect(canvas.getByRole("columnheader", { name: "Commits" })).toBeInTheDocument();
		await expect(canvas.queryByRole("columnheader", { name: /issue comments/i })).toBeNull();
		// And the State column is gone: a fresh timestamp already says SYNCED.
		await expect(canvas.queryByRole("columnheader", { name: "State" })).toBeNull();
		await expect(canvas.queryByRole("columnheader", { name: /synced through/i })).toBeNull();
	},
};

/**
 * Freshness tinting, which only works because the cadence is passed. Athena's comments are ~3 days
 * behind an hourly schedule, so the cell is destructive while its neighbours stay muted.
 */
export const FreshnessTinting: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const tinted = canvasElement.querySelectorAll(".text-destructive, .text-warning");
		await expect(tinted.length).toBeGreaterThan(0);
		await expect(canvas.getAllByText(/days ago$/).length).toBeGreaterThan(0);
	},
};

/** Without a cadence nothing is judged: every reading is printed muted, and none is called stale. */
export const NoCadence: Story = {
	// The errored row is excluded deliberately: its icon is destructive for a reason that has nothing
	// to do with freshness, and this story is about what freshness alone is allowed to colour.
	args: { resources: resources.filter((r) => r.lastError == null), syncIntervalSeconds: undefined },
	play: async ({ canvasElement }) => {
		await expect(canvasElement.querySelectorAll(".text-warning").length).toBe(0);
		await expect(canvasElement.querySelectorAll(".text-destructive").length).toBe(0);
	},
};

/**
 * The merged Comments column reports the OLDER of its two watermarks — the only merge that cannot hide
 * a stalled half — and the hover spells both out, including the one the integration doesn't track.
 */
export const CommentsHoverSpellsBothOut: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The name renders twice per row (display name over external id), hence getAllByText.
		const row = canvas.getAllByText("ls1intum/Artemis")[0].closest("tr");
		await expect(row).not.toBeNull();
		// The Comments cell is the one carrying both classes; hovering it names them individually.
		const cells = within(row as HTMLElement).getAllByText(/ago$|^—$/);
		await userEvent.hover(cells[3]);
		await expect(await screen.findByText(/issue comments/i)).toBeInTheDocument();
		await expect(await screen.findByText(/review comments/i)).toBeInTheDocument();
		await expect(await screen.findByText(/not tracked/i)).toBeInTheDocument();
	},
};

/**
 * A class with no watermark is a dash, and the hover says which of the two silences it is: this
 * integration keeps no watermark, versus this repository has never synced. A dash must never be read
 * as the second when it means the first.
 */
export const NotTrackedVersusNeverSynced: Story = {
	args: {
		resources: [
			{
				id: 300,
				externalId: "hephaestustest/fresh-clone",
				name: "hephaestustest/fresh-clone",
				type: "REPOSITORY",
				counts: [],
				state: "PENDING",
			},
		],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// A resource that has never synced reports no classes at all. The columns still stand — they are
		// the connection's, not the row's — so the matrix does not vanish exactly when it is being asked
		// why nothing has synced; every cell is a dash whose hover says which silence it is.
		await expect(canvas.getByRole("columnheader", { name: "Issues" })).toBeInTheDocument();
		await expect(canvas.getAllByText("—")).toHaveLength(5);
		// PENDING has no timestamp to speak for it, so the state survives as a marker + its screen-reader text.
		await expect(canvas.getByText("Pending")).toBeInTheDocument();
		await userEvent.hover(canvas.getAllByText("—")[0]);
		await expect(await screen.findByText(/has not synced yet/i)).toBeInTheDocument();
	},
};

/** A backfill in flight reports itself under the name; the horizon it reached lives in the hover. */
export const Backfilling: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/backfilling · 62%/i)).toBeInTheDocument();
		await expect(
			canvas.getByRole("progressbar", { name: /backfill progress for ls1intum\/aeolus/i }),
		).toBeInTheDocument();
	},
};

/** The row hover carries what the row itself has no room for: state, upstream count, backfill horizon. */
export const RowHover: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.hover(canvas.getAllByText("ls1intum/Artemis")[0]);
		await expect(await screen.findByText(/upstream/i)).toBeInTheDocument();
		await expect(await screen.findByText(/backfilled to/i)).toBeInTheDocument();
	},
};

/** The error is a read-only peek: it hovers, it does not click, and it traps no focus. */
export const ResourceError: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.hover(
			canvas.getByRole("button", { name: /error for hephaestustest\/broken-repo/i }),
		);
		await expect(await screen.findByText(/repository access revoked/i)).toBeInTheDocument();
	},
};

/** Slack channels mirror one class, so the matrix collapses to a single Messages column. */
export const SingleClass: Story = {
	args: {
		resources: channels,
		resourceNoun: "channel",
		resourceNounPlural: "channels",
		expectedClassKeys: ["messages"],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("columnheader", { name: "Messages" })).toBeInTheDocument();
		await expect(canvas.queryByRole("columnheader", { name: "Issues" })).toBeNull();
	},
};

/** Long org/repo names and a long error must truncate/wrap without breaking the matrix. */
export const LongNames: Story = { args: { resources: longNames } };

/** The skeleton reserves the class columns, so resolving swaps text in rather than growing the table. */
export const Loading: Story = { args: { resources: [], isLoading: true } };

export const ErrorState: Story = {
	args: { resources: [], isError: true, error: new Error("Network error"), onRetry: fn() },
};

export const Empty: Story = { args: { resources: [] } };
