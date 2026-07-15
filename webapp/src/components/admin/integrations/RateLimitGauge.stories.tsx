import type { Meta, StoryObj } from "@storybook/react";
import { RateLimitGauge } from "./RateLimitGauge";

/**
 * A vendor API rate-limit budget as a remaining/limit gauge with a reset hint. Guards the edges:
 * a `limit` of 0 renders 0% instead of dividing by zero, an exhausted budget reads 0 remaining,
 * and an absent snapshot degrades to a dash rather than a broken bar. Large numbers are grouped
 * via `toLocaleString`.
 */
const meta = {
	component: RateLimitGauge,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof RateLimitGauge>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Healthy budget — most of the window still available. */
export const Plenty: Story = {
	args: {
		rateLimit: { limit: 5000, remaining: 4200, resetAt: new Date(Date.now() + 45 * 60 * 1000) },
	},
};

/** Running low — a small slice of the window remains before it resets. */
export const Low: Story = {
	args: {
		rateLimit: { limit: 5000, remaining: 120, resetAt: new Date(Date.now() + 10 * 60 * 1000) },
	},
};

/** Fully exhausted — 0 remaining renders an empty (0%) bar, not a broken one. */
export const Exhausted: Story = {
	args: {
		rateLimit: { limit: 5000, remaining: 0, resetAt: new Date(Date.now() + 3 * 60 * 1000) },
	},
};

/** A zero `limit` must not divide by zero — the percentage falls back to 0. */
export const NoLimit: Story = {
	args: { rateLimit: { limit: 0, remaining: 0 } },
};

/** Very large budgets are grouped for readability (e.g. GraphQL point budgets). */
export const LargeNumbers: Story = {
	args: {
		rateLimit: {
			limit: 15000,
			remaining: 12873,
			resetAt: new Date(Date.now() + 55 * 60 * 1000),
		},
	},
};

/** No snapshot available — the gauge degrades to a dash rather than rendering a stale bar. */
export const Unknown: Story = { args: { rateLimit: undefined } };
