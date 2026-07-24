import type { Meta, StoryObj } from "@storybook/react";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";

/**
 * Shown on the Models tab whenever the server reports `usagePaused` for the workspace's
 * current-month budget (#1368 fix wave) — without it, a workspace admin browsing models has no
 * signal that detection and the mentor are silently paused (that used to be visible only on the
 * separate usage page). Two causes share this banner: the cap was reached (`verdict=EXHAUSTED`),
 * or some usage has no price set and this server's unpriced-usage policy is BLOCK
 * (`verdict=UNVERIFIABLE`).
 */
const meta = {
	component: BudgetExhaustedAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof BudgetExhaustedAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The month's confirmed spend reached the cap. */
export const Exhausted: Story = {
	args: { verdict: "EXHAUSTED" },
};

/** Default export with no explicit verdict — matches every caller before #1368's fix wave. */
export const Default: Story = {};

/**
 * Some usage this month has no price on record and this server's unpriced-usage policy is BLOCK,
 * so the month can't be verified within the cap — treated the same as EXHAUSTED for pausing new
 * work, but with wording that doesn't claim the cap was actually reached.
 */
export const Unverifiable: Story = {
	args: { verdict: "UNVERIFIABLE" },
};
