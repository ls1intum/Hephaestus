import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { Button } from "@/components/ui/button";
import { AreaVisualPicker, type AreaVisualPickerProps } from "./AreaVisualPicker";

const meta = {
	title: "Shared/Practice catalog/Area visual picker",
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
			canvas.getByRole("button", { name: "Edit the icon and color for Code quality" }),
		);

		await userEvent.click(await screen.findByRole("button", { name: "amber" }));
		await expect(args.onChange).toHaveBeenCalledWith({ color: "amber" });

		await userEvent.click(await screen.findByLabelText("Git branch"));
		await expect(args.onChange).toHaveBeenCalledWith({ icon: "GitBranch" });
	},
};

function DisableWhileOpen(props: AreaVisualPickerProps) {
	const [disabled, setDisabled] = useState(false);
	return (
		<div className="flex items-center gap-2">
			<AreaVisualPicker {...props} disabled={disabled} />
			<Button type="button" variant="outline" onClick={() => setDisabled(true)}>
				Disable picker
			</Button>
		</div>
	);
}

export const DisabledWhileOpen: Story = {
	render: (args) => <DisableWhileOpen {...args} />,
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(
			canvas.getByRole("button", { name: "Edit the icon and color for Code quality" }),
		);
		await userEvent.click(canvas.getByRole("button", { name: "Disable picker" }));

		await expect(
			canvas.getByRole("button", { name: "Edit the icon and color for Code quality" }),
		).toBeDisabled();
		await expect(screen.queryByRole("searchbox", { name: "Search icons" })).not.toBeInTheDocument();
		await expect(args.onChange).not.toHaveBeenCalled();
	},
};
