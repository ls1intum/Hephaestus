import type { Meta, StoryObj } from "@storybook/react-vite";
import { DocumentToolRenderer } from "./DocumentToolRenderer";
import type { PartRendererProps, ToolPart } from "./types";

const meta = {
	component: DocumentToolRenderer as React.FC<
		PartRendererProps<"createDocument" | "updateDocument">
	>,
	parameters: { layout: "centered" },
} satisfies Meta<typeof DocumentToolRenderer>;
export default meta;

type Story = StoryObj<typeof meta>;

export const CreateInput: Story = {
	args: {
		message: {
			id: "m1",
			role: "assistant",
			parts: [],
		},
		part: {
			type: "tool-createDocument",
			state: "input-available",
			toolCallId: "tc1",
			input: { title: "Project Plan", kind: "text" },
		} as ToolPart<"createDocument">,
	},
};

export const UpdateInput: Story = {
	args: {
		message: {
			id: "m2",
			role: "assistant",
			parts: [],
		},
		part: {
			type: "tool-updateDocument",
			state: "input-available",
			toolCallId: "tc2",
			input: { id: "doc-1", description: "Refactor section 2" },
		} as ToolPart<"updateDocument">,
	},
};

export const Output: Story = {
	args: {
		message: {
			id: "m3",
			role: "assistant",
			parts: [],
		},
		part: {
			type: "tool-updateDocument",
			state: "output-available",
			toolCallId: "tc3",
			output: {
				id: "doc-1",
				title: "Project Plan",
				kind: "text",
				content: "Document content here",
				description: "Updated document",
			},
		} as ToolPart<"updateDocument">,
	},
};
