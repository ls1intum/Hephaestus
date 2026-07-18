import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SyncResourceState } from "@/api/types.gen";
import { SCM_CLASS_KEYS, SyncResourcesTable } from "./SyncResourcesTable";

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);
const daysAgo = (days: number) => minutesAgo(days * 60 * 24);

/** Hourly reconciliation: 2x = stale, 6x = very stale. Every fixture below is timed against it. */
const SYNC_INTERVAL_SECONDS = 3_600;

/**
 * The six entity classes an SCM repository mirrors — folded by the table into five count columns
 * (issue and review comments share the Comments column).
 *
 * This mirrors `ScmResourceCounts.toSyncResourceCounts` exactly: only issues and pull requests carry a
 * real per-class watermark; the mirror keeps none for comments, reviews, review comments or commits, so
 * those pass `undefined` — absent is honest, and it is the distinction the freshness hover preserves.
 */
function scmCounts(syncedAt: Date | undefined, scale = 1): SyncResourceState["counts"] {
	return [
		{ key: "issues", label: "Issues", count: 3410 * scale, lastSyncedAt: syncedAt },
		{ key: "pullRequests", label: "Pull requests", count: 1204 * scale, lastSyncedAt: syncedAt },
		{ key: "issueComments", label: "Comments", count: 12882 * scale, lastSyncedAt: undefined },
		{ key: "reviews", label: "Reviews", count: 4120 * scale, lastSyncedAt: undefined },
		{
			key: "reviewComments",
			label: "Review comments",
			count: 9004 * scale,
			lastSyncedAt: undefined,
		},
		{ key: "commits", label: "Commits", count: 28710 * scale, lastSyncedAt: undefined },
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
		itemCount: (3410 + 1204) * 3,
	},
	{
		// Silent-broken at the row level: thousands of issues, zero comments. Counts carry this as a bare
		// number, not a colour, because 0 comments is broken on some repositories and normal on others.
		id: 2,
		externalId: "ls1intum/Athena",
		name: "ls1intum/Athena",
		type: "REPOSITORY",
		counts: [
			{ key: "issues", label: "Issues", count: 3410, lastSyncedAt: minutesAgo(6) },
			{ key: "pullRequests", label: "Pull requests", count: 1204, lastSyncedAt: minutesAgo(6) },
			{ key: "issueComments", label: "Comments", count: 0, lastSyncedAt: undefined },
			{ key: "reviews", label: "Reviews", count: 4120, lastSyncedAt: undefined },
			{ key: "reviewComments", label: "Review comments", count: 0, lastSyncedAt: undefined },
			{ key: "commits", label: "Commits", count: 28710, lastSyncedAt: undefined },
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
		itemCount: 4614,
		backfillPercent: 62,
	},
	{
		// Never synced: empty breakdown, not a row of zeros. Freshness reads "Never"; every count is a dot.
		id: 4,
		externalId: "ls1intum/new-repo",
		name: "ls1intum/new-repo",
		type: "REPOSITORY",
		counts: [],
		state: "PENDING",
	},
	{
		// The attention row that triage floats to the top: eleven days behind an hourly schedule. SCM
		// providers emit no per-resource error and no ERROR state, so this is very-stale, not errored.
		id: 5,
		externalId: "ls1intum/legacy-mirror",
		name: "ls1intum/legacy-mirror",
		type: "REPOSITORY",
		counts: scmCounts(daysAgo(11)),
		state: "SYNCED",
		lastSyncedAt: daysAgo(11),
		itemCount: 4614,
	},
];

/** Slack channels mirror ONE class, and their display name (`#channel`) genuinely differs from the id. */
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

/**
 * Outline collections mirror one Documents class. Unlike SCM, Outline is the integration that populates
 * `upstreamCount` and `lastError`, so its fixtures are the honest home for the error cell and the
 * upstream reading — and its title differs from its collection id, so both lines show.
 */
const collections: SyncResourceState[] = [
	{
		id: 20,
		externalId: "col_handbook",
		name: "Engineering Handbook",
		type: "COLLECTION",
		counts: [{ key: "documents", label: "Documents", count: 342, lastSyncedAt: minutesAgo(8) }],
		state: "SYNCED",
		lastSyncedAt: minutesAgo(8),
		itemCount: 342,
		upstreamCount: 350,
	},
	{
		id: 21,
		externalId: "col_archive",
		name: "Archived Notes",
		type: "COLLECTION",
		counts: [{ key: "documents", label: "Documents", count: 12, lastSyncedAt: daysAgo(9) }],
		state: "SYNCED",
		lastSyncedAt: daysAgo(9),
		itemCount: 12,
		upstreamCount: 40,
		lastError: "401: the Outline API token was revoked",
	},
];

/** A fleet whose Comments class is zero everywhere while issues run to the thousands. */
const zeroCommentsFleet: SyncResourceState[] = [
	{
		id: 30,
		externalId: "acme/api",
		name: "acme/api",
		type: "REPOSITORY",
		counts: [
			{ key: "issues", label: "Issues", count: 2100, lastSyncedAt: minutesAgo(7) },
			{ key: "pullRequests", label: "Pull requests", count: 860, lastSyncedAt: minutesAgo(7) },
			{ key: "issueComments", label: "Comments", count: 0, lastSyncedAt: undefined },
			{ key: "reviews", label: "Reviews", count: 940, lastSyncedAt: undefined },
			{ key: "reviewComments", label: "Review comments", count: 0, lastSyncedAt: undefined },
			{ key: "commits", label: "Commits", count: 15400, lastSyncedAt: undefined },
		],
		state: "SYNCED",
		lastSyncedAt: minutesAgo(7),
		itemCount: 2960,
	},
	{
		id: 31,
		externalId: "acme/web",
		name: "acme/web",
		type: "REPOSITORY",
		counts: [
			{ key: "issues", label: "Issues", count: 1330, lastSyncedAt: minutesAgo(9) },
			{ key: "pullRequests", label: "Pull requests", count: 512, lastSyncedAt: minutesAgo(9) },
			{ key: "issueComments", label: "Comments", count: 0, lastSyncedAt: undefined },
			{ key: "reviews", label: "Reviews", count: 640, lastSyncedAt: undefined },
			{ key: "reviewComments", label: "Review comments", count: 0, lastSyncedAt: undefined },
			{ key: "commits", label: "Commits", count: 9800, lastSyncedAt: undefined },
		],
		state: "SYNCED",
		lastSyncedAt: minutesAgo(9),
		itemCount: 1842,
	},
];

/** The ONE real per-class failure: issues fresh, pull requests three days behind, under one headline. */
const divergent: SyncResourceState[] = [
	{
		id: 40,
		externalId: "ls1intum/Artemis",
		name: "ls1intum/Artemis",
		type: "REPOSITORY",
		counts: [
			{ key: "issues", label: "Issues", count: 3410, lastSyncedAt: minutesAgo(4) },
			{ key: "pullRequests", label: "Pull requests", count: 1204, lastSyncedAt: daysAgo(3) },
			{ key: "issueComments", label: "Comments", count: 12882, lastSyncedAt: undefined },
			{ key: "reviews", label: "Reviews", count: 4120, lastSyncedAt: undefined },
			{ key: "reviewComments", label: "Review comments", count: 9004, lastSyncedAt: undefined },
			{ key: "commits", label: "Commits", count: 28710, lastSyncedAt: undefined },
		],
		// The headline is the newest of the two watermarks — the fresh one — so without the marker the
		// row would read "4 minutes ago" and hide that pull requests stopped three days back.
		state: "SYNCED",
		lastSyncedAt: minutesAgo(4),
		itemCount: 4614,
	},
];

/** Seventy-one repositories, all fresh but one long-stale — the scale the triage sort exists for. */
const manyRepos: SyncResourceState[] = Array.from({ length: 71 }, (_, index) => {
	if (index === 37) {
		return {
			id: 1000 + index,
			externalId: "ls1intum/legacy-mirror",
			name: "ls1intum/legacy-mirror",
			type: "REPOSITORY",
			counts: scmCounts(daysAgo(9)),
			state: "SYNCED",
			lastSyncedAt: daysAgo(9),
			itemCount: 4614,
		} satisfies SyncResourceState;
	}
	const suffix = String(index + 1).padStart(2, "0");
	return {
		id: 1000 + index,
		externalId: `ls1intum/service-${suffix}`,
		name: `ls1intum/service-${suffix}`,
		type: "REPOSITORY",
		counts: scmCounts(minutesAgo(4 + index)),
		state: "SYNCED",
		lastSyncedAt: minutesAgo(4 + index),
		itemCount: 4614,
	} satisfies SyncResourceState;
});

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
		itemCount: 4614,
	},
];

/** One fresh repository and one very-stale one — the minimal set the Attention/Fresh facet filters. */
const facetMix: SyncResourceState[] = [
	{
		id: 50,
		externalId: "acme/fresh-service",
		name: "acme/fresh-service",
		type: "REPOSITORY",
		counts: scmCounts(minutesAgo(4)),
		state: "SYNCED",
		lastSyncedAt: minutesAgo(4),
		itemCount: 4614,
	},
	{
		id: 51,
		externalId: "acme/stale-service",
		name: "acme/stale-service",
		type: "REPOSITORY",
		counts: scmCounts(daysAgo(11)),
		state: "SYNCED",
		lastSyncedAt: daysAgo(11),
		itemCount: 4614,
	},
];

/**
 * Holds the table mounted across a resource change so its internal `facet` state survives, reproducing
 * the live case where a sync heals the last attention row while the "Attention" facet is selected.
 */
function FacetFallbackHarness() {
	const [healed, setHealed] = useState(false);
	const withAttention: SyncResourceState[] = [
		{
			id: 60,
			externalId: "acme/fresh-service",
			name: "acme/fresh-service",
			type: "REPOSITORY",
			counts: scmCounts(minutesAgo(4)),
			state: "SYNCED",
			lastSyncedAt: minutesAgo(4),
			itemCount: 4614,
		},
		{
			id: 61,
			externalId: "acme/stale-service",
			name: "acme/stale-service",
			type: "REPOSITORY",
			counts: scmCounts(daysAgo(11)),
			state: "SYNCED",
			lastSyncedAt: daysAgo(11),
			itemCount: 4614,
		},
	];
	// After the heal, the formerly very-stale row is fresh — so the fleet has zero attention rows left.
	const allFresh = withAttention.map((resource) =>
		resource.id === 61
			? { ...resource, counts: scmCounts(minutesAgo(6)), lastSyncedAt: minutesAgo(6) }
			: resource,
	);
	return (
		<div className="space-y-3">
			<button type="button" onClick={() => setHealed(true)}>
				Heal the stale row
			</button>
			<SyncResourcesTable
				resources={healed ? allFresh : withAttention}
				resourceNoun="repository"
				resourceNounPlural="repositories"
				syncIntervalSeconds={SYNC_INTERVAL_SECONDS}
				expectedClassKeys={SCM_CLASS_KEYS}
			/>
		</div>
	);
}

/**
 * The per-resource sync ledger. Counts are the columns — the abundant, real fact where only issues and
 * pull requests carry a watermark — so "0 comments next to 3,410 issues" reads across the whole fleet at
 * once. A triage sort floats the one broken repository to the top, a toolbar searches and facets the
 * set, and a totals footer catches the class that went silent everywhere.
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

/**
 * The headline: one column per class (issues, PRs, reviews, comments, commits), one "Last synced"
 * column, and a triage sort that puts the very-stale mirror in row one. The `Items`, `State` and
 * `Synced through` columns are absent.
 */
export const Default: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		for (const name of ["Issues", "PRs", "Reviews", "Comments", "Commits"]) {
			await expect(canvas.getByRole("columnheader", { name })).toBeInTheDocument();
		}
		await expect(canvas.getByRole("columnheader", { name: "Last synced" })).toBeInTheDocument();
		await expect(canvas.queryByRole("columnheader", { name: "Items" })).toBeNull();
		await expect(canvas.queryByRole("columnheader", { name: "State" })).toBeNull();
		await expect(canvas.queryByRole("columnheader", { name: /synced through/i })).toBeNull();

		// SCM sets name === externalId, so the id is not repeated inline — it renders exactly once.
		await expect(canvas.getAllByText("ls1intum/Artemis")).toHaveLength(1);

		// Triage default: the eleven-day-stale mirror sorts above every fresh repository.
		const firstRow = canvasElement.querySelector("tbody tr");
		await expect(firstRow?.textContent).toContain("legacy-mirror");
	},
};

/**
 * The one real per-class failure the counts cannot show: issues are minutes fresh while pull requests
 * are three days behind. The headline reading is the fresh one, so a marker warns the row is newer than
 * a class behind it, and the freshness hover names the laggard.
 */
export const WatermarkDivergence: Story = {
	args: { resources: divergent },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const marker = canvasElement.querySelector('[aria-label*="further behind"]');
		await expect(marker).not.toBeNull();

		await userEvent.hover(canvas.getByText(/ago$/));
		await expect(await screen.findByText("Pull requests")).toBeInTheDocument();
	},
};

/**
 * The fleet ledger: a class summing to zero across every repository while a sibling runs to the
 * thousands is flagged in the footer, where a per-row zero could never be — the comments pipeline is
 * off everywhere, not just here.
 */
export const ZeroCommentsAgainstManyIssues: Story = {
	args: { resources: zeroCommentsFleet },
	play: async ({ canvasElement }) => {
		const warningCell = canvasElement.querySelector("tfoot .text-warning");
		await expect(warningCell).not.toBeNull();

		const trigger = canvasElement.querySelector('tfoot [data-slot="tooltip-trigger"]');
		await userEvent.hover(trigger as HTMLElement);
		await expect(await screen.findByText(/pipeline may not be running/i)).toBeInTheDocument();
	},
};

/**
 * Seventy-one repositories: the triage sort floats the one long-stale mirror to the top, and the search
 * narrows the set without a debounce, reporting `n of N` while it filters.
 */
export const SeventyOneRepositories: Story = {
	args: { resources: manyRepos },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);

		// Containment guard: the scroll container clips the tall fleet in place rather than growing the
		// page (which is what made the footer float above ~614px of phantom scroll). Bounded to its
		// max-h-[70vh] cap AND scrollable — the same regression the ScrollArea's ManyRepositories story
		// pins for the manage-repos list, here for the sync table's own scroller.
		const container = canvasElement.querySelector<HTMLElement>('[data-slot="table-container"]');
		await expect(container).not.toBeNull();
		if (container) {
			await expect(container.clientHeight).toBeLessThanOrEqual(
				Math.ceil(window.innerHeight * 0.7) + 2,
			);
			await expect(container.scrollHeight).toBeGreaterThan(container.clientHeight);
		}

		// The single very-stale repository is row one, ahead of seventy fresh ones.
		const firstRow = canvasElement.querySelector("tbody tr");
		await expect(firstRow?.textContent).toContain("legacy-mirror");

		const search = canvas.getByRole("searchbox");
		await userEvent.type(search, "legacy");
		await expect(canvas.getByText(/1 of 71 repositories/i)).toBeInTheDocument();
		await expect(canvasElement.querySelectorAll("tbody tr")).toHaveLength(1);
	},
};

/**
 * A never-synced repository: an empty breakdown, so freshness reads "Never" and every count is a faint,
 * screen-reader-labelled dot rather than a fabricated zero. The hover says which silence it is.
 */
export const NeverSynced: Story = {
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
		// The class columns still stand — they are the connection's, not the row's.
		await expect(canvas.getByRole("columnheader", { name: "Issues" })).toBeInTheDocument();
		await expect(canvas.getByText("0 Issues")).toBeInTheDocument();

		await expect(canvas.getByText("Never")).toBeInTheDocument();
		await userEvent.hover(canvas.getByText("Never"));
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

/** The name hover carries what the row has no room for: state, item count, and the backfill note. */
export const RowHover: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.hover(canvas.getByText("ls1intum/Artemis"));
		await expect(await screen.findByText("Items")).toBeInTheDocument();
		await expect(await screen.findByText(/no backfill has run/i)).toBeInTheDocument();
	},
};

/**
 * The Slack admin page's ledger, exactly as that route now mounts it: one Messages column, no dead
 * columns, and — the parity the page was missing entirely — a per-channel freshness reading judged
 * against the connection's cadence. `#design` is nearly seven cadences behind, so it is tinted and
 * floated above the fresh channel by the triage sort. The channel name and its id genuinely differ,
 * so both lines show; the duplicate-line fix is SCM-only.
 */
export const SlackChannels: Story = {
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
		await expect(canvas.getByText("#engineering")).toBeInTheDocument();
		await expect(canvas.getByText("C0123ABCD")).toBeInTheDocument();
		// Two channels, one class: the footer sums but the pipeline-break rule is disabled.
		await expect(canvas.getByText("All channels")).toBeInTheDocument();

		// The stale channel is row one and its reading is judged, not merely printed.
		const firstRow = canvasElement.querySelector("tbody tr");
		await expect(firstRow?.textContent).toContain("#design");
		// The freshness cell specifically — not merely "something red somewhere in the row".
		const freshnessCell = firstRow?.querySelectorAll("td")[1];
		await expect(freshnessCell?.querySelector(".text-destructive")).not.toBeNull();
	},
};

/**
 * The Outline admin page's ledger, exactly as that route now mounts it: one Documents column, the
 * upstream coverage reading in the hover, and the read-only error peek on the collection whose token
 * was revoked — the one integration that populates `lastError`.
 *
 * This is the single home for a collection's document count and freshness. The management row in
 * `OutlineCollectionRow` deliberately no longer prints either, so the two surfaces cannot disagree and
 * the reading here is the only one — tinted, which the hand-rolled columns never were.
 */
export const OutlineCollections: Story = {
	args: {
		resources: collections,
		resourceNoun: "collection",
		resourceNounPlural: "collections",
		expectedClassKeys: ["documents"],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("columnheader", { name: "Documents" })).toBeInTheDocument();
		await expect(canvas.getByText("Engineering Handbook")).toBeInTheDocument();
		await expect(canvas.getByText("col_handbook")).toBeInTheDocument();

		// The nine-day-stale collection is triaged to row one and its reading is tinted.
		const firstRow = canvasElement.querySelector("tbody tr");
		await expect(firstRow?.textContent).toContain("Archived Notes");
		const freshnessCell = firstRow?.querySelectorAll("td")[1];
		await expect(freshnessCell?.querySelector(".text-destructive")).not.toBeNull();

		// The upstream denominator lives one hover away rather than as a second untinted column.
		await userEvent.hover(canvas.getByText("Engineering Handbook"));
		await expect(await screen.findByText("350 items")).toBeInTheDocument();

		await userEvent.hover(canvas.getByRole("button", { name: /error for archived notes/i }));
		await expect(await screen.findByText(/api token was revoked/i)).toBeInTheDocument();
	},
};

/** Without a cadence nothing is judged: no freshness colour, no divergence marker, no footer alarm. */
export const NoCadence: Story = {
	args: { resources, syncIntervalSeconds: undefined },
	play: async ({ canvasElement }) => {
		await expect(canvasElement.querySelectorAll(".text-warning").length).toBe(0);
		await expect(canvasElement.querySelectorAll(".text-destructive").length).toBe(0);
	},
};

/** A search that matches nothing keeps the header and offers a way back rather than reading as data loss. */
export const FilteredEmpty: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.type(canvas.getByRole("searchbox"), "zzz-no-such-repo");
		await expect(canvas.getByText(/no repositories match/i)).toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /clear filter/i }));
		await expect(canvas.getByText("ls1intum/Artemis")).toBeInTheDocument();
	},
};

/**
 * The status facet: the Attention/Fresh toggle only mounts because at least one row needs attention,
 * and each facet narrows the set to exactly its half.
 */
export const AttentionFilter: Story = {
	args: { resources: facetMix },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		// The default "all" facet shows both rows.
		await expect(canvas.getByText("acme/fresh-service")).toBeInTheDocument();
		await expect(canvas.getByText("acme/stale-service")).toBeInTheDocument();

		// Attention narrows to the very-stale row.
		await userEvent.click(canvas.getByRole("button", { name: /attention \(1\)/i }));
		await expect(canvas.getByText("acme/stale-service")).toBeInTheDocument();
		await expect(canvas.queryByText("acme/fresh-service")).not.toBeInTheDocument();

		// Fresh is the complement.
		await userEvent.click(canvas.getByRole("button", { name: /fresh \(1\)/i }));
		await expect(canvas.getByText("acme/fresh-service")).toBeInTheDocument();
		await expect(canvas.queryByText("acme/stale-service")).not.toBeInTheDocument();
	},
};

/**
 * Clearing the last attention row must not strand the table on the (now unmounted) "Attention" facet.
 * The toggle only mounts while `attentionCount > 0`, but the `facet` state outlives it, so the table
 * falls back to "all" whenever the toggle is gone — otherwise a sync that heals the last stale row
 * would collapse a full fleet to the "no repositories match" empty state.
 */
export const AttentionFacetFallsBackWhenCleared: Story = {
	// The harness owns its own resources; `args` is only here to satisfy the required-prop story type.
	args: { resources: [] },
	render: () => <FacetFallbackHarness />,
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);

		// Filter down to the one attention row.
		await userEvent.click(canvas.getByRole("button", { name: /attention \(1\)/i }));
		await expect(canvas.getByText("acme/stale-service")).toBeInTheDocument();
		await expect(canvas.queryByText("acme/fresh-service")).not.toBeInTheDocument();

		// A refresh heals the stale row: same table instance, new resources, no attention rows left.
		await userEvent.click(canvas.getByRole("button", { name: /heal the stale row/i }));

		// The facet toggle is gone (nothing needs attention) and the table fell back to "all" rather than
		// stranding on "attention" and rendering the empty state.
		await expect(canvas.queryByRole("button", { name: /attention/i })).not.toBeInTheDocument();
		await expect(
			canvas.queryByText(/no repositories match the current filter/i),
		).not.toBeInTheDocument();
		await expect(canvas.getByText("acme/fresh-service")).toBeInTheDocument();
	},
};

/** Long org/repo names must truncate without breaking the ledger. */
export const LongNames: Story = { args: { resources: longNames } };

/** The skeleton reserves the class columns, so resolving swaps text in rather than growing the table. */
export const Loading: Story = { args: { resources: [], isLoading: true } };

export const ErrorState: Story = {
	args: { resources: [], isError: true, error: new Error("Network error"), onRetry: fn() },
};

export const Empty: Story = { args: { resources: [] } };
