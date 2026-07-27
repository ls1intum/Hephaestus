import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import { AuditRefFilterPill } from "./AuditRefFilterPill";

const meta = {
	title: "Admin/Audit/AuditRefFilterPill",
	component: AuditRefFilterPill,
	parameters: { layout: "padded" },
	args: { label: "Actor", id: 7, onClear: fn() },
} satisfies Meta<typeof AuditRefFilterPill>;

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

export const ClearIsTheOnlyControl: Story = {
	args: { name: "Grace Hopper" },
	play: async ({ canvasElement, args }) => {
		const canvas = within(canvasElement);
		const buttons = canvas.getAllByRole("button");
		await expect(buttons).toHaveLength(1);

		await userEvent.click(
			canvas.getByRole("button", { name: /clear actor filter \(Grace Hopper\)/i }),
		);
		await expect(args.onClear).toHaveBeenCalledOnce();
	},
};
