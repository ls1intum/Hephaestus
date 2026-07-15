import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
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

/** One row per job status, including the duration edges (a queued PENDING row has no duration). */
const allStatuses: SyncJob[] = [
	{
		id: 60,
		type: "RECONCILIATION",
		trigger: "SCHEDULED",
		status: "PENDING",
		cancelRequested: false,
		createdAt: new Date("2026-07-14T11:00:00Z"),
	},
	{
		id: 50,
		type: "RECONCILIATION",
		trigger: "MANUAL",
		status: "RUNNING",
		cancelRequested: false,
		createdAt: new Date("2026-07-14T10:59:00Z"),
		startedAt: new Date("2026-07-14T10:59:10Z"),
		itemsProcessed: 7,
		itemsTotal: 20,
	},
	{
		id: 40,
		type: "INITIAL",
		trigger: "LIFECYCLE",
		status: "SUCCEEDED",
		cancelRequested: false,
		createdAt: new Date("2026-07-14T08:00:00Z"),
		startedAt: new Date("2026-07-14T08:00:00Z"),
		finishedAt: new Date("2026-07-14T08:05:00Z"),
		itemsProcessed: 128,
		itemsTotal: 128,
	},
	{
		id: 30,
		type: "RECONCILIATION",
		trigger: "SCHEDULED",
		status: "SUCCEEDED_WITH_WARNINGS",
		cancelRequested: false,
		createdAt: new Date("2026-07-13T02:00:00Z"),
		startedAt: new Date("2026-07-13T02:00:00Z"),
		finishedAt: new Date("2026-07-13T02:03:20Z"),
		itemsProcessed: 39,
		itemsTotal: 40,
		errorSummary: "1 repository skipped: access revoked",
	},
	{
		id: 20,
		type: "BACKFILL",
		trigger: "MANUAL",
		status: "CANCELLED",
		cancelRequested: true,
		createdAt: new Date("2026-07-12T14:00:00Z"),
		startedAt: new Date("2026-07-12T14:00:00Z"),
		finishedAt: new Date("2026-07-12T14:00:45Z"),
		itemsProcessed: 12,
	},
	{
		id: 10,
		type: "INITIAL",
		trigger: "SYSTEM",
		status: "FAILED",
		cancelRequested: false,
		createdAt: new Date("2026-07-01T09:00:00Z"),
		startedAt: new Date("2026-07-01T09:00:00Z"),
		finishedAt: new Date("2026-07-01T09:01:12Z"),
		errorSummary:
			"GitHub API rate limit exceeded after 3 retries; the backoff window did not clear before the job deadline, so the pass was abandoned and will be retried on the next scheduled reconciliation.",
	},
];

/**
 * Paginated audit trail of sync jobs. Each row carries a status badge (its colour matched to the
 * outcome), a humanised type/trigger, a computed duration ("Running…" while live, a dash when a
 * queued job has not started) and, on failures, an error popover. Container states — loading,
 * error-with-retry, empty and paged — are all covered.
 */
const meta = {
	component: SyncJobsTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof SyncJobsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { args: { jobs } };

/** Every status the wire can report, including the queued/no-duration and warning edges. */
export const AllStatuses: Story = { args: { jobs: allStatuses } };

/** The error popover surfaces the truncated summary on demand, keyed to the failing row. */
export const ErrorPopover: Story = {
	args: { jobs: allStatuses },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /error for job 10/i }));
		await expect(
			await screen.findByText(/rate limit exceeded after 3 retries/i),
		).toBeInTheDocument();
	},
};

export const Loading: Story = { args: { jobs: [], isLoading: true } };
export const ErrorState: Story = {
	args: { jobs: [], isError: true, error: new Error("Network error"), onRetry: fn() },
};
export const Empty: Story = { args: { jobs: [] } };
export const Paged: Story = {
	args: { jobs, page: 1, totalPages: 4, onPageChange: fn() },
};
