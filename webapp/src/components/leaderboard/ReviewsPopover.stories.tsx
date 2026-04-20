import type { Meta, StoryObj } from "@storybook/react";
import type { PullRequestInfo } from "@/api/types.gen";
import { withProvider } from "@/stories/decorators";
import { ReviewsPopover } from "./ReviewsPopover";

const mockPullRequests: PullRequestInfo[] = [
	{
		id: 1,
		number: 101,
		title: "Fix login bug",
		state: "CLOSED",
		isDraft: false,
		isMerged: true,
		commentsCount: 3,
		additions: 50,
		deletions: 10,
		htmlUrl: "https://github.com/org/repo/pull/101",
		repository: {
			id: 1,
			name: "Hephaestus",
			nameWithOwner: "org/repo",
			htmlUrl: "https://github.com/org/repo",
			hiddenFromContributions: false,
		},
	},
	{
		id: 2,
		number: 102,
		title: "Update documentation",
		state: "CLOSED",
		isDraft: false,
		isMerged: true,
		commentsCount: 1,
		additions: 120,
		deletions: 5,
		htmlUrl: "https://github.com/org/repo/pull/102",
		repository: {
			id: 2,
			name: "Artemis",
			nameWithOwner: "org/repo-2",
			htmlUrl: "https://github.com/org/repo-2",
			hiddenFromContributions: false,
		},
	},
];

const meta: Meta<typeof ReviewsPopover> = {
	component: ReviewsPopover,
	tags: ["autodocs"],
	argTypes: {
		highlight: { control: "boolean" },
	},
	parameters: {
		layout: "centered",
	},
};

export default meta;
type Story = StoryObj<typeof meta>;

export const WithReviews: Story = {
	args: {
		reviewedPullRequests: mockPullRequests,
		highlight: false,
	},
};

export const Highlighted: Story = {
	args: {
		reviewedPullRequests: mockPullRequests,
		highlight: true,
	},
};

// --- Alternate provider variants ---

const mockMergeRequests: PullRequestInfo[] = [
	{
		id: 1,
		number: 101,
		title: "Fix login bug",
		state: "CLOSED",
		isDraft: false,
		isMerged: true,
		commentsCount: 3,
		additions: 50,
		deletions: 10,
		htmlUrl: "https://gitlab.com/org/repo/-/merge_requests/101",
		repository: {
			id: 1,
			name: "Hephaestus",
			nameWithOwner: "org/repo",
			htmlUrl: "https://gitlab.com/org/repo",
			hiddenFromContributions: false,
		},
	},
	{
		id: 2,
		number: 102,
		title: "Update documentation",
		state: "CLOSED",
		isDraft: false,
		isMerged: true,
		commentsCount: 1,
		additions: 120,
		deletions: 5,
		htmlUrl: "https://gitlab.com/org/repo/-/merge_requests/102",
		repository: {
			id: 2,
			name: "Artemis",
			nameWithOwner: "org/repo-2",
			htmlUrl: "https://gitlab.com/org/repo-2",
			hiddenFromContributions: false,
		},
	},
];

/**
 * Alternate provider — uses merge request icons and terminology.
 */
export const WithReviewsMergeRequest: Story = {
	decorators: [withProvider("GITLAB")],
	args: {
		reviewedPullRequests: mockMergeRequests,
		highlight: false,
		providerType: "GITLAB",
	},
};

/**
 * Alternate provider highlighted variant.
 */
export const HighlightedMergeRequest: Story = {
	decorators: [withProvider("GITLAB")],
	args: {
		reviewedPullRequests: mockMergeRequests,
		highlight: true,
		providerType: "GITLAB",
	},
};
