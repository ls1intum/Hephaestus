import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import type { ChatMessageVote, Document } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import { Artifact } from "./Artifact";

/**
 * Full-screen artifact overlay for immersive document editing experiences.
 * Combines a chat sidebar for continued conversation with a main content area
 * featuring version management, real-time editing, and document actions.
 * Supports streaming content updates and collaborative features.
 */
const meta = {
	component: Artifact,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The Artifact component creates immersive document editing experiences with integrated chat functionality, version control, and real-time collaboration features.",
			},
		},
		viewport: {
			defaultViewport: "responsive",
		},
	},
	tags: ["autodocs"],
	argTypes: {
		artifact: {
			description:
				"Core artifact data including content, title, and display properties",
			control: "object",
			table: {
				type: { summary: "UIArtifact" },
			},
		},
		documents: {
			description: "Array of document versions for version management",
			control: "object",
			table: {
				type: { summary: "Document[]" },
				defaultValue: { summary: "[]" },
			},
		},
		currentDocument: {
			description: "Currently displayed document version",
			control: "object",
			table: {
				type: { summary: "Document | undefined" },
			},
		},
		isVisible: {
			description: "Whether the artifact overlay is visible",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "true" },
			},
		},
		readonly: {
			description: "Whether the artifact is in readonly mode",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "false" },
			},
		},
		mode: {
			description: "Display mode for the artifact content",
			control: "select",
			options: ["edit", "diff"],
			table: {
				type: { summary: "'edit' | 'diff'" },
				defaultValue: { summary: "'edit'" },
			},
		},
		isCurrentVersion: {
			description: "Whether viewing the latest version of the document",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "true" },
			},
		},
		isContentDirty: {
			description: "Whether there are unsaved changes",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "false" },
			},
		},
		status: {
			description: "Current chat status affecting the interface state",
			control: "select",
			options: ["ready", "streaming", "submitted"],
			table: {
				type: { summary: "UseChatHelpers['status']" },
				defaultValue: { summary: "'ready'" },
			},
		},
		isMobile: {
			description: "Whether the interface should use mobile layout",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "false" },
			},
		},
		messages: {
			description: "Chat messages to display in the sidebar",
			control: "object",
			table: {
				type: { summary: "ChatMessage[]" },
			},
		},
		onClose: {
			description: "Handler called when artifact is closed",
			action: "artifact closed",
			table: {
				type: { summary: "() => void" },
			},
		},
		onContentSave: {
			description:
				"Handler for saving content changes (debounced automatically)",
			action: "content saved",
			table: {
				type: { summary: "(content: string) => void" },
			},
		},
		onVersionChange: {
			description: "Handler for navigating between document versions",
			action: "version changed",
			table: {
				type: {
					summary: "(type: 'next' | 'prev' | 'toggle' | 'latest') => void",
				},
			},
		},
		onMessageSubmit: {
			description: "Handler for submitting new chat messages",
			action: "message submitted",
			table: {
				type: {
					summary:
						"(data: { text: string; attachments: Attachment[] }) => void",
				},
			},
		},
	},
	args: {
		isVisible: true,
		readonly: false,
		mode: "edit",
		isCurrentVersion: true,
		isContentDirty: false,
		status: "ready" as const,
		isMobile: false,
		attachments: [],
		metadata: {},
		onClose: fn(),
		onContentSave: fn(),
		onVersionChange: fn(),
		onMessageSubmit: fn(),
		onStop: fn(),
		onFileUpload: fn(async () => []),
		onMessageEdit: fn(),
		onCopy: fn(),
		onVote: fn(),
		onDocumentClick: fn(),
		onDocumentSave: fn(),
		onMetadataUpdate: fn(),
	},
} satisfies Meta<typeof Artifact>;

export default meta;
type Story = StoryObj<typeof meta>;

// Mock data - externalized and organized
const mockArtifact = {
	title: "Project Planning Document",
	documentId: "doc-123",
	kind: "text" as const,
	content: `# Project Planning Document

## Overview
This document outlines the key milestones and deliverables for our upcoming project. The goal is to create a comprehensive roadmap that guides our team through each phase of development.

## Milestones

### Phase 1: Research & Discovery
- Conduct user interviews
- Analyze competitive landscape
- Define core requirements
- **Deadline:** End of Month 1

### Phase 2: Design & Prototyping
- Create wireframes and mockups
- Build interactive prototypes
- Gather stakeholder feedback
- **Deadline:** End of Month 2

### Phase 3: Development
- Set up development environment
- Implement core features
- Conduct testing and QA
- **Deadline:** End of Month 3

## Success Metrics
- User satisfaction score > 4.5/5
- Feature completion rate > 95%
- Zero critical bugs in production

## Risk Assessment
- Resource availability might impact timeline
- External dependencies could cause delays
- Scope creep is a potential concern`,
	isVisible: true,
	status: "idle" as const,
	boundingBox: { top: 100, left: 200, width: 300, height: 200 },
};

const mockMessages: ChatMessage[] = [
	{
		id: "msg-1",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Can you help me create a project planning document?",
			},
		],
	},
	{
		id: "msg-2",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "I'll help you create a comprehensive project planning document. Let me start by outlining the key sections and milestones for your project.",
			},
		],
	},
	{
		id: "msg-3",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Make sure to include risk assessment and success metrics",
			},
		],
	},
	{
		id: "msg-4",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "Perfect! I've added a comprehensive risk assessment section and defined clear success metrics. The document now includes measurable goals and potential challenges to watch out for.",
			},
		],
	},
];

const mockDocuments: Document[] = [
	{
		id: "doc-v1",
		title: "Project Planning Document",
		content:
			"# Project Planning Document\n\n## Overview\nInitial draft of the project plan...",
		kind: "TEXT",
		userId: "user-123",
		createdAt: new Date(Date.now() - 3600000),
	},
	{
		id: "doc-v2",
		title: "Project Planning Document",
		content: mockArtifact.content,
		kind: "TEXT",
		userId: "user-123",
		createdAt: new Date(Date.now() - 1800000),
	},
];

const mockVotes: ChatMessageVote[] = [
	{ messageId: "msg-2", isUpvoted: true, createdAt: new Date() },
];

/**
 * Standard artifact editing experience with full chat sidebar and document editing capabilities.
 * Demonstrates the default state with version management and interactive content editing.
 */
export const Default: Story = {
	args: {
		artifact: mockArtifact,
		documents: mockDocuments,
		currentDocument: mockDocuments[1],
		currentVersionIndex: 1,
		messages: mockMessages,
		votes: mockVotes,
	},
};

/**
 * Real-time streaming content generation showing dynamic updates.
 * Displays the artifact receiving live content updates with appropriate UI feedback.
 */
export const Streaming: Story = {
	args: {
		...Default.args,
		artifact: {
			...mockArtifact,
			status: "streaming",
			content:
				"# Project Planning Document\n\n## Overview\nThis document outlines...",
		},
		status: "streaming",
		isContentDirty: true,
	},
};

/**
 * Version comparison mode for reviewing document history.
 * Shows how users can navigate between different document versions and compare changes.
 */
export const VersionHistory: Story = {
	args: {
		...Default.args,
		currentVersionIndex: 0,
		isCurrentVersion: false,
		currentDocument: mockDocuments[0],
		artifact: {
			...mockArtifact,
			content: mockDocuments[0].content,
		},
	},
};

/**
 * Read-only view preventing any edits or interactions.
 * Useful for sharing documents or when user permissions restrict editing.
 */
export const Readonly: Story = {
	args: {
		...Default.args,
		readonly: true,
	},
};

/**
 * Mobile-optimized layout for smaller screens.
 * Demonstrates responsive design adaptations for mobile devices.
 */
export const Mobile: Story = {
	args: {
		...Default.args,
		isMobile: true,
	},
	parameters: {
		viewport: { defaultViewport: "mobile2" },
		docs: {
			description: {
				story:
					"Mobile layout removes the sidebar and optimizes the interface for touch interactions.",
			},
		},
	},
};

/**
 * Minimal content state for testing empty or loading scenarios.
 * Shows how the component handles sparse content and initial document creation.
 */
export const EmptyDocument: Story = {
	args: {
		...Default.args,
		artifact: {
			...mockArtifact,
			content: "# New Document\n\nStart writing...",
		},
		messages: [
			{
				id: "msg-1",
				role: "user",
				parts: [{ type: "text", text: "Create a new document" }],
			},
		],
		documents: [],
		currentDocument: undefined,
	},
};

/**
 * Extended conversation with substantial message history.
 * Tests performance and UI behavior with large amounts of chat data.
 */
export const ExtensiveChat: Story = {
	args: {
		...Default.args,
		messages: [
			...mockMessages,
			...Array.from({ length: 8 }, (_, i) => ({
				id: `msg-extra-${i}`,
				role: (i % 2 === 0 ? "user" : "assistant") as "user" | "assistant",
				parts: [
					{
						type: "text" as const,
						text: `Message ${i + 5}: ${i % 2 === 0 ? `Can you add more details about section ${i + 1}?` : "I've updated the document with more comprehensive information about that topic."}`,
					},
				],
			})),
		],
	},
};
