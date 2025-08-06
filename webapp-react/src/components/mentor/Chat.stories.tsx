import type { ChatMessageVote } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { Chat } from "./Chat";

/**
 * Chat component providing a complete conversational AI interface with advanced artifact support.
 *
 * Features comprehensive message handling, real-time input capabilities, file attachment support,
 * and seamless transitions between standard chat and artifact modes. The component manages its
 * internal artifact state while providing clean external APIs for data integration and user actions.
 *
 * Key capabilities:
 * - Multi-modal message rendering (text, files, tool outputs)
 * - Real-time streaming with status indicators
 * - Interactive document artifacts with click-to-expand functionality
 * - Voting and feedback mechanisms
 * - Comprehensive accessibility support
 * - Flexible layout modes (standard, readonly, fullscreen)
 */
const meta = {
	component: Chat,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"Primary conversational interface for AI-powered interactions with support for artifacts, attachments, and real-time streaming.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		id: {
			description:
				"Unique identifier for the chat session, used for persistence and analytics",
			control: "text",
		},
		messages: {
			description:
				"Chronologically ordered array of chat messages with full conversation history",
			control: "object",
		},
		votes: {
			description:
				"User feedback votes associated with specific messages for quality improvement",
			control: "object",
		},
		status: {
			description: "Current operational state of the chat interface",
			control: "select",
			options: ["submitted", "streaming", "ready", "error"],
		},
		readonly: {
			description:
				"Disables input interface for viewing historical conversations",
			control: "boolean",
		},
		attachments: {
			description:
				"Currently attached files awaiting submission with the next message",
			control: "object",
		},
		showSuggestedActions: {
			description:
				"Displays contextual suggested actions to guide user interaction",
			control: "boolean",
		},
		inputPlaceholder: {
			description: "Instructional text displayed in the message input field",
			control: "text",
		},
		disableAttachments: {
			description:
				"Removes file attachment functionality for security-restricted environments",
			control: "boolean",
		},
	},
	args: {
		id: "demo-chat-session",
		messages: [],
		votes: [],
		status: "ready",
		readonly: false,
		isAtBottom: true,
		attachments: [],
		// Event handlers with realistic implementations for demo purposes
		onMessageSubmit: fn((data: { text: string; attachments: unknown[] }) =>
			console.log(
				"Message submitted:",
				data.text,
				"with attachments:",
				data.attachments,
			),
		),
		onStop: fn(() => console.log("Streaming stopped by user")),
		onFileUpload: fn(async (files: File[]) => {
			console.log(
				"Files uploaded:",
				files.map((f) => f.name),
			);
			return [];
		}),
		onAttachmentsChange: fn((attachments: unknown[]) =>
			console.log("Attachments changed:", attachments),
		),
		onMessageEdit: fn((messageId: string, newContent: string) =>
			console.log("Message edited:", messageId, newContent),
		),
		onCopy: fn((content: string) => console.log("Content copied:", content)),
		onVote: fn((messageId: string, isUpvoted: boolean) =>
			console.log("Vote cast:", messageId, isUpvoted ? "upvote" : "downvote"),
		),
		onSuggestedAction: fn((action: string) =>
			console.log("Suggested action selected:", action),
		),
		onDocumentClick: fn((documentId: string, boundingRect: DOMRect) =>
			console.log("Document artifact clicked:", documentId, boundingRect),
		),
		scrollToBottom: fn(() => console.log("Scroll to bottom triggered")),
		showSuggestedActions: true,
		inputPlaceholder: "Ask me anything...",
		disableAttachments: false,
	},
} satisfies Meta<typeof Chat>;

export default meta;
type Story = StoryObj<typeof meta>;

// =============================================================================
// MOCK DATA
// =============================================================================

/**
 * Realistic conversation data demonstrating the chat's artifact creation and editing capabilities.
 * This scenario shows a parent planning their child's birthday, showcasing multiple document types
 * and the iterative improvement process typical in real AI conversations.
 */
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
				type: "tool-createDocument",
				toolCallId: "tool-1",
				state: "output-available",
				input: {
					title: "Birthday Poem for Emma",
					kind: "text" as const,
				},
				output: {
					id: "doc-1",
					title: "Birthday Poem for Emma",
					content: `Eight Candles Bright

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
					kind: "TEXT" as const,
					createdAt: new Date().toISOString(),
					updatedAt: new Date().toISOString(),
				},
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
				type: "tool-createDocument",
				toolCallId: "tool-2",
				state: "output-available",
				input: {
					title: "Birthday Card Message",
					kind: "text" as const,
				},
				output: {
					id: "doc-2",
					title: "Birthday Card Message",
					content: `Dear Emma,

Happy 8th Birthday to our amazing little girl!

You bring so much joy and magic into our lives every single day. Watching you grow into such a kind, creative, and wonderful person has been the greatest gift.

May this new year be filled with unicorn adventures, rainbow discoveries, and all the happiness your heart can hold.

We love you to the moon and back!

With all our love,
Mom & Dad 💕

P.S. Don't forget to make a special wish when you blow out your candles! 🎂✨`,
					kind: "TEXT" as const,
					createdAt: new Date().toISOString(),
					updatedAt: new Date().toISOString(),
				},
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-5",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Perfect! One more thing - could you update the poem to mention that she's starting 3rd grade soon? I think that would make it even more special.",
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-6",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "What a great idea! Adding that milestone will make the poem even more personal and meaningful. Let me update it to celebrate both her birthday and this exciting new chapter.",
			},
			{
				type: "tool-updateDocument",
				toolCallId: "tool-3",
				state: "output-available",
				input: {
					id: "doc-1",
					description: "Add reference to starting 3rd grade",
				},
				output: {
					id: "doc-1",
					title: "Birthday Poem for Emma",
					content: `Eight Candles Bright

Today you turn eight, our shining star,
With dreams that travel oh so far.
Like unicorns with silky manes,
Dancing through the rainbow lanes.

Third grade awaits with books to read,
New friends to meet, new goals to lead.
Your laughter sparkles, pure and true,
A magic only found in you.

Eight years of joy, eight years of light,
Making every day so bright.
Growing bigger, growing smart,
With such a loving, caring heart.

So blow your candles, make a wish,
For all the dreams upon your list.
Our little unicorn so dear,
We celebrate another year!

Happy 8th Birthday! 🦄🌈📚`,
					kind: "TEXT" as const,
					createdAt: new Date().toISOString(),
					updatedAt: new Date().toISOString(),
				},
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-7",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Wonderful! One last request - can you help me create a simple shopping list for her party? We're having about 10 kids over.",
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-8",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "Of course! Let me create a practical shopping list for a fun 8th birthday party with 10 kids. I'll include everything from decorations to food and party favors.",
			},
			{
				type: "tool-createDocument",
				toolCallId: "tool-4",
				state: "output-available",
				input: {
					title: "Birthday Party Shopping List",
					kind: "text" as const,
				},
				output: {
					id: "doc-3",
					title: "Birthday Party Shopping List",
					content: `Emma's 8th Birthday Party Shopping List
🦄 For 10 kids + family 🌈

## DECORATIONS
- [ ] Unicorn/rainbow themed tablecloth
- [ ] Colorful balloons (pink, purple, blue, rainbow)
- [ ] Birthday banner
- [ ] Paper plates and cups (unicorn theme)
- [ ] Napkins
- [ ] Plastic forks and spoons

## FOOD & DRINKS
- [ ] Birthday cake (or ingredients to make one)
- [ ] Juice boxes or punch
- [ ] Pizza (2-3 large pizzas)
- [ ] Fresh fruit (strawberries, grapes, watermelon)
- [ ] Veggie tray with ranch dip
- [ ] Chips and pretzels
- [ ] Ice cream (vanilla and chocolate)

## PARTY ACTIVITIES
- [ ] Party games supplies
- [ ] Craft materials (if doing a craft activity)
- [ ] Music playlist ready

## PARTY FAVORS
- [ ] Small gift bags
- [ ] Stickers (unicorn/rainbow theme)
- [ ] Small toys or trinkets
- [ ] Candy or small treats

## DON'T FORGET
- [ ] Candles (number 8!)
- [ ] Lighter/matches
- [ ] Camera for photos
- [ ] Thank you cards

**Budget estimate: $80-120 for everything!** 🎉`,
					kind: "TEXT" as const,
					createdAt: new Date().toISOString(),
					updatedAt: new Date().toISOString(),
				},
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-9",
		role: "user",
		parts: [
			{
				type: "text",
				text: "This is so helpful! Thank you for making Emma's birthday planning so much easier. You've created everything I need! 🎉",
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
	{
		id: "msg-10",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "You're so welcome! I'm thrilled I could help make Emma's 8th birthday extra special. She's going to love the personalized poem, and it sounds like you have everything planned for a magical celebration. I hope she has the most wonderful day filled with unicorns, rainbows, and lots of birthday joy! 🦄🌈🎂✨",
			},
		],
		metadata: {
			createdAt: new Date().toISOString(),
		},
	},
];

/**
 * User feedback votes demonstrating the voting system on helpful responses.
 * Typically votes are cast on messages that provide significant value or solve problems effectively.
 */
const CONVERSATION_VOTES: ChatMessageVote[] = [
	{
		messageId: "msg-2",
		isUpvoted: true,
	},
	{
		messageId: "msg-6",
		isUpvoted: true,
	},
	{
		messageId: "msg-8",
		isUpvoted: true,
	},
];

/**
 * Sample file attachments for testing upload and display functionality.
 * Represents typical files users might attach to provide context for their requests.
 */
const SAMPLE_ATTACHMENTS = [
	{
		name: "party-requirements.md",
		url: "https://example.com/party-requirements.md",
		contentType: "text/markdown",
	},
	{
		name: "budget-constraints.pdf",
		url: "https://example.com/budget.pdf",
		contentType: "application/pdf",
	},
];

// =============================================================================
// STORIES
// =============================================================================

/**
 * Complete conversational experience demonstrating end-to-end functionality.
 *
 * This story showcases a realistic AI-assisted creative planning session, featuring:
 * - Multi-turn conversation with natural flow and context retention
 * - Document artifact creation (poems, messages, lists) with real-world utility
 * - Document editing and iteration based on user feedback
 * - User voting on helpful responses to demonstrate feedback mechanisms
 * - Click-to-expand artifact functionality for detailed document interaction
 *
 * Click on any document preview to trigger artifact mode and explore the full content.
 * This demonstrates the seamless transition between conversational and document-focused views.
 */
export const FullConversationDemo: Story = {
	args: {
		messages: CONVERSATION_MESSAGES,
		votes: CONVERSATION_VOTES,
		attachments: [],
		onDocumentClick: fn((documentId: string, boundingBox: DOMRect) => {
			console.log("Document clicked:", documentId, boundingBox);
			// In production, this would trigger external data fetching
			// The component manages artifact state transitions internally
		}),
	},
};

/**
 * Clean slate interface for new conversations.
 *
 * Demonstrates the initial state with welcome messaging and input readiness.
 * Perfect for testing onboarding experience and first-time user interactions.
 */
export const EmptyState: Story = {
	args: {
		messages: [],
		attachments: [],
	},
};

/**
 * Active conversation with pending file attachments.
 *
 * Shows the interface state when users have attached files but haven't sent the message yet.
 * Useful for testing attachment display, removal, and submission workflows.
 */
export const WithPendingAttachments: Story = {
	args: {
		messages: CONVERSATION_MESSAGES.slice(0, 2),
		attachments: SAMPLE_ATTACHMENTS,
	},
};

/**
 * Real-time streaming response in progress.
 *
 * Demonstrates the interface during active AI response generation with streaming indicators.
 * Critical for testing loading states, stop functionality, and real-time message updates.
 */
export const StreamingResponse: Story = {
	args: {
		messages: [
			...CONVERSATION_MESSAGES.slice(0, 3),
			{
				id: "msg-streaming",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "I'm crafting a personalized birthday card message that will capture your daughter's special day...",
					},
				],
				metadata: {
					createdAt: new Date().toISOString(),
				},
			},
		],
		status: "streaming",
	},
};

/**
 * Archive view for completed conversations.
 *
 * Read-only mode without input interface, ideal for viewing conversation history,
 * customer service transcripts, or educational content. Maintains full voting and
 * artifact functionality while preventing new message creation.
 */
export const ReadOnlyArchive: Story = {
	args: {
		messages: CONVERSATION_MESSAGES,
		votes: CONVERSATION_VOTES,
		readonly: true,
	},
};

/**
 * Security-restricted environment configuration.
 *
 * Demonstrates the interface with file attachments disabled for compliance with
 * enterprise security policies or environments where file uploads are prohibited.
 * Input remains fully functional for text-based interactions.
 */
export const SecureMode: Story = {
	args: {
		messages: CONVERSATION_MESSAGES.slice(0, 4),
		disableAttachments: true,
		inputPlaceholder: "Send a message (file attachments disabled)...",
	},
};
