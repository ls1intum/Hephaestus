import type { Meta, StoryObj } from "@storybook/react";
import AIMentor from "./AIMentor";

/**
 * AI Mentor component that provides guidance and assistance to users.
 * Can be displayed as a full button or an icon-only version for space-constrained UIs.
 */
const meta = {
	component: AIMentor,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"An AI assistance button that provides users with guidance and support throughout the application.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		iconOnly: {
			control: "boolean",
			description: "Whether to show only the icon without text",
			defaultValue: false,
		},
	},
} satisfies Meta<typeof AIMentor>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default view showing the full AI Mentor button with text and icon.
 */
export const Default: Story = {
	args: {
		iconOnly: false,
	},
};

/**
 * Compact version showing only the icon for space-constrained layouts.
 */
export const IconOnly: Story = {
	args: {
		iconOnly: true,
	},
};
