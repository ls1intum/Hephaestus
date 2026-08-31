import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";

import { ProductFeedbackDialog } from "./ProductFeedbackDialog";

const onSubmit = fn(async () => true);
const meta = {
	title: "Surveys/Product feedback dialog",
	component: ProductFeedbackDialog,
	args: { isSubmitting: false, onSubmit },
	tags: ["autodocs"],
} satisfies Meta<typeof ProductFeedbackDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const SubmitFeedback: Story = {
	play: async ({ canvas, args }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Send product feedback" }));
		const dialog = within(await within(document.body).findByRole("dialog"));
		await userEvent.type(
			dialog.getByRole("textbox", { name: "Message" }),
			"The survey flow is clear.",
		);
		await userEvent.click(dialog.getByRole("button", { name: "Send" }));
		await expect(args.onSubmit).toHaveBeenCalledWith("FEEDBACK", "The survey flow is clear.");
	},
};
