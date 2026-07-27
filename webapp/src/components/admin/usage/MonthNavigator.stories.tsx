import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import { expectGenuinelyDisabled } from "@/test/controls";
import { MonthNavigator } from "./MonthNavigator";

/**
 * Prev/next month stepper shared by the workspace and instance AI usage pages.
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

/**
 * The current month — stepping forward is disabled, since there are no future months.
 *
 * `disabled`, not `aria-disabled`: a dimmed control stays in the tab order and is still announced as
 * available, so a keyboard user is invited to step into a month that cannot exist (WCAG 2.2
 * SC 4.1.2). Stepping back stays offered.
 */
export const CurrentMonth: Story = {
	args: { canGoNext: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Next month" }));
		await expect(canvas.getByRole("button", { name: "Previous month" })).toBeEnabled();
	},
};

/** Month labels are spelled out in full, so the widest ones must still fit. */
export const LongMonthLabel: Story = {
	args: { month: "2026-09" },
};
