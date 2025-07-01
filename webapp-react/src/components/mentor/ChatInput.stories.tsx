import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { useState } from "react";

import { ChatInput } from "./ChatInput";

const meta: Meta<typeof ChatInput> = {
	component: ChatInput,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Professional chat input component with auto-resize, keyboard shortcuts, and streaming support. Features include character counting, composition event handling, and accessibility features.",
			},
		},
	},
	argTypes: {
		onSubmit: {
			action: "submit",
			description: "Called when user submits a message",
		},
		onStop: {
			action: "stop",
			description: "Called when user stops message generation",
		},
		placeholder: {
			control: "text",
			description: "Placeholder text for the input",
		},
		disabled: {
			control: "boolean",
			description: "Whether the input is disabled",
		},
		isStreaming: {
			control: "boolean",
			description: "Whether AI is currently streaming a response",
		},
		maxLength: {
			control: "number",
			description: "Maximum character limit for messages",
		},
		autoFocus: {
			control: "boolean",
			description: "Whether to auto-focus the input on mount",
		},
	},
	args: {
		onSubmit: fn(),
		onStop: fn(),
		placeholder: "Type your message...",
		disabled: false,
		isStreaming: false,
		maxLength: 2000,
		autoFocus: false,
	},
	decorators: [
		(Story) => (
			<div className="h-[200px] w-[600px] flex flex-col">
				<div className="flex-1 bg-muted/30 rounded-t-lg flex items-center justify-center">
					<span className="text-muted-foreground text-sm">
						Message area above
					</span>
				</div>
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof ChatInput>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default chat input ready for user interaction.
 */
export const Default: Story = {
	args: {},
};

/**
 * Input with custom placeholder encouraging specific questions.
 */
export const WithCustomPlaceholder: Story = {
	args: {
		placeholder:
			"Ask me about React hooks, TypeScript patterns, or software architecture...",
	},
};

/**
 * Input in streaming state showing stop button instead of send.
 */
export const Streaming: Story = {
	args: {
		isStreaming: true,
		placeholder: "AI is responding...",
	},
};

/**
 * Disabled input state for maintenance or restricted access.
 */
export const Disabled: Story = {
	args: {
		disabled: true,
		placeholder: "Chat is temporarily unavailable",
	},
};

/**
 * Input with shorter character limit for demonstration.
 */
export const WithCharacterLimit: Story = {
	args: {
		maxLength: 100,
		placeholder: "Short message (max 100 characters)",
	},
};

/**
 * Auto-focused input that immediately receives focus.
 */
export const AutoFocused: Story = {
	args: {
		autoFocus: true,
		placeholder: "This input will be automatically focused",
	},
};

/**
 * Interactive example showing controlled input behavior.
 */
export const Interactive: Story = {
	render: (args) => {
		const [isStreaming, setIsStreaming] = useState(false);
		const [disabled, setDisabled] = useState(false);

		const handleSubmit = (message: string) => {
			console.log("Submitted:", message);
			setIsStreaming(true);

			// Simulate AI response
			setTimeout(() => {
				setIsStreaming(false);
			}, 3000);
		};

		const handleStop = () => {
			console.log("Stopped");
			setIsStreaming(false);
		};

		return (
			<div className="space-y-4">
				<div className="flex gap-2">
					<button
						type="button"
						onClick={() => setDisabled(!disabled)}
						className="px-3 py-1 text-xs bg-blue-100 text-blue-700 rounded"
					>
						{disabled ? "Enable" : "Disable"}
					</button>
					<button
						type="button"
						onClick={() => setIsStreaming(!isStreaming)}
						className="px-3 py-1 text-xs bg-green-100 text-green-700 rounded"
					>
						{isStreaming ? "Stop Streaming" : "Start Streaming"}
					</button>
				</div>

				<ChatInput
					{...args}
					onSubmit={handleSubmit}
					onStop={handleStop}
					isStreaming={isStreaming}
					disabled={disabled}
					placeholder="Try typing a message and pressing Enter..."
				/>
			</div>
		);
	},
};

/**
 * Example showing long text behavior and auto-resize.
 */
export const LongTextExample: Story = {
	render: (args) => {
		return (
			<div className="space-y-2">
				<p className="text-sm text-muted-foreground">
					Try pasting this long text to see auto-resize behavior:
				</p>
				<code className="text-xs bg-muted p-2 rounded block">
					This is a longer message that demonstrates how the textarea
					automatically resizes as you type more content. The input will grow
					vertically to accommodate multiple lines while maintaining good UX
					boundaries.
				</code>
				<ChatInput
					{...args}
					onSubmit={args.onSubmit}
					placeholder="Paste the example text above or type a long message..."
				/>
			</div>
		);
	},
};
