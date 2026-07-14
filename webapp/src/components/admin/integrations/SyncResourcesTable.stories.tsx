import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
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
		state: "BACKFILLING",
		itemCount: 40,
		upstreamCount: 200,
		backfillPercent: 20,
	},
	{
		id: 3,
		externalId: "octocat/broken-repo",
		name: "octocat/broken-repo",
		type: "REPOSITORY",
		state: "ERROR",
		lastError: "403: repository access revoked",
	},
];

const meta = {
	component: SyncResourcesTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: { resourceNoun: "repository" },
} satisfies Meta<typeof SyncResourcesTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { resources } };
export const Loading: Story = { args: { resources: [], isLoading: true } };
export const ErrorState: Story = {
	args: { resources: [], isError: true, error: new Error("Network error"), onRetry: fn() },
};
export const Empty: Story = { args: { resources: [] } };
