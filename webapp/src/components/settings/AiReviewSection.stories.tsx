import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AiReviewSection } from "./AiReviewSection";

const meta = {
	component: AiReviewSection,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		aiReviewEnabled: {
			control: "boolean",
			description: "Whether practice-feedback comments are enabled",
		},
		onToggleAiReview: {
			description: "Callback when AI review setting is changed",
		},
		isLoading: {
			control: "boolean",
			description: "Whether the component is in loading state",
		},
	},
	args: {
		onToggleAiReview: fn(),
	},
} satisfies Meta<typeof AiReviewSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Enabled: Story = {
	args: {
		aiReviewEnabled: true,
		isLoading: false,
	},
};

export const Disabled: Story = {
	args: {
		aiReviewEnabled: false,
		isLoading: false,
	},
};

export const Loading: Story = {
	args: {
		aiReviewEnabled: true,
		isLoading: true,
	},
};
