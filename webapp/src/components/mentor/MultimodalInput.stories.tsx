import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { MultimodalInput } from "./MultimodalInput";

/**
 * MultimodalInput component provides a rich text input interface with file upload capabilities.
 * Features auto-resizing textarea, attachment previews, suggested actions, and submission controls.
 * Now with smart internal state management - handles its own input state while exposing a clean API for business logic.
 */
const meta = {
	component: MultimodalInput,
	tags: ["autodocs"],
	argTypes: {
		status: {
			description: "Current upload/submission status",
			control: "select",
			options: ["ready", "submitted", "error"],
		},
		onStop: {
			description: "Handler for stopping current submission",
			control: false,
		},
		attachments: {
			description: "Array of current attachments",
			control: "object",
		},
		onAttachmentsChange: {
			description: "Handler for attachment changes",
			control: false,
		},
		onFileUpload: {
			description: "Handler for file upload processing",
			control: false,
		},
		onSubmit: {
			description: "Handler for form submission with text and attachments",
			control: false,
		},
		placeholder: {
			description: "Placeholder text for textarea",
			control: "text",
		},
		showSuggestedActions: {
			description:
				"Whether to show suggested actions (requires onSuggestedAction handler, disabled in readonly mode)",
			control: "boolean",
		},
		initialInput: {
			description: "Initial input value",
			control: "text",
		},
		readonly: {
			description: "Whether the input should be readonly",
			control: "boolean",
		},
		disableAttachments: {
			description: "Whether to disable attachment functionality",
			control: "boolean",
		},
	},
	args: {
		status: "ready",
		onStop: fn(),
		attachments: [],
		onAttachmentsChange: fn(),
		onFileUpload: fn(async () => []),
		onSubmit: fn(),
		// Suggested actions send immediately via onSubmit; no handler required
		placeholder: "Send a message...",
		showSuggestedActions: true, // Explicitly enable suggested actions
		initialInput: "",
		readonly: false,
		disableAttachments: false,
	},
	decorators: [
		(Story) => (
			<div className="max-w-2xl w-full pt-20">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof MultimodalInput>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default empty state with suggested actions visible.
 */
export const Default: Story = {};

/**
 * Explicitly showing suggested actions with clear action handler.
 */
export const WithSuggestedActions: Story = {
	args: {
		showSuggestedActions: true,
	},
};

/**
 * Input with some initial text.
 */
export const WithInitialText: Story = {
	args: {
		initialInput: "What are the best practices for React component design?",
	},
};

/**
 * Input with attached files showing preview thumbnails.
 */
export const WithAttachments: Story = {
	args: {
		attachments: [
			{
				name: "screenshot.png",
				url: "https://picsum.photos/seed/screenshot/200/150",
				contentType: "image/png",
			},
			{
				name: "document.pdf",
				url: "https://example.com/document.pdf",
				contentType: "application/pdf",
			},
		],
	},
};

/**
 * Submission in progress showing stop button.
 */
export const Submitting: Story = {
	args: {
		status: "submitted",
	},
};

/**
 * Custom placeholder text demonstration.
 */
export const CustomPlaceholder: Story = {
	args: {
		placeholder: "Ask me anything about your project...",
	},
};

/**
 * Disabled suggested actions.
 */
export const NoSuggestedActions: Story = {
	args: {
		showSuggestedActions: false,
	},
};

/**
 * Readonly input state - suggested actions are automatically disabled.
 */
export const ReadonlyInput: Story = {
	args: {
		readonly: true,
		initialInput: "This input is readonly",
		showSuggestedActions: true, // This will be ignored due to readonly
	},
};

/**
 * Input with attachments disabled - no attachment button or file upload.
 */
export const DisabledAttachments: Story = {
	args: {
		disableAttachments: true,
		placeholder: "Send a message (attachments disabled)...",
	},
};
