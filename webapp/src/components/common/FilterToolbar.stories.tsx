import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { FilterToolbar } from "./FilterToolbar";

const meta = {
	title: "Common/FilterToolbar",
	component: FilterToolbar,
	parameters: { layout: "padded" },
	args: {
		hasFilter: false,
		onReset: fn(),
		children: <span>Filter controls</span>,
	},
} satisfies Meta<typeof FilterToolbar>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		canvas.getByText("Filter controls");
		await expect(canvas.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
	},
};

export const Filtered: Story = {
	args: { hasFilter: true },
	play: async ({ args, canvasElement }) => {
		await userEvent.click(within(canvasElement).getByRole("button", { name: "Reset" }));
		await expect(args.onReset).toHaveBeenCalledOnce();
	},
};
