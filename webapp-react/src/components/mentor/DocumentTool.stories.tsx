import { DocumentTool } from "@/components/mentor/DocumentTool";
import type { Meta, StoryObj } from "@storybook/react";

/**
 * DocumentTool displays document operations in both loading and completed states.
 * A single component that adapts based on props - shows loading spinner when isLoading=true,
 * or completed result when result prop is provided.
 */
const meta = {
	component: DocumentTool,
	tags: ["autodocs"],
	argTypes: {
		type: {
			description: "Type of document operation",
			control: "select",
			options: ["create", "update", "request-suggestions"],
		},
		isLoading: {
			description:
				"Whether the operation is in progress (shows loading spinner)",
			control: "boolean",
		},
		isReadonly: {
			description: "Whether the document is in readonly mode",
			control: "boolean",
		},
	},
	args: {
		type: "create",
		isLoading: false,
		isReadonly: false,
	},
} satisfies Meta<typeof DocumentTool>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Completed document creation with clickable result.
 */
export const Created: Story = {
	args: {
		type: "create",
		result: {
			id: "doc-123",
			title: "Project Requirements",
			kind: "text",
		},
	},
};

/**
 * Document creation in progress with loading spinner.
 */
export const Creating: Story = {
	args: {
		type: "create",
		isLoading: true,
		args: {
			title: "Meeting Notes",
			kind: "text",
		},
	},
};

/**
 * Completed document update with clickable result.
 */
export const Updated: Story = {
	args: {
		type: "update",
		result: {
			id: "doc-456",
			title: "Meeting Notes",
			kind: "text",
		},
	},
};

/**
 * Document update in progress with loading spinner.
 */
export const Updating: Story = {
	args: {
		type: "update",
		isLoading: true,
		args: {
			id: "doc-456",
			description: "Add conclusion section",
		},
	},
};

/**
 * Completed suggestions addition with clickable result.
 */
export const SuggestionsAdded: Story = {
	args: {
		type: "request-suggestions",
		result: {
			id: "doc-789",
			title: "Project Plan",
			kind: "text",
		},
	},
};

/**
 * AI suggestions being added with loading spinner.
 */
export const RequestingSuggestions: Story = {
	args: {
		type: "request-suggestions",
		isLoading: true,
		args: {
			documentId: "doc-789",
		},
	},
};
