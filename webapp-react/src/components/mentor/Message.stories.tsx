import type { Meta, StoryObj } from "@storybook/react";
import { Message } from "./Message";

/**
 * Message component displays individual chat messages with proper styling for different roles.
 * Supports text, images, reasoning, and source citations.
 */
const meta = {
	component: Message,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		role: {
			control: "select",
			options: ["user", "assistant"],
			description: "Message sender role",
		},
		isStreaming: {
			control: "boolean",
			description: "Shows streaming indicator when true",
		},
	},
} satisfies Meta<typeof Message>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * User message with simple text content.
 */
export const UserMessage: Story = {
	args: {
		role: "user",
		parts: [
			{
				type: "text",
				text: "Hello! Can you help me understand how React hooks work?",
			},
		],
	},
};

/**
 * Assistant message with helpful response.
 */
export const AssistantMessage: Story = {
	args: {
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "React hooks are functions that let you use state and lifecycle features in functional components. The most common ones are useState for managing state and useEffect for side effects.",
			},
		],
	},
};

/**
 * Assistant message currently being streamed.
 */
export const StreamingMessage: Story = {
	args: {
		role: "assistant",
		isStreaming: true,
		parts: [
			{
				type: "text",
				text: "React hooks are functions that let you use state and lifecycle features in functional comp",
			},
		],
	},
};

/**
 * Long user message that wraps to multiple lines.
 */
export const LongUserMessage: Story = {
	args: {
		role: "user",
		parts: [
			{
				type: "text",
				text: "I have been working on a complex React application and I'm struggling with state management. The component tree is getting quite deep and I'm passing props through many levels. I've heard about context and state management libraries like Redux or Zustand. What would you recommend for a medium-sized application with about 20-30 components?",
			},
		],
	},
};

/**
 * Assistant message with code example formatting.
 */
export const CodeExample: Story = {
	args: {
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "Here's a simple useState example:\n\n```jsx\nconst [count, setCount] = useState(0);\n\nreturn (\n  <button onClick={() => setCount(count + 1)}>\n    Count: {count}\n  </button>\n);\n```\n\nThis creates a state variable and a function to update it.",
			},
		],
	},
};

/**
 * Message with reasoning details (expandable).
 */
export const MessageWithReasoning: Story = {
	args: {
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "Based on your requirements, I'd recommend using Zustand for state management.",
			},
			{
				type: "reasoning",
				details: [
					{
						type: "text",
						text: "The user mentioned a medium-sized application with 20-30 components. Zustand is lightweight, has a simple API, and doesn't require boilerplate like Redux. It's perfect for this scale of application.",
					},
				],
			},
		],
	},
};

/**
 * Message with source citations.
 */
export const MessageWithSources: Story = {
	args: {
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "React 19 introduces several new features including the new React Compiler and improved concurrent features.",
			},
			{
				type: "source",
				source: {
					id: "1",
					url: "https://react.dev/blog/2024/04/25/react-19",
					title: "React 19 Beta Blog Post",
				},
			},
			{
				type: "source",
				source: {
					id: "2",
					url: "https://github.com/facebook/react",
					title: "React GitHub Repository",
				},
			},
		],
	},
};
