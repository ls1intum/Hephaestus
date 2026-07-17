import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { MonthNavigator } from "./MonthNavigator";

/**
 * Prev/next month stepper shared by the workspace and instance LLM usage pages.
 * Pure/presentational — the container owns the selected month.
 */
const meta = {
	component: MonthNavigator,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		month: "2026-07",
		canGoNext: true,
		onPrevMonth: fn(),
		onNextMonth: fn(),
	},
} satisfies Meta<typeof MonthNavigator>;

export default meta;
type Story = StoryObj<typeof meta>;

/** A past month — both directions are available. */
export const Default: Story = {};

/** The current month — stepping forward is disabled, since there are no future months. */
export const CurrentMonth: Story = {
	args: { canGoNext: false },
};

/** Month labels are spelled out in full, so the widest ones must still fit. */
export const LongMonthLabel: Story = {
	args: { month: "2026-09" },
};
