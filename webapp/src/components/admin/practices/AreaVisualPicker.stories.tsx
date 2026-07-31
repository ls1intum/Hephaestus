import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { AreaVisualPicker } from "./AreaVisualPicker";

const meta = {
	title: "Admin/Practices/Building blocks/Area visual picker",
	component: AreaVisualPicker,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	args: {
		slug: "code-quality",
		name: "Code quality",
		onChange: fn(),
	},
} satisfies Meta<typeof AreaVisualPicker>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(
			canvas.getByRole("button", { name: "Edit icon and colour for Code quality" }),
		);

		await userEvent.click(await screen.findByRole("button", { name: "Colour amber" }));
		await expect(args.onChange).toHaveBeenCalledWith({ color: "amber" });

		await userEvent.click(await screen.findByLabelText("Git branch"));
		await expect(args.onChange).toHaveBeenCalledWith({ icon: "GitBranch" });
	},
};
