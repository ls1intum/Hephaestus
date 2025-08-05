import type { Meta, StoryObj } from "@storybook/react";
import { MentorAvatar } from "./MentorAvatar";

/**
 * MentorAvatar component displays the AI assistant's avatar using a Bot icon.
 * Designed to provide visual consistency across the mentor interface.
 */
const meta = {
	component: MentorAvatar,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		className: {
			description: "Additional CSS classes for styling",
		},
	},
} satisfies Meta<typeof MentorAvatar>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default mentor avatar used in most message contexts.
 */
export const Default: Story = {};
