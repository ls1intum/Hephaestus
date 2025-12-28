import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { ArtifactCloseButton } from "./ArtifactCloseButton";

/**
 * ArtifactCloseButton provides a close button for artifacts.
 * Always requires an explicit onClose handler to be provided.
 */
const meta = {
	component: ArtifactCloseButton,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		onClose: {
			description: "Handler for closing the artifact",
			control: false,
		},
	},
	args: {
		onClose: fn(),
	},
} satisfies Meta<typeof ArtifactCloseButton>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default close button with handler.
 */
export const Default: Story = {};
