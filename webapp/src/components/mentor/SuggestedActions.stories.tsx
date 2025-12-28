import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { SuggestedActions } from "./SuggestedActions";

/**
 * SuggestedActions component displays actionable prompts for users to start conversations.
 * Features responsive grid layout, smooth animations, and purely presentational design.
 * Perfect for onboarding and guiding users toward meaningful interactions.
 */
const meta = {
	component: SuggestedActions,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		onAction: {
			description: "Callback function triggered when a suggested action is clicked",
			control: false,
		},
	},
	args: {
		onAction: fn(),
	},
} satisfies Meta<typeof SuggestedActions>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default suggested actions display with all four action buttons.
 */
export const Default: Story = {};
