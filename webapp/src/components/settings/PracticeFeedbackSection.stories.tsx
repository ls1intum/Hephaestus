import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
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

export const Enabled: Story = {
	args: {
		practiceFeedbackDeliveryEnabled: true,
		isLoading: false,
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
