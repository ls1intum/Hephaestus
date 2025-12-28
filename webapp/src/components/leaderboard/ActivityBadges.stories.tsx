import type { Meta, StoryObj } from "@storybook/react-vite";
import { ActivityBadges } from "./ActivityBadges";
import type { ReviewedPullRequest } from "./ReviewsPopover";

const basePRs: ReviewedPullRequest[] = [
	{
		id: 1,
		number: 42,
		title: "Improve docs",
		htmlUrl: "https://github.com/example/repo/pull/42",
		additions: 10,
		deletions: 2,
		commentsCount: 0,
		isDraft: false,
		isMerged: false,
		createdAt: new Date(),
		state: "OPEN",
		labels: [],
		repository: {
			id: 100,
			name: "repo",
			nameWithOwner: "example/repo",
			htmlUrl: "https://github.com/example/repo",
			hiddenFromContributions: false,
		},
	},
	{
		id: 2,
		number: 7,
		title: "Fix tests",
		htmlUrl: "https://github.com/example/repo/pull/7",
		additions: 5,
		deletions: 1,
		commentsCount: 0,
		isDraft: false,
		isMerged: false,
		createdAt: new Date(),
		state: "OPEN",
		labels: [],
		repository: {
			id: 101,
			name: "repo",
			nameWithOwner: "example/repo",
			htmlUrl: "https://github.com/example/repo",
			hiddenFromContributions: false,
		},
	},
];

/**
 * Activity badges summarize review work by showing reviewed pull requests, approvals, change requests, comments, and code comments at a glance.
 */
const meta = {
	component: ActivityBadges,
	tags: ["autodocs"],
	args: {
		reviewedPullRequests: basePRs,
		approvals: 3,
		changeRequests: 1,
		comments: 5,
		codeComments: 2,
		isLoading: false,
	},
} satisfies Meta<typeof ActivityBadges>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Balanced mix of approvals, change requests, comments, and code comments.
 */
export const Default: Story = {};

/**
 * Highlights reviewed pull requests without other activity types.
 */
export const ReviewsOnly: Story = {
	args: {
		reviewedPullRequests: basePRs,
		approvals: 0,
		changeRequests: 0,
		comments: 0,
		codeComments: 0,
		highlightReviews: true,
	},
};

/**
 * Minimal activity with a single change request and no reviewed pull requests.
 */
export const Minimal: Story = {
	args: {
		reviewedPullRequests: [],
		approvals: 0,
		changeRequests: 1,
		comments: 0,
		codeComments: 0,
	},
};

/**
 * Skeleton placeholders shown while review stats are still loading.
 */
export const Loading: Story = {
	args: {
		isLoading: true,
		reviewedPullRequests: [],
		approvals: 0,
		changeRequests: 0,
		comments: 0,
		codeComments: 0,
	},
};
