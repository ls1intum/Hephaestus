import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { ArtifactShell, type ArtifactShellProps } from "./ArtifactShell";
import { TextArtifactContent } from "./TextArtifactContent";

/**
 * ArtifactShell provides the presentational layout for artifact overlays: header, content area, and chat sidebar.
 * Use this to preview artifact UIs in isolation. No global state, API, or container logic involved.
 */
const meta = {
	component: ArtifactShell,
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
	argTypes: {
		isVisible: { control: "boolean", description: "Toggle overlay visibility" },
		readonly: { control: "boolean" },
		status: {
			control: "select",
			options: ["idle", "streaming"],
			description: "Chat status in the sidebar",
		},
	},
	args: {
		overlay: {
			title: "Demo Document",
			status: "idle",
			boundingBox: { top: 200, left: 420, width: 360, height: 220 },
		},
		isVisible: true,
		isMobile: false,
		readonly: false,
		messages: [],
		votes: [],
		status: "ready" as unknown as ArtifactShellProps["status"],
		attachments: [],
		onMessageSubmit: fn(),
		onStop: fn(),
		onFileUpload: async () => [],
		onMessageEdit: fn(),
		onCopy: fn(),
		onVote: fn(),
		onClose: fn(),
		actions: [],
	} satisfies Partial<ArtifactShellProps>,
} satisfies Meta<typeof ArtifactShell>;

export default meta;
export type Story = StoryObj<typeof meta>;

/**
 * Default visible shell with a TextArtifact content area.
 */
export const Default: Story = {
	args: {
		children: (
			<div className="h-full overflow-auto">
				<TextArtifactContent
					content={"# Hello World\n\nThis is a demo artifact body."}
					mode="edit"
					status="idle"
					isCurrentVersion={true}
					onSaveContent={fn()}
					isLoading={false}
				/>
			</div>
		),
	},
};

/**
 * Streaming chat status with disabled interactions in the content area.
 */
export const Streaming: Story = {
	args: {
		status: "streaming" as unknown as ArtifactShellProps["status"],
		interactionDisabled: true,
		children: (
			<div className="h-full overflow-auto">
				<TextArtifactContent
					content={"Generating content..."}
					mode="edit"
					status="streaming"
					isCurrentVersion={true}
					onSaveContent={fn()}
					isLoading={false}
				/>
			</div>
		),
	},
};
