import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { TextArtifact } from "./TextArtifact";
import type { ArtifactShellModel, TextContentModel } from "./artifact-model";

/**
 * TextArtifact integrates shell + chat sidebar + text content via ArtifactShellModel.
 * Use this to validate the full presentational wrapper with minimal mock wiring.
 */
const defaultModel: ArtifactShellModel<TextContentModel> = {
	overlay: {
		title: "Project Plan",
		status: "idle",
		boundingBox: { top: 0, left: 0, width: 600, height: 400 },
	},
	ui: { isVisible: true, isMobile: false, readonly: false },
	chat: {
		messages: [],
		votes: [],
		status: "ready",
		attachments: [],
		onMessageSubmit: fn(),
		onStop: fn(),
		onFileUpload: async () => [],
		onMessageEdit: fn(),
		onCopy: fn(),
		onVote: fn(),
		onClose: fn(),
	},
	version: {
		isCurrentVersion: true,
		currentVersionIndex: -1,
		selectedUpdatedAt: new Date(),
	},
	content: {
		content: `# Project Plan\n\nThis is some example content to showcase the editor.`,
		mode: "edit",
		onSaveContent: fn(),
		isLoading: false,
	},
};

const meta = {
	component: TextArtifact,
	tags: ["autodocs"],
	parameters: { layout: "fullscreen" },
	args: {
		model: defaultModel satisfies ArtifactShellModel<TextContentModel>,
	},
} satisfies Meta<typeof TextArtifact>;

export default meta;
export type Story = StoryObj<typeof meta>;

export const Default: Story = {};

export const ViewingPreviousVersion: Story = {
	args: {
		model: {
			...defaultModel,
			version: {
				...(defaultModel.version ?? {
					isCurrentVersion: true,
					currentVersionIndex: -1,
				}),
				isCurrentVersion: false,
				currentVersionIndex: 2,
				canPrev: true,
				canNext: true,
			},
		},
	},
};
