import type { ChatMessageVote, Document } from "@/api/types.gen";
import type { ChatMessage } from "@/lib/types";
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { Chat } from "./Chat";

/**
 * Chat component providing a complete conversational interface with artifact support.
 * Handles message display, input, file attachments, and seamlessly switches between
 * standard chat view and artifact mode when documents are clicked. Self-contained
 * artifact state management with clean external API for data fetching.
 */
const meta = {
	component: Chat,
	parameters: {
		layout: "centered",
	},
	tags: ["autodocs"],
	argTypes: {
		id: {
			description: "Unique identifier for the chat session",
			control: "text",
		},
		messages: {
			description: "Array of chat messages to display",
			control: "object",
		},
		votes: {
			description: "Array of votes for messages",
			control: "object",
		},
		status: {
			description: "Current chat status",
			control: "select",
			options: ["submitted", "streaming", "ready", "error"],
		},
		readonly: {
			description: "Whether the interface is in readonly mode",
			control: "boolean",
		},
		attachments: {
			description: "Current input attachments",
			control: "object",
		},
		showSuggestedActions: {
			description: "Whether to show suggested actions in input",
			control: "boolean",
		},
		inputPlaceholder: {
			description: "Placeholder text for input",
			control: "text",
		},
		disableAttachments: {
			description: "Whether to disable attachment functionality",
			control: "boolean",
		},
	},
	args: {
		id: "chat-demo",
		messages: [],
		votes: [],
		status: "ready",
		readonly: false,
		isAtBottom: true,
		attachments: [],
		onMessageSubmit: fn(),
		onStop: fn(),
		onFileUpload: fn(async () => []),
		onAttachmentsChange: fn(),
		onMessageEdit: fn(),
		onCopy: fn(),
		onVote: fn(),
		onSuggestedAction: fn(),
		onDocumentClick: fn(),
		scrollToBottom: fn(),
		showSuggestedActions: true,
		inputPlaceholder: "Send a message...",
		disableAttachments: false,
	},
} satisfies Meta<typeof Chat>;

export default meta;
type Story = StoryObj<typeof meta>;

// Mock data for comprehensive demo
const mockMessages: ChatMessage[] = [
	{
		id: "msg-1",
		role: "user",
		parts: [
			{
				type: "text",
				text: "Can you help me create a React component for displaying user profiles?",
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
				text: "I'll help you create a React component for displaying user profiles. Let me create a comprehensive component with TypeScript support.",
			},
			{
				type: "tool-createDocument",
				toolCallId: "tool-1",
				state: "output-available",
				input: {
					title: "UserProfile.tsx",
					kind: "text" as const,
				},
				output: {
					id: "doc-1",
					title: "UserProfile.tsx",
					content: `import React from 'react';
import { cn } from '@/lib/utils';

interface User {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  bio?: string;
  location?: string;
  joinedAt: Date;
}

interface UserProfileProps {
  user: User;
  showEmail?: boolean;
  variant?: 'default' | 'compact' | 'detailed';
  className?: string;
}

export const UserProfile: React.FC<UserProfileProps> = ({
  user,
  showEmail = false,
  variant = 'default',
  className,
}) => {
  return (
    <div className={cn(
      'flex items-center space-x-4 p-4 rounded-lg border',
      {
        'flex-col space-x-0 space-y-2': variant === 'compact',
        'flex-col lg:flex-row lg:space-y-0 lg:space-x-6': variant === 'detailed',
      },
      className
    )}>
      <div className="flex-shrink-0">
        {user.avatar ? (
          <img
            src={user.avatar}
            alt={user.name}
            className="w-12 h-12 rounded-full object-cover"
          />
        ) : (
          <div className="w-12 h-12 rounded-full bg-gray-200 flex items-center justify-center">
            <span className="text-gray-600 font-medium">
              {user.name.charAt(0).toUpperCase()}
            </span>
          </div>
        )}
      </div>
      
      <div className="flex-1 min-w-0">
        <h3 className="text-lg font-semibold text-gray-900 truncate">
          {user.name}
        </h3>
        
        {showEmail && (
          <p className="text-sm text-gray-600 truncate">
            {user.email}
          </p>
        )}
        
        {user.bio && variant === 'detailed' && (
          <p className="text-sm text-gray-700 mt-2">
            {user.bio}
          </p>
        )}
        
        <div className="flex items-center text-xs text-gray-500 mt-1">
          {user.location && (
            <span className="mr-2">📍 {user.location}</span>
          )}
          <span>
            Joined {user.joinedAt.toLocaleDateString()}
          </span>
        </div>
      </div>
    </div>
  );
};

export default UserProfile;`,
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
				text: "This looks great! Can you also add a stories file for this component?",
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
				text: "Absolutely! I'll create a comprehensive Storybook stories file that demonstrates all the variants and features of the UserProfile component.",
			},
			{
				type: "tool-createDocument",
				toolCallId: "tool-2",
				state: "output-available",
				input: {
					title: "UserProfile.stories.tsx",
					kind: "text" as const,
				},
				output: {
					id: "doc-2",
					title: "UserProfile.stories.tsx",
					content: `import type { Meta, StoryObj } from '@storybook/react';
import { UserProfile } from './UserProfile';

/**
 * UserProfile component for displaying user information in various formats.
 * Supports different variants and customization options for different contexts.
 */
const meta = {
  component: UserProfile,
  parameters: {
    layout: 'centered',
  },
  tags: ['autodocs'],
  argTypes: {
    user: {
      description: 'User data object',
      control: 'object',
    },
    showEmail: {
      description: 'Whether to display the user email',
      control: 'boolean',
    },
    variant: {
      description: 'Display variant',
      control: 'select',
      options: ['default', 'compact', 'detailed'],
    },
  },
  args: {
    user: {
      id: '1',
      name: 'Sarah Wilson',
      email: 'sarah.wilson@example.com',
      avatar: 'https://i.pravatar.cc/150?img=1',
      bio: 'Frontend developer passionate about creating accessible and beautiful user interfaces.',
      location: 'San Francisco, CA',
      joinedAt: new Date('2023-01-15'),
    },
    showEmail: false,
    variant: 'default',
  },
  decorators: [
    (Story) => (
      <div className="max-w-md p-6">
        <Story />
      </div>
    ),
  ],
} satisfies Meta<typeof UserProfile>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default user profile display.
 */
export const Default: Story = {};

/**
 * Compact variant for smaller spaces or lists.
 */
export const Compact: Story = {
  args: {
    variant: 'compact',
  },
};

/**
 * Detailed variant with bio and additional information.
 */
export const Detailed: Story = {
  args: {
    variant: 'detailed',
    showEmail: true,
  },
};

/**
 * User profile without avatar image.
 */
export const WithoutAvatar: Story = {
  args: {
    user: {
      id: '2',
      name: 'John Doe',
      email: 'john.doe@example.com',
      bio: 'Backend engineer specializing in scalable systems.',
      location: 'New York, NY',
      joinedAt: new Date('2022-08-20'),
    },
    variant: 'detailed',
    showEmail: true,
  },
};`,
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
				type: "file",
				filename: "design-system.png",
				mediaType: "image/png",
				url: "https://picsum.photos/400/300?random=1",
			},
			{
				type: "text",
				text: "Here's our design system reference. Can you update the component to match these styles?",
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
				text: "Perfect! I can see your design system. Let me update the UserProfile component to align with these visual guidelines and color scheme.",
			},
			{
				type: "tool-updateDocument",
				toolCallId: "tool-3",
				state: "output-available",
				input: {
					id: "doc-1",
					description: "Update component styling to match design system",
				},
				output: {
					id: "doc-1",
					title: "UserProfile.tsx",
					content: `import React from 'react';
import { cn } from '@/lib/utils';

interface User {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  bio?: string;
  location?: string;
  joinedAt: Date;
  status?: 'online' | 'offline' | 'away';
}

interface UserProfileProps {
  user: User;
  showEmail?: boolean;
  showStatus?: boolean;
  variant?: 'default' | 'compact' | 'detailed';
  className?: string;
  onClick?: () => void;
}

export const UserProfile: React.FC<UserProfileProps> = ({
  user,
  showEmail = false,
  showStatus = false,
  variant = 'default',
  className,
  onClick,
}) => {
  const getStatusColor = (status?: string) => {
    switch (status) {
      case 'online': return 'bg-green-500';
      case 'away': return 'bg-yellow-500';
      case 'offline': return 'bg-gray-400';
      default: return 'bg-gray-400';
    }
  };

  return (
    <div 
      className={cn(
        'group flex items-center space-x-4 p-6 rounded-xl',
        'bg-white border border-gray-200 shadow-sm',
        'hover:shadow-md hover:border-gray-300 transition-all duration-200',
        {
          'flex-col space-x-0 space-y-3 p-4': variant === 'compact',
          'flex-col lg:flex-row lg:space-y-0 lg:space-x-6 p-8': variant === 'detailed',
          'cursor-pointer': onClick,
        },
        className
      )}
      onClick={onClick}
    >
      <div className="relative flex-shrink-0">
        {user.avatar ? (
          <img
            src={user.avatar}
            alt={user.name}
            className={cn(
              'rounded-full object-cover ring-2 ring-gray-100',
              {
                'w-10 h-10': variant === 'compact',
                'w-12 h-12': variant === 'default',
                'w-16 h-16': variant === 'detailed',
              }
            )}
          />
        ) : (
          <div className={cn(
            'rounded-full bg-gradient-to-br from-blue-500 to-purple-600',
            'flex items-center justify-center text-white font-semibold',
            {
              'w-10 h-10 text-sm': variant === 'compact',
              'w-12 h-12 text-base': variant === 'default',
              'w-16 h-16 text-lg': variant === 'detailed',
            }
          )}>
            {user.name.charAt(0).toUpperCase()}
          </div>
        )}
        
        {showStatus && user.status && (
          <div className={cn(
            'absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-white',
            getStatusColor(user.status)
          )} />
        )}
      </div>
      
      <div className="flex-1 min-w-0">
        <div className="flex items-center space-x-2">
          <h3 className={cn(
            'font-semibold text-gray-900 truncate',
            {
              'text-sm': variant === 'compact',
              'text-base': variant === 'default',
              'text-lg': variant === 'detailed',
            }
          )}>
            {user.name}
          </h3>
          
          {showStatus && user.status && (
            <span className={cn(
              'inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium',
              {
                'bg-green-100 text-green-800': user.status === 'online',
                'bg-yellow-100 text-yellow-800': user.status === 'away',
                'bg-gray-100 text-gray-800': user.status === 'offline',
              }
            )}>
              {user.status}
            </span>
          )}
        </div>
        
        {showEmail && (
          <p className="text-sm text-gray-600 truncate mt-0.5">
            {user.email}
          </p>
        )}
        
        {user.bio && variant === 'detailed' && (
          <p className="text-sm text-gray-700 mt-2 leading-relaxed">
            {user.bio}
          </p>
        )}
        
        <div className="flex items-center text-xs text-gray-500 mt-2">
          {user.location && (
            <span className="inline-flex items-center mr-3">
              <svg className="w-3 h-3 mr-1" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M5.05 4.05a7 7 0 119.9 9.9L10 18.9l-4.95-4.95a7 7 0 010-9.9zM10 11a2 2 0 100-4 2 2 0 000 4z" clipRule="evenodd" />
              </svg>
              {user.location}
            </span>
          )}
          <span className="inline-flex items-center">
            <svg className="w-3 h-3 mr-1" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M6 2a1 1 0 00-1 1v1H4a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V6a2 2 0 00-2-2h-1V3a1 1 0 10-2 0v1H7V3a1 1 0 00-1-1zm0 5a1 1 0 000 2h8a1 1 0 100-2H6z" clipRule="evenodd" />
            </svg>
            Joined {user.joinedAt.toLocaleDateString('en-US', { 
              month: 'short', 
              year: 'numeric' 
            })}
          </span>
        </div>
      </div>
    </div>
  );
};

export default UserProfile;`,
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
];

const mockVotes: ChatMessageVote[] = [
	{
		messageId: "msg-2",
		isUpvoted: true,
	},
	{
		messageId: "msg-4",
		isUpvoted: true,
	},
];

const mockAttachments = [
	{
		name: "component-spec.md",
		url: "https://example.com/spec.md",
		contentType: "text/markdown",
	},
];

/**
 * Complete chat experience showing the full conversation flow with artifact switching.
 * Click on any document preview to open it in artifact mode. This demonstrates
 * how the component seamlessly transitions between chat and artifact views.
 */
export const ComprehensiveDemo: Story = {
	args: {
		messages: mockMessages,
		votes: mockVotes,
		attachments: [],
		onDocumentClick: fn((document: Document, boundingBox: DOMRect) => {
			console.log("Document clicked:", document.title, boundingBox);
			// In a real app, this would trigger external data fetching
			// The component handles the artifact state internally
		}),
	},
};

/**
 * Empty chat state showing the greeting and input interface.
 */
export const EmptyChat: Story = {
	args: {
		messages: [],
		attachments: [],
	},
};

/**
 * Chat with current input attachments ready to send.
 */
export const WithAttachments: Story = {
	args: {
		messages: mockMessages.slice(0, 2),
		attachments: mockAttachments,
	},
};

/**
 * Chat in streaming state with thinking indicator.
 */
export const Streaming: Story = {
	args: {
		messages: [
			...mockMessages.slice(0, 3),
			{
				id: "msg-streaming",
				role: "assistant",
				parts: [
					{
						type: "text",
						text: "I'm analyzing your design system and updating the component...",
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
 * Readonly chat view - no input interface, used for historical conversations.
 */
export const ReadonlyMode: Story = {
	args: {
		messages: mockMessages,
		votes: mockVotes,
		readonly: true,
	},
};

/**
 * Chat with disabled attachments for restricted environments.
 */
export const NoAttachments: Story = {
	args: {
		messages: mockMessages.slice(0, 4),
		disableAttachments: true,
		inputPlaceholder: "Send a message (attachments disabled)...",
	},
};
