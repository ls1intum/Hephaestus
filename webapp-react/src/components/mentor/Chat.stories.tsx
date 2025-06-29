import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { useState } from "react";

import type { UIMessage } from "@ai-sdk/react";
import { Chat } from "./Chat";

const sampleMessages: UIMessage[] = [
	{
		id: "1",
		role: "user",
		parts: [
			{
				type: "text",
				text: "What are the key principles of clean code?",
			},
		],
	},
	{
		id: "2",
		role: "assistant",
		parts: [
			{
				type: "step-start",
			},
			{
				type: "text",
				text: "Clean code is essential for maintainable software. Here are the key principles:\n\n**1. Readability First**\n- Code should read like well-written prose\n- Use meaningful variable and function names\n- Keep functions small and focused\n\n**2. Single Responsibility Principle**\n- Each function should do one thing well\n- Classes should have only one reason to change\n\n**3. DRY (Don't Repeat Yourself)**\n- Eliminate code duplication\n- Extract common functionality into reusable components\n\n**4. Consistent Formatting**\n- Use consistent indentation and spacing\n- Follow team coding standards\n- Use automated formatting tools\n\n**5. Meaningful Comments**\n- Explain 'why', not 'what'\n- Keep comments up-to-date with code changes\n- Remove obsolete comments\n\nWould you like me to elaborate on any of these principles?",
			},
		],
	},
];

const meta: Meta<typeof Chat> = {
	title: "Components/Mentor/Chat",
	component: Chat,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"Complete chat interface with AI Mentor. Features professional header, message display with avatars, auto-scrolling, loading states, error handling, and message regeneration capabilities.",
			},
		},
	},
	argTypes: {
		messages: {
			description: "Array of UIMessage objects for the conversation",
		},
		onSendMessage: {
			action: "sendMessage",
			description: "Called when user sends a new message",
		},
		onStop: {
			action: "stop",
			description: "Called when user stops message generation",
		},
		onRegenerate: {
			action: "regenerate",
			description: "Called when user regenerates last assistant message",
		},
		isLoading: {
			control: "boolean",
			description: "Whether AI is currently generating a response",
		},
		error: {
			description: "Error object to display error state",
		},
		disabled: {
			control: "boolean",
			description: "Whether the input is disabled",
		},
		placeholder: {
			control: "text",
			description: "Placeholder text for the input",
		},
	},
	args: {
		onSendMessage: fn(),
		onStop: fn(),
		onRegenerate: fn(),
		isLoading: false,
		error: null,
		disabled: false,
		placeholder:
			"Ask me anything about software development, best practices, or technical concepts...",
	},
	decorators: [
		(Story) => (
			<div className="h-[700px] w-full max-w-4xl mx-auto">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof Chat>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Empty chat state with welcoming message and suggested topics.
 */
export const Empty: Story = {
	args: {
		messages: [],
	},
};

/**
 * Basic conversation demonstrating user and assistant messages.
 */
export const BasicConversation: Story = {
	args: {
		messages: sampleMessages,
	},
};

/**
 * Chat in loading state while AI generates response.
 */
export const Loading: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [
					{
						type: "text",
						text: "Can you explain dependency injection in TypeScript?",
					},
				],
			},
		],
		isLoading: true,
	},
};

/**
 * Chat showing an error state with retry option.
 */
export const WithError: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [
					{
						type: "text",
						text: "What are the best practices for React testing?",
					},
				],
			},
		],
		error: new Error(
			"Network connection failed. Please check your internet connection and try again.",
		),
	},
};

/**
 * Interactive chat simulation demonstrating full conversation flow.
 */
export const InteractiveChat: Story = {
	render: (args) => {
		const [messages, setMessages] = useState<UIMessage[]>([]);
		const [isLoading, setIsLoading] = useState(false);

		const handleSendMessage = (text: string) => {
			const userMessage: UIMessage = {
				id: Date.now().toString(),
				role: "user",
				parts: [{ type: "text", text }],
			};

			setMessages((prev) => [...prev, userMessage]);
			setIsLoading(true);

			// Simulate AI response
			setTimeout(() => {
				const assistantMessage: UIMessage = {
					id: (Date.now() + 1).toString(),
					role: "assistant",
					parts: [
						{ type: "step-start" },
						{
							type: "text",
							text: `Great question about "${text}"! This is a simulated AI response demonstrating how the chat interface handles real-time conversations with proper message formatting and structure.`,
						},
					],
				};

				setMessages((prev) => [...prev, assistantMessage]);
				setIsLoading(false);
			}, 2000);
		};

		const handleRegenerate = () => {
			if (
				messages.length > 0 &&
				messages[messages.length - 1].role === "assistant"
			) {
				setIsLoading(true);

				setTimeout(() => {
					const newResponse: UIMessage = {
						...messages[messages.length - 1],
						id: Date.now().toString(),
						parts: [
							{ type: "step-start" },
							{
								type: "text",
								text: "Here's a regenerated response with different phrasing and approach. This demonstrates the regeneration functionality working smoothly.",
							},
						],
					};

					setMessages((prev) => [...prev.slice(0, -1), newResponse]);
					setIsLoading(false);
				}, 1500);
			}
		};

		return (
			<Chat
				{...args}
				messages={messages}
				onSendMessage={handleSendMessage}
				onRegenerate={handleRegenerate}
				onStop={() => setIsLoading(false)}
				isLoading={isLoading}
			/>
		);
	},
};
