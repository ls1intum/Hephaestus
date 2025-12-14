import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import type { ChatMessageVote } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import { Chat } from "./Chat";
import { Copilot } from "./Copilot";

/**
 * Copilot widget for displaying a Chat component in a floating bottom-right popover.
 * Provides easy access to AI assistance without disrupting the main application workflow.
 */
const meta: Meta<typeof Copilot> = {
	component: Copilot,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component: `A floating chat widget positioned in the bottom-right corner. Contains a Chat component for AI assistance.

## Portal Implementation

When documents are clicked within the chat, the Artifact components are rendered using React's \`createPortal\` to the document body. This ensures the artifacts appear as true fullscreen overlays above all other content, including the Popover container itself.

The portal implementation breaks the artifacts out of the Popover's stacking context, preventing z-index conflicts and ensuring proper fullscreen behavior.`,
			},
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof Copilot>;

export default meta;
type Story = StoryObj<typeof meta>;

// Mock conversation data from Chat stories
const CONVERSATION_MESSAGES: ChatMessage[] = [
	{
		id: "msg-1",
		role: "user",
		parts: [
			{
				type: "text",
				text: "I need help writing a poem for my daughter's birthday. She's turning 8 and loves unicorns and rainbows.",
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-2",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "What a special milestone! I'd love to help you create a magical birthday poem for your daughter. Let me craft something that captures her love for unicorns and rainbows.",
			},
			{
				type: "text",
				text: `Created document: Birthday Poem for Emma\n\nEight Candles Bright

Today you turn eight, our shining star,
With dreams that travel oh so far.
Like unicorns with silky manes,
Dancing through the rainbow lanes.

Your laughter sparkles, pure and true,
A magic only found in you.
Eight years of joy, eight years of light,
Making every day so bright.

So blow your candles, make a wish,
For all the dreams upon your list.
Our little unicorn so dear,
We celebrate another year!

Happy 8th Birthday! 🦄🌈`,
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-3",
		role: "user",
		parts: [
			{
				type: "text",
				text: "This is beautiful! Could you also create a simple birthday card message that I can write inside her card?",
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-4",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "Absolutely! Let me create a sweet and simple birthday message that would be perfect for the inside of her birthday card.",
			},
			{
				type: "text",
				text: `Created document: Birthday Card Message\n\nDear Emma,

Happy 8th Birthday to our amazing little girl!

You bring so much joy and magic into our lives every single day. Watching you grow into such a kind, creative, and wonderful person has been the greatest gift.

May this new year be filled with unicorn adventures, rainbow discoveries, and all the happiness your heart can hold.

We love you to the moon and back!

With all our love,
Mom & Dad 💕

P.S. Don't forget to make a special wish when you blow out your candles! 🎂✨`,
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
];

/**
 * Empty Copilot widget ready for new conversations.
 * Click the chat icon in the bottom-right corner to open a fresh chat interface.
 */
export const Default: Story = {
	render: () => (
		<div className="h-screen w-full bg-background relative">
			<div className="p-8">
				<h1 className="text-2xl font-bold mb-4">Copilot Widget - New Chat</h1>
				<p className="text-muted-foreground mb-8 max-w-2xl">
					This demonstrates the Copilot widget in its initial state. Look for
					the chat icon in the bottom-right corner to start a new conversation
					with the AI assistant.
				</p>

				<div className="space-y-4">
					<div className="p-6 bg-background rounded-lg border">
						<h2 className="font-semibold mb-2">Main Application Content</h2>
						<p className="text-muted-foreground">
							This is your main application content. The Copilot widget floats
							above this content and can be accessed at any time without
							disrupting the user's workflow.
						</p>
					</div>

					<div className="p-6 bg-background rounded-lg border">
						<h2 className="font-semibold mb-2">Always Available</h2>
						<p className="text-muted-foreground">
							Users can continue working while having instant access to AI
							assistance through the floating widget. Perfect for contextual
							help and quick questions.
						</p>
					</div>
				</div>
			</div>

			<Copilot onNewChat={fn()} onOpenFullChat={fn()}>
				<Chat
					id="copilot-empty"
					messages={[]}
					votes={[]}
					status="ready"
					attachments={[]}
					onMessageSubmit={fn()}
					onStop={fn()}
					onFileUpload={fn()}
					onAttachmentsChange={fn()}
					onMessageEdit={fn()}
					onCopy={fn()}
					onVote={fn()}
					scrollToBottom={fn()}
					showSuggestedActions={true}
					inputPlaceholder="Ask me anything..."
					className="h-full max-h-none"
				/>
			</Copilot>
		</div>
	),
};

/**
 * Copilot widget with an ongoing conversation.
 * Demonstrates the widget with a realistic conversation flow including document artifacts.
 */
export const WithConversation: Story = {
	render: () => (
		<div className="h-screen w-full bg-background relative">
			<div className="p-8">
				<h1 className="text-2xl font-bold mb-4">
					Copilot Widget - Active Conversation
				</h1>
				<p className="text-muted-foreground mb-8 max-w-2xl">
					This shows the Copilot widget with an ongoing conversation about
					birthday planning. Click the chat icon to see the conversation history
					and document artifacts.
				</p>

				<div className="space-y-4">
					<div className="p-6 bg-background rounded-lg border">
						<h2 className="font-semibold mb-2">Rich Conversations</h2>
						<p className="text-muted-foreground">
							The widget supports complex conversations with document creation,
							editing, and artifact management. All within a compact,
							non-intrusive interface.
						</p>
					</div>

					<div className="p-6 bg-background rounded-lg border">
						<h2 className="font-semibold mb-2">Document Artifacts</h2>
						<p className="text-muted-foreground">
							AI-generated documents can be viewed, edited, and downloaded
							directly from the chat interface. Perfect for creative writing,
							planning, and collaborative content creation.
						</p>
					</div>
				</div>
			</div>

			<Copilot onNewChat={fn()} onOpenFullChat={fn()}>
				<Chat
					id="copilot-conversation"
					messages={CONVERSATION_MESSAGES}
					votes={
						[
							{
								messageId: "msg-2",
								isUpvoted: true,
								createdAt: new Date(),
								updatedAt: new Date(),
							},
							{
								messageId: "msg-4",
								isUpvoted: true,
								createdAt: new Date(),
								updatedAt: new Date(),
							},
						] satisfies ChatMessageVote[]
					}
					status="ready"
					attachments={[]}
					onMessageSubmit={fn()}
					onStop={fn()}
					onFileUpload={fn()}
					onAttachmentsChange={fn()}
					onMessageEdit={fn()}
					onCopy={fn()}
					onVote={fn()}
					scrollToBottom={fn()}
					showSuggestedActions={true}
					inputPlaceholder="Continue the conversation..."
					className="h-full max-h-none"
				/>
			</Copilot>
		</div>
	),
};
