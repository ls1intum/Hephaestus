import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";

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
				type: "text",
				text: "Clean code is essential for maintainable software. Here are the key principles:\n\n## 1. Readability First\n- Code should read like well-written prose\n- Use meaningful variable and function names\n- Keep functions small and focused\n\n## 2. Single Responsibility Principle\n- Each function should do one thing well\n- Classes should have only one reason to change\n\n## 3. DRY (Don't Repeat Yourself)\n- Eliminate code duplication\n- Extract common functionality into reusable components\n\n```typescript\n// Good: Clear, focused function\nfunction calculateTotalPrice(items: Item[]): number {\n  return items.reduce((total, item) => total + item.price, 0);\n}\n```\n\n## 4. Consistent Formatting\n- Use consistent indentation and spacing\n- Follow team coding standards\n- Use automated formatting tools\n\n## 5. Meaningful Comments\n- Explain 'why', not 'what'\n- Keep comments up-to-date with code changes\n- Remove obsolete comments\n\nWould you like me to elaborate on any of these principles?",
			},
		],
	},
];

/**
 * Minimal chat interface for AI Mentor conversations.
 * Clean design focused on the conversation without visual clutter.
 */
const meta: Meta<typeof Chat> = {
	component: Chat,
	parameters: {
		layout: "fullscreen",
	},
	tags: ["autodocs"],
	argTypes: {
		messages: {
			description: "Array of UIMessage objects for the conversation",
		},
		onSendMessage: {
			description: "Called when user sends a new message",
		},
		onStop: {
			description: "Called when user stops message generation",
		},
		onRegenerate: {
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
		placeholder: "Ask me anything about software development...",
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
 * Empty chat state - clean slate for new conversations.
 */
export const Empty: Story = {
	args: {
		messages: [],
	},
};

/**
 * Basic conversation with markdown rendering in assistant messages.
 */
export const BasicConversation: Story = {
	args: {
		messages: sampleMessages,
	},
};

/**
 * Chat showing loading state while AI generates response.
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
 * Chat displaying error state with retry functionality.
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
 * Long conversation demonstrating scrolling and message layout.
 */
export const LongConversation: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [{ type: "text", text: "What are design patterns?" }],
			},
			{
				id: "2",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "Design patterns are reusable solutions to common problems in software design. They represent best practices and provide a common vocabulary for developers.\n\n## Common Categories:\n\n### Creational Patterns\n- **Singleton**: Ensures only one instance\n- **Factory**: Creates objects without specifying exact classes\n- **Builder**: Constructs complex objects step by step\n\n### Structural Patterns\n- **Adapter**: Allows incompatible interfaces to work together\n- **Decorator**: Adds behavior to objects dynamically\n- **Facade**: Provides a simplified interface\n\n### Behavioral Patterns\n- **Observer**: Defines subscription mechanism\n- **Strategy**: Defines family of algorithms\n- **Command**: Encapsulates requests as objects",
					},
				],
			},
			{
				id: "3",
				role: "user",
				parts: [
					{
						type: "text",
						text: "Can you show me the Observer pattern in TypeScript?",
					},
				],
			},
			{
				id: "4",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "Here's the Observer pattern implemented in TypeScript:\n\n```typescript\n// Subject interface\ninterface Subject {\n  attach(observer: Observer): void;\n  detach(observer: Observer): void;\n  notify(): void;\n}\n\n// Observer interface\ninterface Observer {\n  update(subject: Subject): void;\n}\n\n// Concrete Subject\nclass ConcreteSubject implements Subject {\n  private observers: Observer[] = [];\n  private state: number = 0;\n\n  attach(observer: Observer): void {\n    this.observers.push(observer);\n  }\n\n  detach(observer: Observer): void {\n    const index = this.observers.indexOf(observer);\n    if (index > -1) {\n      this.observers.splice(index, 1);\n    }\n  }\n\n  notify(): void {\n    this.observers.forEach(observer => {\n      observer.update(this);\n    });\n  }\n\n  setState(state: number): void {\n    this.state = state;\n    this.notify();\n  }\n\n  getState(): number {\n    return this.state;\n  }\n}\n\n// Concrete Observer\nclass ConcreteObserver implements Observer {\n  constructor(private name: string) {}\n\n  update(subject: ConcreteSubject): void {\n    console.log(`${this.name} received update. New state: ${subject.getState()}`);\n  }\n}\n```\n\nThis pattern is useful for implementing event handling systems, model-view architectures, and reactive programming!",
					},
				],
			},
		],
	},
};
