import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { DateRangeFacet } from "./DateRangeFacet";

const meta = {
	component: DateRangeFacet,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: { value: undefined, onChange: fn() },
} satisfies Meta<typeof DateRangeFacet>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Empty: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByRole("button", { name: "Date" })).toBeVisible();
	},
};

export const StartOnly: Story = {
	args: { value: { from: new Date(2026, 6, 1) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("From Jul 1, 2026")).toBeVisible();
	},
};

export const FullRange: Story = {
	args: { value: { from: new Date(2026, 6, 1), to: new Date(2026, 6, 24) } },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(await canvas.findByText("Jul 1 – Jul 24, 2026")).toBeVisible();
	},
};

export const AcrossMonths: Story = {
	args: { value: { from: new Date(2026, 5, 28), to: new Date(2026, 6, 3) } },
};

export const OpenedCalendar: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const trigger = await canvas.findByRole("button", { name: "Date" });

		await expect(trigger).toHaveAttribute("aria-expanded", "false");
		await userEvent.click(trigger);
		await expect(trigger).toHaveAttribute("aria-expanded", "true");
	},
};
