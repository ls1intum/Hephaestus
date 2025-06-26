import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { Chat } from "./Chat";

/**
 * Complete chat interface combining messages display and input composer.
 * Provides a full-featured chat experience with streaming support and error handling.
 */
const meta = {
	component: Chat,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		isStreaming: {
			control: "boolean",
			description: "Shows streaming state and thinking indicator",
		},
		disabled: {
			control: "boolean",
			description: "Disables message input",
		},
		placeholder: {
			control: "text",
			description: "Custom placeholder for message input",
		},
		onMessageSubmit: {
			action: "message submitted",
			description: "Called when user sends a message",
		},
		onStop: {
			action: "generation stopped",
			description: "Called when user stops generation",
		},
		onRetry: {
			action: "retry clicked",
			description: "Called when user clicks retry after error",
		},
	},
	args: {
		onMessageSubmit: fn(),
		onStop: fn(),
		onRetry: fn(),
	},
	decorators: [
		(Story) => (
			<div className="h-[600px] w-[800px]">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof Chat>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Empty chat ready for the first message.
 */
export const Empty: Story = {
	args: {
		messages: [],
	},
};

/**
 * Active conversation between user and AI mentor.
 */
export const WithConversation: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [
					{
						type: "text",
						text: "What are React hooks and why should I use them?",
					},
				],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "React hooks are functions that let you use state and lifecycle features in functional components. They were introduced in React 16.8 and offer several advantages:\n\n**Benefits:**\n1. **Simpler code** - No need for class components\n2. **Better reusability** - Custom hooks can be shared\n3. **Easier testing** - Functions are easier to test than classes\n4. **Better performance** - Smaller bundle size\n\n**Most common hooks:**\n- `useState` - for managing state\n- `useEffect` - for side effects\n- `useContext` - for consuming context\n\nWould you like me to show you some examples?",
					},
				],
			},
			{
				id: "3",
				role: "user",
				parts: [
					{ type: "text", text: "Yes, please show me a useState example!" },
				],
			},
		],
	},
};

/**
 * AI is currently generating a response.
 */
export const Streaming: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [
					{
						type: "text",
						text: "Can you explain useState with a practical example?",
					},
				],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "Here's a practical useState example - a simple counter component:\n\n```jsx\nimport { useState } from 'react';\n\nfunction Counter() {\n  const [count, setCount] = useState(0);\n\n  return (\n    <div>\n      <p>Count: {count}</p>\n      <button onClick={() => setCount(count + 1)}>\n        Increment\n      </button>\n      <button onClick={() => setCount(count - 1)}>\n        Decrement\n      </button>\n      <button onClick={() => setCount(0)}>\n        Reset\n      </button>\n    </div>\n  );\n}\n```\n\n**Key points:**\n- `useState(0)` initializes count to 0\n- Returns array with current value and setter function\n- `setCount` triggers re-render when called\n- State updates are",
					},
				],
			},
		],
		isStreaming: true,
		streamingMessageId: "2",
	},
};

/**
 * Error state with retry option.
 */
export const WithError: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [{ type: "text", text: "Explain the useEffect hook to me" }],
			},
		],
		error: new Error("Network connection failed"),
	},
};

/**
 * Disabled state during processing.
 */
export const Disabled: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [{ type: "text", text: "What is TypeScript?" }],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "TypeScript is a programming language developed by Microsoft that builds on JavaScript by adding static type definitions.",
					},
				],
			},
		],
		disabled: true,
		placeholder: "Processing your request...",
	},
};

/**
 * Long conversation showcasing scroll behavior.
 */
export const LongConversation: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [
					{
						type: "text",
						text: "What are the key differences between React and Vue?",
					},
				],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "Great question! Here are the key differences between React and Vue:\n\n**React:**\n- Created by Facebook, more mature ecosystem\n- Uses JSX (JavaScript + HTML-like syntax)\n- More flexible but requires more decisions\n- Larger learning curve\n- Better for large, complex applications\n\n**Vue:**\n- More opinionated, easier to get started\n- Uses template syntax (closer to HTML)\n- Built-in state management and routing\n- Gentler learning curve\n- Great for rapid prototyping",
					},
				],
			},
			{
				id: "3",
				role: "user",
				parts: [
					{
						type: "text",
						text: "Which one should I choose for my first project?",
					},
				],
			},
			{
				id: "4",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "For your first project, I'd recommend considering:\n\n**Choose Vue if:**\n- You're new to frontend frameworks\n- You want to get productive quickly\n- You prefer template-based syntax\n- You're building a small to medium project\n\n**Choose React if:**\n- You want to learn the most popular framework\n- You plan to work at larger companies\n- You enjoy more flexibility in your tools\n- You're comfortable with a steeper learning curve\n\nWhat type of project are you planning to build?",
					},
				],
			},
			{
				id: "5",
				role: "user",
				parts: [
					{
						type: "text",
						text: "I want to build a personal portfolio website",
					},
				],
			},
			{
				id: "6",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "Perfect! For a personal portfolio website, I'd actually recommend React for these reasons:\n\n1. **Job Market** - React skills are highly valued by employers\n2. **Portfolio Value** - Shows you can handle the most popular framework\n3. **Ecosystem** - Excellent tools like Next.js for static sites\n4. **Learning Investment** - Time spent learning React pays off long-term\n\n**Recommended Stack:**\n- Next.js (React framework)\n- Tailwind CSS (styling)\n- Vercel (hosting)\n- MDX (blog posts if needed)\n\nThis combination is perfect for portfolios and will showcase modern development skills to potential employers.",
					},
				],
			},
			{
				id: "7",
				role: "user",
				parts: [
					{
						type: "text",
						text: "That sounds great! Can you help me get started with Next.js?",
					},
				],
			},
		],
	},
};
