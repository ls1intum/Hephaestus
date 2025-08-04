import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { MessageActions } from "./MessageActions";

/**
 * MessageActions component provides copy, upvote, and downvote actions for chat messages.
 * Pure component that delegates all actions to parent through callbacks.
 */
const meta = {
	component: MessageActions,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		messageContentToCopy: {
			description: "The text content to copy",
			control: "text",
		},
		vote: {
			description: "Current vote state for the message",
			control: "object",
		},
		isLoading: {
			description: "Whether actions are currently loading",
			control: "boolean",
		},
		onCopy: {
			description: "Callback when copy action is triggered",
			control: false,
		},
		onVote: {
			description: "Callback when vote action is triggered (isUpvote: boolean)",
			control: false,
		},
	},
	args: {
		messageContentToCopy:
			"This is a sample message that can be copied, upvoted, or downvoted.",
		isLoading: false,
		onCopy: fn(),
		onVote: fn(),
	},
} satisfies Meta<typeof MessageActions>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default message actions with no vote state.
 */
export const Default: Story = {};

/**
 * Message actions with an upvote.
 */
export const Upvoted: Story = {
	args: {
		vote: {
			messageId: "msg-1",
			isUpvoted: true,
		},
	},
};

/**
 * Message actions with a downvote.
 */
export const Downvoted: Story = {
	args: {
		vote: {
			messageId: "msg-1",
			isUpvoted: false,
		},
	},
};

/**
 * Loading state - actions are hidden.
 */
export const Loading: Story = {
	args: {
		isLoading: true,
	},
};

/**
 * Empty message text - actions are hidden.
 */
export const EmptyMessage: Story = {
	args: {
		messageContentToCopy: "",
	},
};

/**
 * Long message text for copy functionality.
 */
export const LongMessage: Story = {
	args: {
		messageContentToCopy: `This is a much longer message that demonstrates how the copy functionality works with substantial content. 

It includes multiple paragraphs, line breaks, and various types of content that might appear in a real chat conversation.

Here's some code:
\`\`\`javascript
function example() {
  console.log("Hello, world!");
}
\`\`\`

And here's a list:
1. First item
2. Second item  
3. Third item

This comprehensive message shows how the MessageActions component handles longer content while maintaining clean, accessible interactions.`,
	},
};
