import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { SyncJob } from "@/api/types.gen";
import { SyncJobsTable } from "./SyncJobsTable";

const jobs: SyncJob[] = [
	{
		id: 3,
		type: "RECONCILIATION",
		trigger: "MANUAL",
		status: "RUNNING",
		cancelRequested: false,
		createdAt: new Date("2026-07-14T10:00:00Z"),
		startedAt: new Date("2026-07-14T10:00:05Z"),
		itemsProcessed: 4,
		itemsTotal: 12,
	},
	{
		id: 2,
		type: "RECONCILIATION",
		trigger: "SCHEDULED",
		status: "SUCCEEDED",
		cancelRequested: false,
		createdAt: new Date("2026-07-13T02:00:00Z"),
		startedAt: new Date("2026-07-13T02:00:00Z"),
		finishedAt: new Date("2026-07-13T02:04:30Z"),
		itemsProcessed: 40,
		itemsTotal: 40,
	},
	{
		id: 1,
		type: "INITIAL",
		trigger: "LIFECYCLE",
		status: "FAILED",
		cancelRequested: false,
		createdAt: new Date("2026-07-01T09:00:00Z"),
		startedAt: new Date("2026-07-01T09:00:00Z"),
		finishedAt: new Date("2026-07-01T09:01:12Z"),
		errorSummary: "GitHub API rate limit exceeded after 3 retries",
	},
];

const meta = {
	component: SyncJobsTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof SyncJobsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { jobs } };
export const Loading: Story = { args: { jobs: [], isLoading: true } };
export const ErrorState: Story = {
	args: { jobs: [], isError: true, error: new Error("Network error"), onRetry: fn() },
};
export const Empty: Story = { args: { jobs: [] } };
export const Paged: Story = {
	args: { jobs, page: 1, totalPages: 4, onPageChange: fn() },
};
