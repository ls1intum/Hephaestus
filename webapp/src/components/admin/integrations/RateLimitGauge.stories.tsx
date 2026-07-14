import type { Meta, StoryObj } from "@storybook/react";
import { RateLimitGauge } from "./RateLimitGauge";

const meta = {
	component: RateLimitGauge,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof RateLimitGauge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Plenty: Story = {
	args: {
		rateLimit: { limit: 5000, remaining: 4200, resetAt: new Date(Date.now() + 45 * 60 * 1000) },
	},
};

export const Low: Story = {
	args: {
		rateLimit: { limit: 5000, remaining: 120, resetAt: new Date(Date.now() + 10 * 60 * 1000) },
	},
};

export const Unknown: Story = { args: { rateLimit: undefined } };
