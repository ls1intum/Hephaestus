import type { ChatMessageVote, Document } from "@/api/types.gen";
import type { Attachment, ChatMessage } from "@/lib/types";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import type { UIArtifact } from "./Artifact";
import { Chat } from "./Chat";

/**
 * Chat component provides a complete conversational interface with integrated artifact support.
 * This presentational component handles message display, input interactions, and artifact overlays
 * while delegating all business logic to parent containers through clean handler props.
 *
 * Key Features:
 * - Message thread display with voting and editing capabilities
 * - Rich multimodal input with file uploads and suggested actions
 * - Integrated artifact overlay for document editing and collaboration
 * - Responsive design adapting to mobile and desktop layouts
 * - Comprehensive accessibility support and keyboard navigation
 */
const meta = {
	component: Chat,
	parameters: {
		layout: "fullscreen",
		docs: {
			description: {
				component:
					"The Chat component creates comprehensive conversational experiences with integrated document editing capabilities, file uploads, and real-time collaboration features.",
			},
		},
		viewport: {
			defaultViewport: "responsive",
		},
	},
	tags: ["autodocs"],
	argTypes: {
		id: {
			description: "Unique identifier for the chat session",
			control: "text",
			table: {
				type: { summary: "string" },
			},
		},
		messages: {
			description: "Array of chat messages to display",
			control: "object",
			table: {
				type: { summary: "ChatMessage[]" },
			},
		},
		votes: {
			description: "Array of votes for messages",
			control: "object",
			table: {
				type: { summary: "ChatMessageVote[]" },
				defaultValue: { summary: "[]" },
			},
		},
		status: {
			description: "Current chat status affecting the interface state",
			control: "select",
			options: ["ready", "streaming", "submitted", "error"],
			table: {
				type: { summary: "UseChatHelpers['status']" },
				defaultValue: { summary: "'ready'" },
			},
		},
		readonly: {
			description: "Whether the interface is in readonly mode",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "false" },
			},
		},
		artifact: {
			description: "Current artifact state for overlay display",
			control: "object",
			table: {
				type: { summary: "UIArtifact | undefined" },
			},
		},
		attachments: {
			description: "Current input attachments",
			control: "object",
			table: {
				type: { summary: "Attachment[]" },
				defaultValue: { summary: "[]" },
			},
		},
		showSuggestedActions: {
			description: "Whether to show suggested actions in input",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "false" },
			},
		},
		disableAttachments: {
			description: "Whether to disable attachment functionality",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "false" },
			},
		},
		inputPlaceholder: {
			description: "Placeholder text for input field",
			control: "text",
			table: {
				type: { summary: "string" },
				defaultValue: { summary: "'Send a message...'" },
			},
		},
		// Event handlers
		onMessageSubmit: {
			description: "Handler for message submission",
			action: "message submitted",
			table: {
				type: {
					summary:
						"(data: { text: string; attachments: Attachment[] }) => void",
				},
			},
		},
		onStop: {
			description: "Handler for stopping current operation",
			action: "operation stopped",
			table: {
				type: { summary: "() => void" },
			},
		},
		onFileUpload: {
			description: "Handler for file uploads",
			action: "files uploaded",
			table: {
				type: { summary: "(files: File[]) => Promise<Attachment[]>" },
			},
		},
		onVote: {
			description: "Handler for message voting",
			action: "message voted",
			table: {
				type: { summary: "(messageId: string, isUpvote: boolean) => void" },
			},
		},
		onMessageEdit: {
			description: "Handler for message editing",
			action: "message edited",
			table: {
				type: { summary: "(messageId: string, content: string) => void" },
			},
		},
		onSuggestedAction: {
			description: "Handler for suggested action clicks",
			action: "suggested action clicked",
			table: {
				type: { summary: "(actionMessage: string) => void" },
			},
		},
	},
	args: {
		id: "chat-123",
		messages: [],
		votes: [],
		status: "ready",
		readonly: false,
		isAtBottom: true,
		attachments: [],
		showSuggestedActions: true,
		disableAttachments: false,
		inputPlaceholder: "Send a message...",
		// Event handlers with mock implementations
		onMessageSubmit: fn(),
		onStop: fn(),
		onFileUpload: fn(async () => []),
		onAttachmentsChange: fn(),
		onMessageEdit: fn(),
		onCopy: fn(),
		onVote: fn(),
		onDocumentClick: fn(),
		onDocumentSave: fn(),
		onSuggestedAction: fn(),
		scrollToBottom: fn(),
	},
	decorators: [
		(Story) => (
			<div className="w-full h-screen">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof Chat>;

export default meta;
type Story = StoryObj<typeof meta>;

// Mock data - organized and reusable
const mockMessages: ChatMessage[] = [
	{
		id: "msg-1",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Can you help me understand React component patterns?",
			},
		],
		metadata: {
			createdAt: new Date("2024-01-15T10:30:00Z").toISOString(),
		},
	},
	{
		id: "msg-2",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: 'I\'d be happy to help you understand React component patterns! There are several important patterns that can make your components more maintainable and reusable:\n\n## 1. Presentational vs Container Components\n- **Presentational components** focus purely on how things look\n- **Container components** handle data fetching and state management\n\n## 2. Compound Components\nThis pattern allows you to create components that work together, like:\n```jsx\n<Select>\n  <Select.Trigger />\n  <Select.Content>\n    <Select.Item value="option1">Option 1</Select.Item>\n  </Select.Content>\n</Select>\n```\n\n## 3. Render Props\nShare code between components using a prop whose value is a function.\n\nWould you like me to dive deeper into any of these patterns?',
			},
		],
		metadata: {
			createdAt: new Date("2024-01-15T10:31:00Z").toISOString(),
		},
	},
	{
		id: "msg-3",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Yes, could you create a practical example of the presentational vs container pattern?",
			},
		],
		metadata: {
			createdAt: new Date("2024-01-15T10:32:00Z").toISOString(),
		},
	},
];

const mockLongConversation: ChatMessage[] = [
	...mockMessages,
	{
		id: "msg-4",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "Absolutely! Let me create a practical example that demonstrates the presentational vs container pattern clearly.\n\nImagine we're building a user profile component. Here's how we'd separate concerns:",
			},
		],
		metadata: {
			createdAt: new Date("2024-01-15T10:33:00Z").toISOString(),
		},
	},
	{
		id: "msg-5",
		role: "user",
		parts: [
			{
				type: "text",
				text: "That makes sense! What about error handling in this pattern?",
			},
		],
		metadata: {
			createdAt: new Date("2024-01-15T10:35:00Z").toISOString(),
		},
	},
	{
		id: "msg-6",
		role: "assistant",
		parts: [
			{
				type: "text",
				text: "Great question! Error handling is typically managed by the container component, which then passes error states down to presentational components as props.\n\nHere's how you might handle errors in our user profile example:\n\n```tsx\n// Container Component\nfunction UserProfileContainer({ userId }: { userId: string }) {\n  const [user, setUser] = useState<User | null>(null);\n  const [error, setError] = useState<string | null>(null);\n  const [loading, setLoading] = useState(true);\n\n  useEffect(() => {\n    fetchUser(userId)\n      .then(setUser)\n      .catch(err => setError(err.message))\n      .finally(() => setLoading(false));\n  }, [userId]);\n\n  return (\n    <UserProfile \n      user={user} \n      error={error} \n      loading={loading}\n      onRetry={() => {\n        setError(null);\n        setLoading(true);\n        fetchUser(userId).then(setUser).catch(err => setError(err.message));\n      }}\n    />\n  );\n}\n```\n\nThe presentational component just needs to know how to display different states.",
			},
		],
		metadata: {
			createdAt: new Date("2024-01-15T10:36:00Z").toISOString(),
		},
	},
];

const mockVotes: ChatMessageVote[] = [
	{
		messageId: "msg-2",
		isUpvoted: true,
		createdAt: new Date("2024-01-15T10:32:00Z"),
		updatedAt: new Date("2024-01-15T10:32:00Z"),
	},
	{
		messageId: "msg-4",
		isUpvoted: false,
		createdAt: new Date("2024-01-15T10:34:00Z"),
		updatedAt: new Date("2024-01-15T10:34:00Z"),
	},
];

const mockAttachments: Attachment[] = [
	{
		name: "component-diagram.png",
		url: "https://picsum.photos/seed/diagram/400/300",
		contentType: "image/png",
	},
	{
		name: "react-patterns.pdf",
		url: "https://example.com/react-patterns.pdf",
		contentType: "application/pdf",
	},
];

const mockArtifact: UIArtifact = {
	title: "React Component Patterns Guide",
	documentId: "doc-patterns-123",
	kind: "text",
	content: `# React Component Patterns Guide

## 1. Presentational vs Container Components

### Presentational Components
- Focus on **how things look**
- Receive data and callbacks exclusively via props
- Rarely have their own state (except UI state)
- Don't specify how data is loaded or mutated
- Are written as functional components

### Container Components  
- Focus on **how things work**
- Provide data and behavior to presentational components
- Call actions and provide these as callbacks to presentational components
- Are often stateful and serve as data sources
- Are usually generated using higher order components

## 2. Example Implementation

### Presentational Component
\`\`\`tsx
interface UserProfileProps {
  user: User | null;
  error: string | null;
  loading: boolean;
  onRetry: () => void;
}

function UserProfile({ user, error, loading, onRetry }: UserProfileProps) {
  if (loading) return <div>Loading...</div>;
  if (error) return <div>Error: {error} <button onClick={onRetry}>Retry</button></div>;
  if (!user) return <div>No user found</div>;
  
  return (
    <div className="user-profile">
      <img src={user.avatar} alt={user.name} />
      <h2>{user.name}</h2>
      <p>{user.email}</p>
    </div>
  );
}
\`\`\`

### Container Component
\`\`\`tsx
function UserProfileContainer({ userId }: { userId: string }) {
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchUserData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const userData = await fetchUser(userId);
      setUser(userData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    fetchUserData();
  }, [fetchUserData]);

  return (
    <UserProfile 
      user={user}
      error={error}
      loading={loading}
      onRetry={fetchUserData}
    />
  );
}
\`\`\`

## Benefits

1. **Separation of Concerns**: Clear division between presentation and business logic
2. **Reusability**: Presentational components can be reused with different data sources
3. **Testability**: Each component type can be tested in isolation
4. **Maintainability**: Changes to business logic don't affect presentation and vice versa`,
	isVisible: true,
	status: "idle",
	boundingBox: { top: 100, left: 200, width: 600, height: 400 },
};

const mockDocuments: Document[] = [
	{
		id: "doc-patterns-123",
		title: "React Component Patterns Guide",
		content: mockArtifact.content,
		kind: "TEXT",
		userId: "user-123",
		createdAt: new Date("2024-01-15T10:33:00Z"),
	},
];

/**
 * Empty chat state - perfect starting point for new conversations.
 * Shows greeting message and suggested actions to guide users.
 */
export const Empty: Story = {
	args: {
		messages: [],
		showSuggestedActions: true,
		onSuggestedAction: fn((action) => {
			console.log("Suggested action clicked:", action);
		}),
	},
};

/**
 * Basic conversation with a few exchanges between user and assistant.
 * Demonstrates the core chat functionality without artifacts.
 */
export const BasicConversation: Story = {
	args: {
		messages: mockMessages,
		votes: mockVotes.slice(0, 1), // Just one vote
	},
};

/**
 * Extended conversation showing multiple message exchanges.
 * Includes voting functionality and demonstrates scrolling behavior.
 */
export const LongConversation: Story = {
	args: {
		messages: mockLongConversation,
		votes: mockVotes,
		isAtBottom: false, // Show scroll-to-bottom behavior
	},
};

/**
 * Chat with file attachments in the input area.
 * Shows how the interface handles multimedia content and file previews.
 */
export const WithAttachments: Story = {
	args: {
		messages: mockMessages,
		attachments: mockAttachments,
		onFileUpload: fn().mockImplementation(async (files: File[]) => {
			// Simulate upload processing
			return files.map((file: File) => ({
				name: file.name,
				url: URL.createObjectURL(file),
				contentType: file.type,
			}));
		}),
	},
};

/**
 * Chat in submission state showing the stop button and thinking indicator.
 * Demonstrates the interface during active AI processing.
 */
export const Submitting: Story = {
	args: {
		messages: [
			...mockMessages,
			{
				id: "msg-temp",
				role: "user",
				parts: [
					{
						type: "text",
						text: "Can you explain the compound component pattern?",
					},
				],
				metadata: { createdAt: new Date().toISOString() },
			},
		],
		status: "submitted",
	},
};

/**
 * Read-only chat interface where users can view but not interact.
 * Useful for displaying conversation history or archived chats.
 */
export const ReadOnly: Story = {
	args: {
		messages: mockLongConversation,
		votes: mockVotes,
		readonly: true,
		showSuggestedActions: false,
	},
};

/**
 * Chat with artifact overlay active.
 * Demonstrates the integrated document editing experience alongside conversation.
 */
export const WithArtifact: Story = {
	args: {
		messages: mockLongConversation,
		votes: mockVotes,
		artifact: mockArtifact,
		documents: mockDocuments,
		currentDocument: mockDocuments[0],
		isCurrentVersion: true,
		onArtifactClose: fn(),
		onArtifactContentSave: fn(),
		onArtifactVersionChange: fn(),
		onArtifactMetadataUpdate: fn(),
	},
};

/**
 * Chat with artifact in editing mode showing unsaved changes.
 * Demonstrates version control and content management features.
 */
export const ArtifactWithChanges: Story = {
	args: {
		messages: mockLongConversation,
		artifact: {
			...mockArtifact,
			content: `${mockArtifact.content}\n\n## 3. Additional Pattern\n\nThis is new content being added...`,
		},
		documents: mockDocuments,
		currentDocument: mockDocuments[0],
		isCurrentVersion: true,
		isContentDirty: true,
		onArtifactClose: fn(),
		onArtifactContentSave: fn((content) => {
			console.log("Saving content:", `${content.substring(0, 100)}...`);
		}),
	},
};

/**
 * Mobile-optimized chat layout.
 * Shows how the interface adapts to smaller screens and touch interactions.
 */
export const Mobile: Story = {
	args: {
		messages: mockMessages,
		votes: mockVotes,
		showSuggestedActions: true,
	},
	parameters: {
		viewport: {
			defaultViewport: "mobile1",
		},
	},
};

/**
 * Chat with attachments disabled.
 * Useful for text-only conversation modes or restricted environments.
 */
export const NoAttachments: Story = {
	args: {
		messages: mockMessages,
		disableAttachments: true,
		inputPlaceholder: "Type your message (attachments disabled)...",
	},
};

/**
 * Error state demonstration.
 * Shows how the interface handles and recovers from errors.
 */
export const WithError: Story = {
	args: {
		messages: [
			...mockMessages,
			{
				id: "msg-error",
				role: "user",
				parts: [{ type: "text", text: "This message caused an error" }],
				metadata: { createdAt: new Date().toISOString() },
			},
		],
		status: "error",
	},
};
