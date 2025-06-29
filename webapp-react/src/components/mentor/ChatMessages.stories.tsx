import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";

import type { UIMessage } from "@ai-sdk/react";
import { ChatMessages } from "./ChatMessages";

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
				state: "done",
			},
		],
	},
	{
		id: "3",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Can you give me a practical example of the Single Responsibility Principle?",
			},
		],
	},
	{
		id: "4",
		role: "assistant",
		parts: [
			{
				type: "step-start",
			},
			{
				type: "text",
				text: "Absolutely! Here's a practical example of violating and then applying the Single Responsibility Principle:\n\n**❌ Violating SRP - Class doing too much:**\n\n```typescript\nclass UserManager {\n  saveUser(user: User) {\n    // Validation logic\n    if (!user.email || !user.name) {\n      throw new Error('Invalid user data');\n    }\n    \n    // Database logic\n    database.save(user);\n    \n    // Email logic\n    emailService.send({\n      to: user.email,\n      subject: 'Welcome!',\n      body: 'Thanks for joining!'\n    });\n    \n    // Logging logic\n    console.log(`User ${user.name} saved at ${new Date()}`);\n  }\n}\n```\n\n**✅ Following SRP - Separated responsibilities:**\n\n```typescript\nclass UserValidator {\n  validate(user: User): boolean {\n    return user.email && user.name;\n  }\n}\n\nclass UserRepository {\n  save(user: User): void {\n    database.save(user);\n  }\n}\n\nclass WelcomeEmailService {\n  sendWelcomeEmail(user: User): void {\n    emailService.send({\n      to: user.email,\n      subject: 'Welcome!',\n      body: 'Thanks for joining!'\n    });\n  }\n}\n\nclass UserService {\n  constructor(\n    private validator: UserValidator,\n    private repository: UserRepository,\n    private emailService: WelcomeEmailService,\n    private logger: Logger\n  ) {}\n  \n  saveUser(user: User): void {\n    if (!this.validator.validate(user)) {\n      throw new Error('Invalid user data');\n    }\n    \n    this.repository.save(user);\n    this.emailService.sendWelcomeEmail(user);\n    this.logger.log(`User ${user.name} saved`);\n  }\n}\n```\n\n**Benefits of this approach:**\n- Each class has a single, clear responsibility\n- Easier to test individual components\n- Changes to email logic don't affect database logic\n- More reusable components",
				state: "done",
			},
		],
	},
];

const longConversationMessages: UIMessage[] = [
	...sampleMessages,
	{
		id: "5",
		role: "user",
		parts: [
			{
				type: "text",
				text: "This is great! How does this relate to dependency injection?",
			},
		],
	},
	{
		id: "6",
		role: "assistant",
		parts: [
			{
				type: "step-start",
			},
			{
				type: "text",
				text: "Excellent question! Dependency injection is a technique that naturally supports the Single Responsibility Principle. In the example above, notice how `UserService` receives its dependencies through its constructor rather than creating them internally.\n\n**Benefits of Dependency Injection:**\n\n1. **Loose Coupling**: Classes don't depend on concrete implementations\n2. **Testability**: Easy to inject mock dependencies for testing\n3. **Flexibility**: Can switch implementations without changing the class\n4. **Single Responsibility**: Each class focuses on its core logic, not on creating dependencies\n\nThis creates a clean, maintainable architecture where each component has a clear purpose.",
				state: "done",
			},
		],
	},
	{
		id: "7",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Perfect! Can you also explain how to test such code?",
			},
		],
	},
];

const meta: Meta<typeof ChatMessages> = {
	title: "Components/Mentor/ChatMessages",
	component: ChatMessages,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Enhanced messages container with auto-scroll, loading states, and error handling. Displays conversation history with proper avatars, animations, and user experience enhancements.",
			},
		},
	},
	argTypes: {
		messages: {
			description: "Array of UIMessage objects to display",
		},
		isLoading: {
			control: "boolean",
			description: "Whether AI is currently generating a response",
		},
		error: {
			description: "Error object to display error state",
		},
		onRegenerate: {
			action: "regenerate",
			description: "Called when user clicks retry in error state",
		},
	},
	args: {
		onRegenerate: fn(),
	},
	decorators: [
		(Story) => (
			<div className="h-[600px] w-[800px] border rounded-lg">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof ChatMessages>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Empty chat state with welcoming message and suggested topics.
 */
export const Empty: Story = {
	args: {
		messages: [],
		isLoading: false,
		error: null,
	},
};

/**
 * Short conversation demonstrating message formatting and avatars.
 */
export const ShortConversation: Story = {
	args: {
		messages: sampleMessages.slice(0, 2),
		isLoading: false,
		error: null,
	},
};

/**
 * Full conversation showing multiple message exchanges.
 */
export const FullConversation: Story = {
	args: {
		messages: sampleMessages,
		isLoading: false,
		error: null,
	},
};

/**
 * Long conversation demonstrating scroll behavior.
 */
export const LongConversation: Story = {
	args: {
		messages: longConversationMessages,
		isLoading: false,
		error: null,
	},
};

/**
 * Loading state while AI is thinking and generating response.
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
						text: "Can you explain the difference between composition and inheritance in object-oriented programming?",
					},
				],
			},
		],
		isLoading: true,
		error: null,
	},
};

/**
 * Error state with retry option for failed AI requests.
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
						text: "What are some advanced React patterns I should know?",
					},
				],
			},
		],
		isLoading: false,
		error: new Error(
			"Failed to connect to AI service. Please check your internet connection and try again.",
		),
	},
};

/**
 * Network timeout error scenario.
 */
export const NetworkError: Story = {
	args: {
		messages: sampleMessages.slice(0, 2),
		isLoading: false,
		error: new Error(
			"Request timeout - The AI service is taking longer than expected to respond.",
		),
	},
};

/**
 * API rate limit error scenario.
 */
export const RateLimitError: Story = {
	args: {
		messages: sampleMessages.slice(0, 1),
		isLoading: false,
		error: new Error(
			"Rate limit exceeded. Please wait a moment before sending another message.",
		),
	},
};

/**
 * Conversation with streaming message being generated.
 */
export const StreamingResponse: Story = {
	args: {
		messages: [
			...sampleMessages.slice(0, 3),
			{
				id: "streaming-4",
				role: "assistant",
				parts: [
					{
						type: "step-start",
					},
					{
						type: "text",
						text: "Great question! Here's a practical example that demonstrates the Single Responsibility Principle in action...",
						state: "streaming",
					},
				],
			},
		],
		isLoading: true,
		error: null,
	},
};

/**
 * Message with various UI part types for comprehensive testing.
 */
export const MessageWithMultipleParts: Story = {
	args: {
		messages: [
			{
				id: "1",
				role: "user",
				parts: [
					{
						type: "text",
						text: "Can you help me understand React useEffect?",
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
						text: "Absolutely! useEffect is one of the most important React hooks. Let me break it down for you:",
						state: "done",
					},
				],
			},
			{
				id: "3",
				role: "user",
				parts: [
					{
						type: "text",
						text: "Perfect, that's exactly what I needed to understand!",
					},
				],
			},
		],
		isLoading: false,
		error: null,
	},
};
