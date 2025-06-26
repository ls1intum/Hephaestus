import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { MessageComposer } from "./MessageComposer";

/**
 * Message composer component for typing and sending chat messages.
 * Supports keyboard shortcuts, character limits, and streaming state management.
 */
const meta = {
	component: MessageComposer,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		placeholder: {
			control: "text",
			description: "Placeholder text for the input field",
		},
		disabled: {
			control: "boolean",
			description: "Disables the entire composer",
		},
		isStreaming: {
			control: "boolean",
			description: "Shows stop button when true",
		},
		maxLength: {
			control: "number",
			description: "Maximum character limit",
		},
		onSubmit: {
			action: "message submitted",
			description: "Called when message is submitted",
		},
		onStop: {
			action: "generation stopped",
			description: "Called when stop button is clicked",
		},
	},
	args: {
		onSubmit: fn(),
		onStop: fn(),
	},
	decorators: [
		(Story) => (
			<div className="w-96 border rounded-lg overflow-hidden">
				<div className="h-64 bg-muted/30 flex items-center justify-center text-muted-foreground">
					Chat messages would appear here
				</div>
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof MessageComposer>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state ready for user input.
 */
export const Default: Story = {
	args: {
		placeholder: "Ask me anything about software development...",
	},
};

/**
 * Custom placeholder text for specific contexts.
 */
export const CustomPlaceholder: Story = {
	args: {
		placeholder: "Describe your coding challenge...",
	},
};

/**
 * Disabled state when input is not allowed.
 */
export const Disabled: Story = {
	args: {
		disabled: true,
		placeholder: "Please wait while we process your request...",
	},
};

/**
 * Streaming state with stop button shown.
 */
export const Streaming: Story = {
	args: {
		isStreaming: true,
		placeholder: "AI is responding...",
	},
};

/**
 * Reduced character limit for concise responses.
 */
export const ShortLimit: Story = {
	args: {
		maxLength: 200,
		placeholder: "Keep it brief (200 chars max)...",
	},
};

/**
 * Extended character limit for detailed questions.
 */
export const ExtendedLimit: Story = {
	args: {
		maxLength: 5000,
		placeholder: "Feel free to provide detailed context...",
	},
};

/**
 * Minimal styling variant.
 */
export const Minimal: Story = {
	args: {
		placeholder: "Type your message...",
		className: "border-0 bg-transparent p-2",
	},
	decorators: [
		(Story) => (
			<div className="w-96">
				<Story />
			</div>
		),
	],
};
