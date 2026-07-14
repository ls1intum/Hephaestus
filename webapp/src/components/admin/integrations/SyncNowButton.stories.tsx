import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { SyncJob } from "@/api/types.gen";
import { SyncNowButton } from "./SyncNowButton";

const runningJob: SyncJob = {
	id: 1,
	type: "RECONCILIATION",
	trigger: "MANUAL",
	status: "RUNNING",
	cancelRequested: false,
	createdAt: new Date("2026-07-14T10:00:00Z"),
};

const meta = {
	component: SyncNowButton,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { onClick: fn() },
} satisfies Meta<typeof SyncNowButton>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Idle: Story = {};
export const Triggering: Story = { args: { isTriggering: true } };
export const ActiveJob: Story = { args: { activeJob: runningJob } };
export const Backfill: Story = { args: { label: "Backfill" } };
