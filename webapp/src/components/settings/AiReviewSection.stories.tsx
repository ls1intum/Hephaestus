import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AiReviewSection } from "./AiReviewSection";

/**
 * AiReviewSection component for managing AI-generated review comment preferences
 * Allows users to toggle practice review comments on their pull requests
 */
const meta = {
	component: AiReviewSection,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		aiReviewEnabled: {
			control: "boolean",
			description: "Whether AI review comments are enabled",
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

/**
 * Default state with AI review enabled
 */
export const Enabled: Story = {
	args: {
		aiReviewEnabled: true,
		isLoading: false,
	},
};

/**
 * State with AI review disabled
 */
export const Disabled: Story = {
	args: {
		aiReviewEnabled: false,
		isLoading: false,
	},
};

/**
 * Loading state where the toggle is disabled
 */
export const Loading: Story = {
	args: {
		aiReviewEnabled: true,
		isLoading: true,
	},
};
