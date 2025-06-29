import type { Meta, StoryObj } from "@storybook/react";

import type { UIMessage } from "@ai-sdk/react";
import { Message } from "./Message";

const userMessage: UIMessage = {
	id: "1",
	role: "user",
	parts: [
		{
			type: "text",
			text: "What are the key principles of clean code?",
		},
	],
};

const shortAssistantMessage: UIMessage = {
	id: "2",
	role: "assistant",
	parts: [
		{
			type: "step-start",
		},
		{
			type: "text",
			text: "Clean code should be readable, maintainable, and simple. Focus on clear naming, small functions, and consistent formatting.",
			state: "done",
		},
	],
};

const longAssistantMessage: UIMessage = {
	id: "3",
	role: "assistant",
	parts: [
		{
			type: "step-start",
		},
		{
			type: "text",
			text: "Clean code is essential for maintainable software. Here are the key principles:\n\n**1. Readability First**\n- Code should read like well-written prose\n- Use meaningful variable and function names\n- Keep functions small and focused\n\n**2. Single Responsibility Principle**\n- Each function should do one thing well\n- Classes should have only one reason to change\n\n**3. DRY (Don't Repeat Yourself)**\n- Eliminate code duplication\n- Extract common functionality into reusable components\n\n**4. Consistent Formatting**\n- Use consistent indentation and spacing\n- Follow team coding standards\n- Use automated formatting tools\n\n**5. Meaningful Comments**\n- Explain 'why', not 'what'\n- Keep comments up-to-date with code changes\n- Remove obsolete comments\n\nRemember: clean code is not just about following rules, it's about making your code easy for other developers (including your future self) to understand and modify.",
			state: "done",
		},
	],
};

const codeExampleMessage: UIMessage = {
	id: "4",
	role: "assistant",
	parts: [
		{
			type: "step-start",
		},
		{
			type: "text",
			text: "Here's a practical example of refactoring code to be cleaner:\n\n**Before (messy code):**\n```javascript\nfunction calc(a, b, c) {\n  if (c == 1) {\n    return a + b;\n  } else if (c == 2) {\n    return a - b;\n  } else if (c == 3) {\n    return a * b;\n  } else {\n    return a / b;\n  }\n}\n```\n\n**After (clean code):**\n```javascript\nconst Operations = {\n  ADD: 1,\n  SUBTRACT: 2,\n  MULTIPLY: 3,\n  DIVIDE: 4\n};\n\nfunction calculate(firstNumber, secondNumber, operation) {\n  switch (operation) {\n    case Operations.ADD:\n      return add(firstNumber, secondNumber);\n    case Operations.SUBTRACT:\n      return subtract(firstNumber, secondNumber);\n    case Operations.MULTIPLY:\n      return multiply(firstNumber, secondNumber);\n    case Operations.DIVIDE:\n      return divide(firstNumber, secondNumber);\n    default:\n      throw new Error(`Unsupported operation: ${operation}`);\n  }\n}\n\nfunction add(a, b) { return a + b; }\nfunction subtract(a, b) { return a - b; }\nfunction multiply(a, b) { return a * b; }\nfunction divide(a, b) {\n  if (b === 0) throw new Error('Division by zero');\n  return a / b;\n}\n```\n\n**Improvements made:**\n- Descriptive function and variable names\n- Separated each operation into its own function\n- Added error handling\n- Used constants for operation types\n- Clear, readable structure",
			state: "done",
		},
	],
};

const streamingMessage: UIMessage = {
	id: "5",
	role: "assistant",
	parts: [
		{
			type: "step-start",
		},
		{
			type: "text",
			text: "Let me explain the difference between composition and inheritance in object-oriented programming...",
			state: "streaming",
		},
	],
};

const multiPartMessage: UIMessage = {
	id: "6",
	role: "assistant",
	parts: [
		{
			type: "step-start",
		},
		{
			type: "text",
			text: "I'll help you understand React hooks. Let me break this down:",
			state: "done",
		},
		{
			type: "text",
			text: "\n\n**useState** is for managing component state:\n```jsx\nconst [count, setCount] = useState(0);\n```",
			state: "done",
		},
		{
			type: "text",
			text: "\n\n**useEffect** is for side effects:\n```jsx\nuseEffect(() => {\n  // Effect logic here\n}, [dependencies]);\n```",
			state: "done",
		},
	],
};

const meta: Meta<typeof Message> = {
	title: "Components/Mentor/Message",
	component: Message,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Individual message component displaying user or assistant messages with proper avatars, formatting, and support for various AI SDK v5 message part types.",
			},
		},
	},
	argTypes: {
		message: {
			description: "UIMessage object containing the message data",
		},
	},
	decorators: [
		(Story) => (
			<div className="max-w-4xl mx-auto space-y-4 p-4">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof Message>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Basic user message showing how user input is displayed.
 */
export const UserMessage: Story = {
	args: {
		message: userMessage,
	},
};

/**
 * Short assistant response with AI mentor avatar.
 */
export const ShortAssistantMessage: Story = {
	args: {
		message: shortAssistantMessage,
	},
};

/**
 * Long assistant message with markdown formatting and structure.
 */
export const LongAssistantMessage: Story = {
	args: {
		message: longAssistantMessage,
	},
};

/**
 * Assistant message containing code examples and technical explanations.
 */
export const CodeExampleMessage: Story = {
	args: {
		message: codeExampleMessage,
	},
};

/**
 * Message currently being streamed from the AI (in progress).
 */
export const StreamingMessage: Story = {
	args: {
		message: streamingMessage,
	},
};

/**
 * Message with multiple text parts showing complex responses.
 */
export const MultiPartMessage: Story = {
	args: {
		message: multiPartMessage,
	},
};

/**
 * Comparison of user and assistant messages side by side.
 */
export const MessageComparison: Story = {
	render: () => (
		<div className="space-y-6">
			<div>
				<h3 className="text-sm font-medium text-muted-foreground mb-2">
					User Message
				</h3>
				<Message message={userMessage} />
			</div>

			<div>
				<h3 className="text-sm font-medium text-muted-foreground mb-2">
					Assistant Response
				</h3>
				<Message message={shortAssistantMessage} />
			</div>
		</div>
	),
};

/**
 * Various message lengths to test layout behavior.
 */
export const MessageLengthVariations: Story = {
	render: () => (
		<div className="space-y-6">
			<div>
				<h3 className="text-sm font-medium text-muted-foreground mb-2">
					Very Short Question
				</h3>
				<Message
					message={{
						id: "short",
						role: "user",
						parts: [{ type: "text", text: "Why?" }],
					}}
				/>
			</div>

			<div>
				<h3 className="text-sm font-medium text-muted-foreground mb-2">
					Medium Question
				</h3>
				<Message message={userMessage} />
			</div>

			<div>
				<h3 className="text-sm font-medium text-muted-foreground mb-2">
					Long Technical Question
				</h3>
				<Message
					message={{
						id: "long-question",
						role: "user",
						parts: [
							{
								type: "text",
								text: "I'm working on a React application and I'm trying to understand the difference between useCallback and useMemo hooks. I've read the documentation but I'm still confused about when to use each one. Can you provide practical examples showing the differences and explain the performance implications of each? Also, how do they relate to React's reconciliation process and when should I avoid using them?",
							},
						],
					}}
				/>
			</div>

			<div>
				<h3 className="text-sm font-medium text-muted-foreground mb-2">
					Comprehensive Answer
				</h3>
				<Message message={longAssistantMessage} />
			</div>
		</div>
	),
};
