import type { Meta, StoryObj } from "@storybook/react";
import type { SyncJob } from "@/api/types.gen";
import { ActiveJobProgress } from "./ActiveJobProgress";

const base: SyncJob = {
	id: 1,
	type: "RECONCILIATION",
	trigger: "MANUAL",
	status: "RUNNING",
	cancelRequested: false,
	createdAt: new Date("2026-07-14T10:00:00Z"),
};

const meta = {
	component: ActiveJobProgress,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof ActiveJobProgress>;

export default meta;
type Story = StoryObj<typeof meta>;

export const NoActiveJob: Story = { args: { job: null } };

export const DeterminateProgress: Story = {
	args: { job: { ...base, itemsProcessed: 4, itemsTotal: 12 } },
};

export const IndeterminateWithStep: Story = {
	args: { job: { ...base, progress: { currentStep: "pull-requests" } } },
};

export const IndeterminateNoStep: Story = {
	args: { job: { ...base, type: "BACKFILL" } },
};
