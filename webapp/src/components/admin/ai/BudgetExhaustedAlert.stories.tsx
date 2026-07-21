import type { Meta, StoryObj } from "@storybook/react";
import { BudgetExhaustedAlert } from "./BudgetExhaustedAlert";

/**
 * Shown on the Models tab when the workspace's current-month budget verdict is EXHAUSTED — without
 * it, a workspace admin browsing models has no signal that detection and the mentor are silently
 * paused (that used to be visible only on the separate usage page).
 */
const meta = {
	component: BudgetExhaustedAlert,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
} satisfies Meta<typeof BudgetExhaustedAlert>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};
