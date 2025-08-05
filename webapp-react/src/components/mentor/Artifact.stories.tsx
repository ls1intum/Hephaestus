import type { ChatMessageVote, Document } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { Artifact } from "./Artifact";

/**
 * Artifact component provides a full-screen overlay for displaying and editing documents
 * generated during conversations. Features a chat sidebar for continued interaction
 * and a main content area with version management, editing capabilities, and document actions.
 */
const meta = {
	component: Artifact,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The Artifact component creates immersive document editing experiences with integrated chat functionality.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		artifact: {
			description:
				"Core artifact data including content, title, and display properties",
		},
		documents: {
			description: "Array of document versions for version management",
		},
		currentDocument: {
			description: "Currently displayed document version",
		},
		isVisible: {
			control: "boolean",
			description: "Whether the artifact overlay is visible",
		},
		readonly: {
			control: "boolean",
			description: "Whether the artifact is in readonly mode",
		},
		mode: {
			control: { type: "radio" },
			options: ["edit", "diff"],
			description: "Display mode for the artifact content",
		},
		isCurrentVersion: {
			control: "boolean",
			description: "Whether viewing the latest version of the document",
		},
		isContentDirty: {
			control: "boolean",
			description: "Whether there are unsaved changes",
		},
		status: {
			control: { type: "radio" },
			options: ["idle", "streaming", "ready"],
			description: "Current chat status",
		},
		onClose: {
			description: "Handler called when artifact is closed",
		},
		onContentSave: {
			description: "Handler for saving content changes with debounce option",
		},
		onVersionChange: {
			description: "Handler for navigating between document versions",
		},
		onMessageSubmit: {
			description: "Handler for submitting new chat messages",
		},
	},
	args: {
		isVisible: true,
		readonly: false,
		mode: "edit",
		isCurrentVersion: true,
		isContentDirty: false,
		status: "streaming" as const,
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

// Mock data
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
	boundingBox: {
		top: 100,
		left: 200,
		width: 300,
		height: 200,
	},
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
	{
		messageId: "msg-2",
		isUpvoted: true,
		createdAt: new Date(),
	},
];

/**
 * Default artifact view showing a text document with full editing capabilities.
 * Demonstrates the standard layout with chat sidebar and document editing area.
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
 * Streaming state showing real-time content generation.
 * The artifact displays with streaming status and appropriate UI feedback.
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
 * Historical version view showing previous document state.
 * Demonstrates version navigation and comparison capabilities.
 */
export const HistoricalVersion: Story = {
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
 * Readonly mode preventing any edits or interactions.
 * Useful for viewing completed documents or when permissions are restricted.
 */
export const Readonly: Story = {
	args: {
		...Default.args,
		readonly: true,
	},
};

/**
 * Mobile responsive layout optimized for smaller screens.
 * Shows how the component adapts its layout for mobile devices.
 */
export const Mobile: Story = {
	args: {
		...Default.args,
		isMobile: true,
	},
	parameters: {
		viewport: {
			defaultViewport: "mobile2",
		},
	},
};

/**
 * Empty state with minimal content to test loading and initial states.
 * Demonstrates how the component handles sparse content.
 */
export const MinimalContent: Story = {
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
 * Diff mode showing changes between document versions.
 * Highlights the difference viewing capability for version comparison.
 */
export const DiffMode: Story = {
	args: {
		...Default.args,
		mode: "diff",
		currentVersionIndex: 0,
		isCurrentVersion: false,
	},
};

/**
 * Long conversation with extensive chat history.
 * Tests the component's performance with substantial message data.
 */
export const ExtensiveChat: Story = {
	args: {
		...Default.args,
		messages: [
			...mockMessages,
			...Array.from({ length: 10 }, (_, i) => ({
				id: `msg-extra-${i}`,
				role: (i % 2 === 0 ? "user" : "assistant") as "user" | "assistant",
				parts: [
					{
						type: "text" as const,
						text: `This is message ${i + 5} in our conversation about the project planning document. ${i % 2 === 0 ? `What about adding more details to section ${i + 1}?` : "Great suggestion! I've updated the document to include more comprehensive information about that topic."}`,
					},
				],
			})),
		],
	},
};
