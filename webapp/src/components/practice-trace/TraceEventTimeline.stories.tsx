import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { eventLabel, ownItemTrace } from "./fixtures";
import { TraceEventTimeline } from "./TraceEventTimeline";

/**
 * The events recorded about one piece of work that explain a review of it, oldest first, each in
 * the words the definition options give its name.
 */
const meta = {
	component: TraceEventTimeline,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		events: ownItemTrace.events,
		eventLabel,
	},
	argTypes: { events: { control: false }, eventLabel: { control: false } },
} satisfies Meta<typeof TraceEventTimeline>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Each event by its words, never by its wire name. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Marked ready for review")).toBeVisible();
		await expect(canvas.queryByText("scm.pull_request.ready")).not.toBeInTheDocument();
		await expect(canvas.getAllByRole("listitem")).toHaveLength(ownItemTrace.events.length);
	},
};

/** Inside a level: its own heading level, and each time as a day and a time with no tooltip stop. */
export const PlainInALevel: Story = {
	args: { title: "Why it ran", level: 3, plain: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Why it ran", level: 3 })).toBeVisible();
		await expect(canvas.queryByRole("button")).not.toBeInTheDocument();
	},
};

/** Nothing was ever recorded, so no practice was ever asked a question about this work. */
export const NothingRecorded: Story = {
	args: { events: [] },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Nothing was recorded about this work")).toBeVisible();
	},
};
