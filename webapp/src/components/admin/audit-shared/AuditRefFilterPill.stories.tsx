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

/** With a name resolved from the loaded rows — what an admin should normally see. */
export const WithName: Story = {
	args: { name: "Grace Hopper" },
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Actor: Grace Hopper")).toBeInTheDocument();
	},
};

/** No name available (e.g. the filter matches zero rows) — the id is all there is to show. */
export const IdOnly: Story = {
	args: {},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Actor: #7")).toBeInTheDocument();
	},
};

/** Only the X clears; the label is not a control, and the X says what it clears. */
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
