import type { UIMessage } from "@ai-sdk/react";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { Message } from "./Message";

// Define the union type for better TypeScript inference
type MessageArgs =
	| {
			type: "message";
			message: UIMessage;
			className?: string;
	  }
	| {
			type: "loading";
			className?: string;
	  }
	| {
			type: "error";
			error: Error;
			onRetry?: () => void;
			className?: string;
	  };

const userMessage: UIMessage = {
	id: "1",
	role: "user",
	parts: [
		{
			type: "text",
			text: "How do I write effective pull request descriptions?",
		},
	],
};

const shortAssistantMessage: UIMessage = {
	id: "2",
	role: "assistant",
	parts: [
		{
			type: "text",
			text: "Great question! **Clear PR descriptions** help reviewers understand your changes quickly. Include the **why** behind your changes, not just the what.",
		},
	],
};

const longAssistantMessage: UIMessage = {
	id: "3",
	role: "assistant",
	parts: [
		{
			type: "text",
			text: `Here's how to write PR descriptions that get reviewed faster:

## 1. Start with the Why
Explain the problem you're solving or the feature you're adding. Context helps reviewers understand the motivation.

## 2. Describe What Changed
Highlight the key changes without going into every line detail:

- Added user authentication middleware
- Updated database schema for new user fields
- Fixed edge case in password validation

## 3. Include Testing Notes
Let reviewers know how to test your changes:

\`\`\`bash
# Test the new authentication flow
npm run test:auth

# Verify the UI changes
npm run dev
# Navigate to /login and test different scenarios
\`\`\`

## 4. Call Out Breaking Changes
If your PR includes breaking changes, make them **very obvious**:

⚠️ **BREAKING CHANGE**: Updated API endpoint from \`/api/users\` to \`/api/v2/users\`

## 5. Link Related Issues
Reference the ticket or issue this PR addresses:

Closes #123
Related to #456

This approach helps your team review faster and reduces back-and-forth questions!`,
		},
	],
};

/**
 * Message component for displaying chat messages, loading states, and error states.
 * Supports user messages, assistant messages with markdown, loading indicators, and error handling.
 */
const meta = {
	component: Message,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Versatile message component that handles chat messages, loading states, and error states with consistent styling.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		type: {
			control: "select",
			options: ["message", "loading", "error"],
			description: "Type of message to display",
		},
		message: {
			description: "UIMessage object for regular messages",
		},
		error: {
			description: "Error object for error states",
		},
		onRetry: {
			description: "Callback function for retry action in error state",
		},
	},
	// For union types, we need to provide a default args structure
	args: {
		type: "message" as const,
		message: userMessage,
	},
	decorators: [
		(Story) => (
			<div className="max-w-4xl w-full p-6 bg-background">
				<Story />
			</div>
		),
	],
} satisfies Meta<MessageArgs>;

export default meta;
// biome-ignore lint/suspicious/noExplicitAny: Complex union type requires any for Storybook compatibility
type Story = StoryObj<any>;

/**
 * User message showing a process-related question.
 */
export const UserMessage: Story = {
	args: {
		type: "message",
		message: userMessage,
	},
};

/**
 * Short assistant response with basic markdown formatting.
 */
export const ShortAssistantMessage: Story = {
	args: {
		type: "message",
		message: shortAssistantMessage,
	},
};

/**
 * Long assistant message demonstrating comprehensive markdown rendering.
 */
export const LongAssistantMessage: Story = {
	args: {
		type: "message",
		message: longAssistantMessage,
	},
};

/**
 * Loading state while AI is processing.
 */
export const Loading: Story = {
	args: {
		type: "loading",
	},
};

/**
 * Error state with retry functionality.
 */
export const ErrorWithRetry: Story = {
	args: {
		type: "error",
		error: new Error(
			"Unable to connect to AI service. Please check your connection and try again.",
		),
		onRetry: fn(),
	},
};

/**
 * Error state without retry option.
 */
export const ErrorWithoutRetry: Story = {
	args: {
		type: "error",
		error: new Error(
			"Rate limit exceeded. Please wait a moment before trying again.",
		),
	},
};

/**
 * Conversation flow showing multiple message types.
 */
export const ConversationFlow: Story = {
	args: {
		type: "message" as const,
		message: userMessage,
	},
	render: () => (
		<div className="space-y-6">
			<Message type="message" message={userMessage} />
			<Message type="loading" />
		</div>
	),
	parameters: {
		docs: {
			description: {
				story: "Shows how loading state appears in a conversation context.",
			},
		},
	},
};
