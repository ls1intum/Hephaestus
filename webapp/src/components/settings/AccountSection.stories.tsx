import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { AccountSection } from "./AccountSection";

/**
 * AccountSection component for account management
 * Provides account deletion functionality with confirmation dialog
 */
const meta = {
	component: AccountSection,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		onDeleteAccount: {
			description: "Callback when account deletion is confirmed",
		},
		isDeleting: {
			control: "boolean",
			description: "Whether the delete action is in progress",
		},
	},
	args: {
		onDeleteAccount: fn(),
	},
} satisfies Meta<typeof AccountSection>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state where deletion is available
 */
export const Ready: Story = {
	args: {
		isDeleting: false,
	},
};

/**
 * Deleting state where the button is disabled during the operation
 */
export const Deleting: Story = {
	args: {
		isDeleting: true,
	},
};
