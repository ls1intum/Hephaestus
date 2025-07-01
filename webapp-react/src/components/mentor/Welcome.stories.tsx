import type { Meta, StoryObj } from "@storybook/react";
import { Welcome } from "./Welcome";

/**
 * Welcome component for the AI Mentor chat interface.
 * Displays a friendly greeting and suggested topics when starting a new conversation.
 */
const meta: Meta<typeof Welcome> = {
	component: Welcome,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
} satisfies Meta<typeof Welcome>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default welcome state with greeting and topic suggestions.
 */
export const Default: Story = {};
