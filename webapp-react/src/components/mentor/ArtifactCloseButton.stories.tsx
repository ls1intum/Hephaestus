import type { Meta, StoryObj } from "@storybook/react";
import { ArtifactCloseButton } from "./ArtifactCloseButton";

/**
 * ArtifactCloseButton provides a close button for artifacts with smart state management.
 * This component directly manages artifact visibility and handles different close behaviors
 * based on the current artifact status (streaming vs idle).
 */
const meta = {
	component: ArtifactCloseButton,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		className: {
			description: "Additional CSS classes for styling",
		},
		iconSize: {
			control: { type: "number", min: 12, max: 32, step: 2 },
			description: "Size of the close icon in pixels",
		},
	},
	args: {
		iconSize: 18,
	},
} satisfies Meta<typeof ArtifactCloseButton>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default close button as used in artifact panels.
 */
export const Default: Story = {};
