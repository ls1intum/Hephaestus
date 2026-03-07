import type { Meta, StoryObj } from "@storybook/react";
import type { PullRequestInfo } from "@/api/types.gen";
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
type Story = StoryObj<typeof ReviewsPopover>;

export const WithReviews: Story = {
	args: {
		reviewedPRs: mockPullRequests,
		highlight: false,
	},
};

export const Highlighted: Story = {
	args: {
		reviewedPRs: mockPullRequests,
		highlight: true,
	},
};

export const NoReviews: Story = {
	args: {
		reviewedPRs: [],
		highlight: false,
	},
};

// --- GitLab variants ---
import { gitlabDecorator } from "@/stories/decorators";

const gitlabMockPullRequests: PullRequestInfo[] = [
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
	},
];

/**
 * GitLab version — uses GitLab MR icons and "merge requests" terminology.
 */
export const GitLabWithReviews: Story = {
	decorators: [gitlabDecorator],
	args: {
		reviewedPRs: gitlabMockPullRequests,
		highlight: false,
		providerType: "GITLAB",
	},
};

/**
 * GitLab highlighted variant.
 */
export const GitLabHighlighted: Story = {
	decorators: [gitlabDecorator],
	args: {
		reviewedPRs: gitlabMockPullRequests,
		highlight: true,
		providerType: "GITLAB",
	},
};
