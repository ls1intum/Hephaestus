import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import { ReviewActions } from "./ReviewActions";

const meta = {
	component: ReviewActions,
	parameters: {
		layout: "centered",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	args: {
		isCancelling: false,
		onCancel: fn(),
	},
} satisfies Meta<typeof ReviewActions>;

export default meta;
type Story = StoryObj<typeof meta>;

export const CancelRunningReview: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Cancel review" }));
		const dialog = within(await screen.findByRole("alertdialog"));
		dialog.getByText("The running review stops and cannot be resumed.");
		await userEvent.click(dialog.getByRole("button", { name: "Cancel review" }));
		await expect(args.onCancel).toHaveBeenCalledOnce();
	},
};

/** The cancel request is in flight: the button says so and takes no second press. */
export const Cancelling: Story = {
	args: { isCancelling: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Canceling…" })).toBeDisabled();
	},
};
