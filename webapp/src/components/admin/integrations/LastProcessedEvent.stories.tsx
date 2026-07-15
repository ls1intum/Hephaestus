import type { Meta, StoryObj } from "@storybook/react";
import { LastProcessedEvent } from "./LastProcessedEvent";

/**
 * A "Last event processed N ago" line for a connection's most recent inbound webhook. An absent or
 * invalid timestamp renders a dash — never "just now" — so a missing event can't masquerade as a
 * fresh one.
 */
const meta = {
	component: LastProcessedEvent,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof LastProcessedEvent>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Live: Story = {
	args: { lastEventAt: new Date(Date.now() - 2 * 60 * 1000) },
};

export const Stale: Story = {
	args: { lastEventAt: new Date(Date.now() - 48 * 60 * 60 * 1000) },
};

export const Unknown: Story = {
	args: { lastEventAt: undefined },
};
