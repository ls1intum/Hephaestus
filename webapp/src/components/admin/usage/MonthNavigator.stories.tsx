import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, within } from "storybook/test";
import { expectGenuinelyDisabled } from "@/test/controls";
import { MonthNavigator } from "./MonthNavigator";

/** Prev/next month stepper. Pure — the container owns the selected month. */
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

export const Default: Story = {};

/**
 * WCAG 2.2 SC 4.1.2: `disabled`, not `aria-disabled` — a merely dimmed control stays in the tab
 * order and still announces as available, inviting a keyboard user into a month that cannot exist.
 */
export const CurrentMonth: Story = {
	args: { canGoNext: false },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Next month" }));
		await expect(canvas.getByRole("button", { name: "Previous month" })).toBeEnabled();
	},
};

export const LongMonthLabel: Story = {
	args: { month: "2026-09" },
};
