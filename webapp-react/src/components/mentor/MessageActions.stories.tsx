import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";

import type { UIMessage } from "@ai-sdk/react";
import { MessageActions } from "./MessageActions";

const sampleMessage: UIMessage = {
	id: "1",
	role: "assistant",
	parts: [
		{
			type: "text",
			text: "Clean code is essential for maintainable software. Here are the key principles that every developer should follow to write better, more readable code.",
			state: "done",
		},
	],
};

const meta: Meta<typeof MessageActions> = {
	title: "Components/Mentor/MessageActions",
	component: MessageActions,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Message actions component providing copy and regenerate functionality. Appears on hover for assistant messages with smooth animations and tooltips.",
			},
		},
	},
	argTypes: {
		message: {
			description: "UIMessage object containing the message data",
		},
		onRegenerate: {
			action: "regenerate",
			description: "Called when user requests message regeneration",
		},
		canRegenerate: {
			control: "boolean",
			description: "Whether the regenerate button should be shown",
		},
	},
	args: {
		message: sampleMessage,
		onRegenerate: fn(),
		canRegenerate: true,
	},
	decorators: [
		(Story) => (
			<div className="relative p-8 bg-muted/30 rounded-lg">
				<div className="relative bg-background p-4 rounded border group hover:bg-muted/20 transition-colors">
					<p className="text-sm text-muted-foreground mb-4">
						Hover over this message area to see the actions appear
					</p>
					<p className="text-foreground">
						{sampleMessage.parts[0]?.type === "text"
							? sampleMessage.parts[0].text
							: "Message content"}
					</p>
					<Story />
				</div>
			</div>
		),
	],
} satisfies Meta<typeof MessageActions>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default message actions with both copy and regenerate buttons.
 */
export const Default: Story = {
	args: {},
};

/**
 * Actions with only copy button (regenerate disabled).
 */
export const CopyOnly: Story = {
	args: {
		canRegenerate: false,
	},
};

/**
 * Actions with regenerate enabled.
 */
export const WithRegenerate: Story = {
	args: {
		canRegenerate: true,
	},
};

/**
 * Interactive example showing copy feedback.
 */
export const InteractiveCopy: Story = {
	render: (args) => (
		<div className="space-y-4">
			<p className="text-sm text-muted-foreground">
				Hover and click the copy button to test functionality
			</p>
			<div className="relative bg-background p-4 rounded border group hover:bg-muted/20 transition-colors">
				<p className="text-foreground">
					Clean code is essential for maintainable software. Here are the key
					principles...
				</p>
				<MessageActions
					{...args}
					message={sampleMessage}
					canRegenerate={false}
				/>
			</div>
		</div>
	),
};

/**
 * Interactive example showing regenerate functionality.
 */
export const InteractiveRegenerate: Story = {
	render: (args) => (
		<div className="space-y-4">
			<p className="text-sm text-muted-foreground">
				Hover and click the regenerate button to test functionality
			</p>
			<div className="relative bg-background p-4 rounded border group hover:bg-muted/20 transition-colors">
				<p className="text-foreground">
					Clean code is essential for maintainable software. Here are the key
					principles...
				</p>
				<MessageActions
					{...args}
					message={sampleMessage}
					canRegenerate={true}
				/>
			</div>
		</div>
	),
};

/**
 * Multiple message scenarios showing different action states.
 */
export const MessageScenarios: Story = {
	render: () => (
		<div className="space-y-8">
			<div className="space-y-2">
				<h3 className="text-sm font-medium">First Message - Can Regenerate</h3>
				<div className="relative bg-background p-4 rounded border group hover:bg-muted/20 transition-colors">
					<p className="text-sm">This is the first assistant message...</p>
					<MessageActions
						message={sampleMessage}
						onRegenerate={fn()}
						canRegenerate={true}
					/>
				</div>
			</div>

			<div className="space-y-2">
				<h3 className="text-sm font-medium">Middle Message - Copy Only</h3>
				<div className="relative bg-background p-4 rounded border group hover:bg-muted/20 transition-colors">
					<p className="text-sm">
						This is a middle message in the conversation...
					</p>
					<MessageActions
						message={sampleMessage}
						onRegenerate={fn()}
						canRegenerate={false}
					/>
				</div>
			</div>

			<div className="space-y-2">
				<h3 className="text-sm font-medium">Last Message - Can Regenerate</h3>
				<div className="relative bg-background p-4 rounded border group hover:bg-muted/20 transition-colors">
					<p className="text-sm">
						This is the most recent assistant message...
					</p>
					<MessageActions
						message={sampleMessage}
						onRegenerate={fn()}
						canRegenerate={true}
					/>
				</div>
			</div>
		</div>
	),
};

/**
 * Hover behavior demonstration.
 */
export const HoverBehavior: Story = {
	render: () => (
		<div className="space-y-4">
			<p className="text-sm text-muted-foreground">
				Hover over the message below to see the actions appear
			</p>
			<div className="relative bg-background p-6 rounded border group hover:bg-muted/20 transition-colors">
				<p className="text-foreground mb-2">
					Understanding React's useEffect hook is crucial for managing side
					effects in functional components.
				</p>
				<p className="text-muted-foreground text-sm">
					The hook allows you to perform side effects such as data fetching,
					subscriptions, or manually changing the DOM.
				</p>
				<MessageActions
					message={sampleMessage}
					onRegenerate={fn()}
					canRegenerate={true}
				/>
			</div>
		</div>
	),
};
