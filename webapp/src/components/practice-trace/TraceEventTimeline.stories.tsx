import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { ownItemTrace } from "./fixtures";
import { TraceEventTimeline } from "./TraceEventTimeline";

const meta = {
	component: TraceEventTimeline,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		events: ownItemTrace.events,
	},
	argTypes: { events: { control: false } },
} satisfies Meta<typeof TraceEventTimeline>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Marked ready for review")).toBeVisible();
		await expect(canvas.queryByText("scm.pull_request.ready")).not.toBeInTheDocument();
		await expect(canvas.getAllByRole("listitem")).toHaveLength(ownItemTrace.events.length);
	},
};

export const PlainInALevel: Story = {
	args: { title: "Recorded activity", level: 3, plain: true },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("heading", { name: "Recorded activity", level: 3 }),
		).toBeVisible();
		await expect(canvas.queryByRole("button")).not.toBeInTheDocument();
	},
};

export const NothingRecorded: Story = {
	args: { events: [] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("No activity is available")).toBeVisible();
	},
};

export const UnknownRecordedActivity: Story = {
	args: {
		events: [
			{
				name: "legacy.retired.event",
				displayName: "Activity recorded",
				occurredAt: new Date("2026-08-05T08:02:00Z"),
			},
		],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Activity recorded")).toBeVisible();
		await expect(canvas.queryByText("legacy.retired.event")).toBeNull();
	},
};
