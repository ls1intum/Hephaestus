import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import type { SyncResourceState } from "@/api/types.gen";
import { SyncResourcesTable } from "./SyncResourcesTable";

const resources: SyncResourceState[] = [
	{
		id: 1,
		externalId: "octocat/Hello-World",
		name: "octocat/Hello-World",
		type: "REPOSITORY",
		state: "ACTIVE",
		lastSyncedAt: new Date("2026-07-14T09:00:00Z"),
		itemCount: 128,
		upstreamCount: 128,
	},
	{
		id: 2,
		externalId: "octocat/private-repo",
		name: "octocat/private-repo",
		type: "REPOSITORY",
		state: "SYNCED",
		itemCount: 40,
		upstreamCount: 200,
		backfillPercent: 20,
	},
	{
		id: 3,
		externalId: "octocat/broken-repo",
		name: "octocat/broken-repo",
		type: "REPOSITORY",
		state: "PENDING",
		lastError: "403: repository access revoked",
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
		await userEvent.click(canvas.getByRole("button", { name: /error for octocat\/broken-repo/i }));
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
