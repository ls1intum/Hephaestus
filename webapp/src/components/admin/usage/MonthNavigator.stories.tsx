import type { Meta, StoryObj } from "@storybook/react";
import { expect } from "storybook/test";

import { expectGenuinelyDisabled } from "@/test/controls";

import { MonthNavigator } from "./MonthNavigator";

const meta = {
	component: MonthNavigator,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		month: "2026-07",
		canGoNext: true,
		renderMonthLink: (month, props) => <a {...props} href={`?month=${month}`} />,
	},
} satisfies Meta<typeof MonthNavigator>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const CurrentMonth: Story = {
	args: { canGoNext: false },
	play: async ({ canvas }) => {
		await expectGenuinelyDisabled(canvas.getByRole("button", { name: "Next month" }));
		await expect(canvas.getByRole("link", { name: "Previous month" })).toHaveAttribute(
			"href",
			"?month=2026-06",
		);
	},
};

export const LongMonthLabel: Story = {
	args: { month: "2026-09" },
};
