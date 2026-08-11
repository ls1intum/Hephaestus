import type { Meta, StoryObj } from "@storybook/react";
import { useState } from "react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SyncResourceState } from "@/api/types.gen";
import { SCM_CLASS_KEYS, SyncResourcesTable } from "./SyncResourcesTable";

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000);
const daysAgo = (days: number) => minutesAgo(days * 60 * 24);

const SYNC_INTERVAL_SECONDS = 3_600;

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
		id: 4,
		externalId: "ls1intum/new-repo",
		name: "ls1intum/new-repo",
		type: "REPOSITORY",
		counts: [],
		state: "PENDING",
	},
	{
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
		state: "SYNCED",
		lastSyncedAt: minutesAgo(4),
		itemCount: 4614,
	},
];

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

export const Default: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		for (const name of ["Issues", "PRs", "Reviews", "Comments", "Commits"]) {
			canvas.getByRole("columnheader", { name });
		}
		canvas.getByRole("columnheader", { name: "Last synced" });
		await expect(canvas.queryByRole("columnheader", { name: "Items" })).toBeNull();
		await expect(canvas.queryByRole("columnheader", { name: "State" })).toBeNull();
		await expect(canvas.queryByRole("columnheader", { name: /synced through/i })).toBeNull();

		canvas.getByText("ls1intum/Artemis");

		canvas.getByRole("row", { name: /legacy-mirror/ });
	},
};

export const WatermarkDivergence: Story = {
	args: { resources: divergent },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByLabelText(/further behind/);

		await userEvent.hover(canvas.getByText(/ago$/));
		await expect(await screen.findByText("Pull requests")).toBeInTheDocument();
	},
};

export const ZeroCommentsAgainstManyIssues: Story = {
	args: { resources: zeroCommentsFleet },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const totals = canvas.getByRole("row", { name: /All repositories/ });
		await userEvent.hover(within(totals).getByText("0"));
		await expect(await screen.findByText(/pipeline may not be running/i)).toBeInTheDocument();
	},
};

export const SeventyOneRepositories: Story = {
	args: { resources: manyRepos },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);

		const container = canvasElement.querySelector<HTMLElement>('[data-slot="table-container"]');
		await expect(container).not.toBeNull();
		if (container) {
			await expect(container.clientHeight).toBeLessThanOrEqual(
				Math.ceil(window.innerHeight * 0.7) + 2,
			);
			await expect(container.scrollHeight).toBeGreaterThan(container.clientHeight);
		}

		canvas.getByRole("row", { name: /legacy-mirror/ });

		const search = canvas.getByRole("searchbox");
		await userEvent.type(search, "legacy");
		canvas.getByText(/1 of 71 repositories/i);
	},
};

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
		canvas.getByText("0 Issues");

		canvas.getByText("Never");
		await userEvent.hover(canvas.getByText("Never"));
		await expect(await screen.findByText(/has not synced yet/i)).toBeInTheDocument();
	},
};

export const Backfilling: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText(/backfilling · 62%/i);
		canvas.getByRole("progressbar", { name: /backfill progress for ls1intum\/aeolus/i });
	},
};

export const RowHover: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.hover(canvas.getByText("ls1intum/Artemis"));
		await expect(await screen.findByText("Items")).toBeInTheDocument();
		await expect(await screen.findByText(/no backfill has run/i)).toBeInTheDocument();
	},
};

export const SlackChannels: Story = {
	args: {
		resources: channels,
		resourceNoun: "channel",
		resourceNounPlural: "channels",
		expectedClassKeys: ["messages"],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByRole("columnheader", { name: "Messages" });
		await expect(canvas.queryByRole("columnheader", { name: "Issues" })).toBeNull();
		canvas.getByText("#engineering");
		canvas.getByText("C0123ABCD");
		canvas.getByText("All channels");

		const firstRow = canvas.getByRole("row", { name: /#design/ });
		within(firstRow).getByRole("button", { name: /very stale/i });
	},
};

export const OutlineCollections: Story = {
	args: {
		resources: collections,
		resourceNoun: "collection",
		resourceNounPlural: "collections",
		expectedClassKeys: ["documents"],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByRole("columnheader", { name: "Documents" });
		canvas.getByText("Engineering Handbook");
		canvas.getByText("col_handbook");

		const firstRow = canvas.getByRole("row", { name: /Archived Notes/ });
		within(firstRow).getByRole("button", { name: /very stale/i });

		await userEvent.hover(canvas.getByText("Engineering Handbook"));
		await expect(await screen.findByText("350 items")).toBeInTheDocument();

		await userEvent.hover(canvas.getByRole("button", { name: /error for archived notes/i }));
		await expect(await screen.findByText(/api token was revoked/i)).toBeInTheDocument();
	},
};

export const NoCadence: Story = {
	args: { resources, syncIntervalSeconds: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button", { name: /stale/i })).not.toBeInTheDocument();
		canvas.getAllByRole("button", { name: /ago$/ });
	},
};

export const FilteredEmpty: Story = {
	args: { resources },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.type(canvas.getByRole("searchbox"), "zzz-no-such-repo");
		canvas.getByText(/no repositories match/i);

		await userEvent.click(canvas.getByRole("button", { name: /clear filter/i }));
		canvas.getByText("ls1intum/Artemis");
	},
};

export const AttentionFilter: Story = {
	args: { resources: facetMix },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("acme/fresh-service");
		canvas.getByText("acme/stale-service");

		await userEvent.click(canvas.getByRole("button", { name: /attention \(1\)/i }));
		canvas.getByText("acme/stale-service");
		await expect(canvas.queryByText("acme/fresh-service")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /fresh \(1\)/i }));
		canvas.getByText("acme/fresh-service");
		await expect(canvas.queryByText("acme/stale-service")).not.toBeInTheDocument();
	},
};

export const AttentionFacetFallsBackWhenCleared: Story = {
	args: { resources: [] },
	render: () => <FacetFallbackHarness />,
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);

		await userEvent.click(canvas.getByRole("button", { name: /attention \(1\)/i }));
		canvas.getByText("acme/stale-service");
		await expect(canvas.queryByText("acme/fresh-service")).not.toBeInTheDocument();

		await userEvent.click(canvas.getByRole("button", { name: /heal the stale row/i }));

		await expect(canvas.queryByRole("button", { name: /attention/i })).not.toBeInTheDocument();
		await expect(
			canvas.queryByText(/no repositories match the current filter/i),
		).not.toBeInTheDocument();
		canvas.getByText("acme/fresh-service");
	},
};

export const LongNames: Story = { args: { resources: longNames } };

export const Loading: Story = { args: { resources: [], isLoading: true } };

export const ErrorState: Story = {
	args: { resources: [], isError: true, error: new Error("Network error"), onRetry: fn() },
};

export const Empty: Story = { args: { resources: [] } };
