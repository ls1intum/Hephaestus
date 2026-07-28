import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";
import { ReferenceFilterPill } from "./ReferenceFilterPill";

const meta = {
	title: "Common/ReferenceFilterPill",
	component: ReferenceFilterPill,
	parameters: { layout: "padded" },
	args: { label: "Actor", id: 7, onClear: fn() },
} satisfies Meta<typeof ReferenceFilterPill>;

export default meta;
type Story = StoryObj<typeof meta>;

export const WithName: Story = {
	args: { name: "Grace Hopper" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Actor: Grace Hopper")).toBeInTheDocument();
	},
};

export const IdOnly: Story = {
	args: {},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Actor: #7")).toBeInTheDocument();
	},
};

export const ClearsTheFilter: Story = {
	args: { name: "Grace Hopper" },
	play: async ({ canvasElement, args }) => {
		const canvas = within(canvasElement);
		await userEvent.click(
			canvas.getByRole("button", { name: /clear actor filter \(Grace Hopper\)/i }),
		);
		await expect(args.onClear).toHaveBeenCalledOnce();
	},
};
