import type { Meta, StoryObj } from "@storybook/react";
import { subDays } from "date-fns";
import { ProfileContent } from "./ProfileContent";

// Mock review activity data
const mockReviewActivity = [
	{
		id: 1,
		state: "APPROVED" as const,
		submittedAt: subDays(new Date(), 2),
		htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
		pullRequest: {
			id: 101,
			title: "Add new feature to dashboard",
			number: 42,
			state: "OPEN" as const,
			isDraft: false,
			isMerged: false,
			htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
			repository: {
				id: 1,
				name: "Hephaestus",
				nameWithOwner: "ls1intum/Hephaestus",
				htmlUrl: "https://github.com/ls1intum/Hephaestus",
				hiddenFromContributions: false,
			},
		},
		score: 80,
		isDismissed: false,
		codeComments: 3,
	},
	{
		id: 2,
		state: "CHANGES_REQUESTED" as const,
		submittedAt: subDays(new Date(), 5),
		htmlUrl: "https://github.com/ls1intum/Artemis/pull/123",
		pullRequest: {
			id: 102,
			title: "Fix authentication bugs",
			number: 123,
			state: "OPEN" as const,
			isDraft: false,
			isMerged: false,
			htmlUrl: "https://github.com/ls1intum/Artemis/pull/123",
			repository: {
				id: 2,
				name: "Artemis",
				nameWithOwner: "ls1intum/Artemis",
				htmlUrl: "https://github.com/ls1intum/Artemis",
				hiddenFromContributions: false,
			},
		},
		score: 65,
		isDismissed: false,
		codeComments: 2,
	},
	{
		id: 3,
		state: "COMMENTED" as const,
		submittedAt: subDays(new Date(), 7),
		htmlUrl: "https://github.com/ls1intum/Athena/pull/15",
		pullRequest: {
			id: 103,
			title: "Update documentation",
			number: 15,
			state: "OPEN" as const,
			isDraft: false,
			isMerged: false,
			htmlUrl: "https://github.com/ls1intum/Athena/pull/15",
			repository: {
				id: 3,
				name: "Athena",
				nameWithOwner: "ls1intum/Athena",
				htmlUrl: "https://github.com/ls1intum/Athena",
				hiddenFromContributions: false,
			},
		},
		score: 50,
		isDismissed: false,
		codeComments: 0,
	},
];

// Mock open pull requests data
const mockOpenPullRequests = [
	{
		id: 101,
		number: 42,
		title: "Add new analytics dashboard",
		state: "OPEN" as const,
		isDraft: false,
		isMerged: false,
		commentsCount: 5,
		additions: 250,
		deletions: 30,
		htmlUrl: "https://github.com/ls1intum/Hephaestus/pull/42",
		createdAt: subDays(new Date(), 3),
		repository: {
			id: 1,
			name: "Hephaestus",
			nameWithOwner: "ls1intum/Hephaestus",
			htmlUrl: "https://github.com/ls1intum/Hephaestus",
			hiddenFromContributions: false,
		},
		labels: [
			{ id: 1, name: "enhancement", color: "0E8A16" },
			{ id: 2, name: "frontend", color: "FBCA04" },
		],
	},
	{
		id: 102,
		number: 87,
		title: "WIP: Refactor authentication module",
		state: "OPEN" as const,
		isDraft: true,
		isMerged: false,
		commentsCount: 0,
		additions: 320,
		deletions: 280,
		htmlUrl: "https://github.com/ls1intum/Artemis/pull/87",
		createdAt: subDays(new Date(), 1),
		repository: {
			id: 2,
			name: "Artemis",
			nameWithOwner: "ls1intum/Artemis",
			htmlUrl: "https://github.com/ls1intum/Artemis",
			hiddenFromContributions: false,
		},
		labels: [
			{ id: 3, name: "refactoring", color: "D93F0B" },
			{ id: 4, name: "security", color: "5319E7" },
		],
	},
];

/**
 * Main content section of the profile page that displays a user's activity.
 * Shows pull requests, review activity, and other contribution metrics.
 */
const meta = {
	component: ProfileContent,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The main content section of the user profile showing activity data, reviews, and pull requests.",
			},
		},
	},
	argTypes: {
		reviewActivity: {
			description: "Array of user review activity data",
			control: "object",
		},
		openPullRequests: {
			description: "Array of user open pull requests",
			control: "object",
		},
		isLoading: {
			description: "Whether the component is in a loading state",
			control: "boolean",
		},
		username: {
			description: "GitHub username of the profile owner",
			control: "text",
		},
		workspaceSlug: {
			description: "Active workspace slug",
			control: "text",
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof ProfileContent>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Standard view showing both reviews and pull requests for an active user.
 */
export const Default: Story = {
	args: {
		reviewActivity: mockReviewActivity,
		openPullRequests: mockOpenPullRequests,
		isLoading: false,
		username: "johndoe",
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
	},
};

/**
 * Loading state shown while user data is being fetched from API.
 */
export const Loading: Story = {
	args: {
		isLoading: true,
		username: "johndoe",
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
	},
};

/**
 * Shows how the UI appears when the user has open pull requests but no review activity.
 */
export const EmptyReviews: Story = {
	args: {
		reviewActivity: [],
		openPullRequests: mockOpenPullRequests,
		isLoading: false,
		username: "johndoe",
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
	},
};

/**
 * Shows how the UI appears when the user has review activity but no open pull requests.
 */
export const EmptyPullRequests: Story = {
	args: {
		reviewActivity: mockReviewActivity,
		openPullRequests: [],
		isLoading: false,
		username: "johndoe",
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
	},
};

/**
 * Shows the empty state when a user has no activity (no reviews and no pull requests).
 */
export const CompletelyEmpty: Story = {
	args: {
		reviewActivity: [],
		openPullRequests: [],
		isLoading: false,
		username: "johndoe",
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
	},
};
