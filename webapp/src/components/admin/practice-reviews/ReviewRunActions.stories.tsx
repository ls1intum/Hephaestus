import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import { mockJobFailedDelivery, mockJobRunning } from "@/components/admin/ai/story-mock-data";

import { ReviewRunActions } from "./ReviewRunActions";

const meta = {
	title: "Workspace admin/Practice reviews/Building blocks/Review actions",
	component: ReviewRunActions,
	parameters: {
		layout: "centered",
		chromatic: { viewports: [1440] },
	},
	tags: ["autodocs"],
	args: {
		job: mockJobRunning,
		isCancelling: false,
		isRetrying: false,
		onCancel: fn(),
		onRetry: fn(),
	},
} satisfies Meta<typeof ReviewRunActions>;

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

export const RetryFailedDelivery: Story = {
	args: { job: mockJobFailedDelivery },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Retry feedback comment" }));
		const dialog = within(await screen.findByRole("alertdialog"));
		dialog.getByText("The failed comment will be posted again.");
		await userEvent.click(dialog.getByRole("button", { name: "Retry feedback comment" }));
		await expect(args.onRetry).toHaveBeenCalledOnce();
	},
};
