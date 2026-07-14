import type { Meta, StoryObj } from "@storybook/react";
import { WebhookLivenessIndicator } from "./WebhookLivenessIndicator";

const meta = {
	component: WebhookLivenessIndicator,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof WebhookLivenessIndicator>;

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
