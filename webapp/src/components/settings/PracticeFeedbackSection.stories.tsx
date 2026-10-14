import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";

import { PracticeFeedbackSection } from "./PracticeFeedbackSection";

const meta = {
	component: PracticeFeedbackSection,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	args: {
		onTogglePracticeFeedback: fn(),
	},
} satisfies Meta<typeof PracticeFeedbackSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The choice belongs to the account, so turning it off in one workspace turns it off in all. */
export const Enabled: Story = {
	args: {
		practiceFeedbackDeliveryEnabled: true,
		isLoading: false,
	},
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("switch", { name: "Comments and Slack reminders" }),
		).toBeChecked();
		await expect(canvas.getByText(/This choice applies in every workspace\./u)).toBeVisible();
	},
};

export const Disabled: Story = {
	args: {
		practiceFeedbackDeliveryEnabled: false,
		isLoading: false,
	},
};

export const Loading: Story = {
	args: {
		practiceFeedbackDeliveryEnabled: true,
		isLoading: true,
	},
};
