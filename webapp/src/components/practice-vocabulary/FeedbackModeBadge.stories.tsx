import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import { FeedbackModeBadge } from "./FeedbackModeBadge";

const meta = {
	component: FeedbackModeBadge,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
} satisfies Meta<typeof FeedbackModeBadge>;

export default meta;
type Story = StoryObj<typeof meta>;

export const EveryFeedbackMode: Story = {
	args: { feedbackMode: "APPROVE_FIRST" },
	render: () => (
		<div className="flex flex-wrap gap-2">
			<FeedbackModeBadge feedbackMode="OFF" />
			<FeedbackModeBadge feedbackMode="APPROVE_FIRST" />
			<FeedbackModeBadge feedbackMode="AUTOMATIC" />
		</div>
	),
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Off")).toBeVisible();
		await expect(canvas.getByText("Approve first")).toBeVisible();
		await expect(canvas.getByText("Automatic")).toBeVisible();
	},
};
