import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AgentJobsTable } from "./AgentJobsTable";
import { mockConfigs, mockJobs } from "./storyMockData";

/**
 * Paginated table of agent job runs with status/runtime filters. Each row opens the
 * details panel; the table itself exposes no cancel/retry actions.
 */
const meta = {
	component: AgentJobsTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		jobs: mockJobs,
		configs: mockConfigs,
		isLoading: false,
		statusFilter: "ALL",
		configFilter: "ALL",
		onStatusFilterChange: fn(),
		onConfigFilterChange: fn(),
		onSelectJob: fn(),
	},
} satisfies Meta<typeof AgentJobsTable>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Mixed statuses: completed+delivered, running, failed-delivery. */
export const Default: Story = {};

export const Loading: Story = {
	args: { isLoading: true },
};

export const Empty: Story = {
	args: { jobs: [] },
};

/** Filtered to a single status. */
export const FilteredByStatus: Story = {
	args: { statusFilter: "COMPLETED", jobs: mockJobs.filter((j) => j.status === "COMPLETED") },
};

/** A filter is set but no jobs match — the empty state still renders. */
export const FilteredEmpty: Story = {
	args: { statusFilter: "CANCELLED", jobs: [] },
};

/** Query failed — destructive alert with a Retry affordance. */
export const LoadError: Story = {
	args: { isError: true, jobs: [], onRetry: fn() },
};
