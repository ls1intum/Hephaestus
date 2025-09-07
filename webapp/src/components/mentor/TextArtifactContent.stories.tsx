import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { TextArtifactContent } from "./TextArtifactContent";

/**
 * TextArtifactContent renders editable text content with version context.
 * Presentational only: no fetching, no global state; ideal for Storybook.
 */
const meta = {
	component: TextArtifactContent,
	tags: ["autodocs"],
	parameters: { layout: "fullscreen" },
	argTypes: {
		mode: {
			control: "select",
			options: ["edit", "diff"],
			description: "Display mode",
		},
		status: {
			control: "select",
			options: ["idle", "streaming"],
			description: "Streaming status",
		},
		isCurrentVersion: {
			control: "boolean",
			description: "Whether the latest version is active",
		},
		currentVersionIndex: {
			control: "number",
			description: "Index of the current version",
		},
		isLoading: {
			control: "boolean",
			description: "Show loading skeleton",
		},
	},
	args: {
		content: `# Project Plan\n\nThis is some example content to showcase the editor.`,
		mode: "edit",
		status: "idle",
		isCurrentVersion: true,
		currentVersionIndex: 2,
		onSaveContent: fn(),
		isLoading: false,
	},
} satisfies Meta<typeof TextArtifactContent>;

export default meta;
export type Story = StoryObj<typeof meta>;

/**
 * Default editable content in the latest version.
 */
export const Default: Story = {};

/**
 * Loading state while versions/content are being fetched.
 */
export const Loading: Story = {
	args: { isLoading: true },
};
