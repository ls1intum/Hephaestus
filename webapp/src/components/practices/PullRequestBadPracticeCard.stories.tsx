import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { PullRequestBadPracticeCard } from "./PullRequestBadPracticeCard";

/**
 * Card component for displaying pull requests with their metadata and analysis options.
 * Allows users to trigger bad practice detection on specific pull requests and take
 * actions on discovered issues.
 */
const meta = {
	component: PullRequestBadPracticeCard,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"A card component for displaying pull request information with options to analyze the code for bad practices. It shows the PR metadata, labels, and detected issues with actionable controls to mark them as fixed, won't fix, or wrong.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		id: {
			description: "Unique identifier for the pull request",
			control: "number",
		},
		title: {
			description: "Pull request title",
			control: "text",
		},
		number: {
			description: "Pull request number (as shown in GitHub)",
			control: "number",
		},
		additions: {
			description: "Number of lines added in the PR",
			control: "number",
		},
		deletions: {
			description: "Number of lines deleted in the PR",
			control: "number",
		},
		htmlUrl: {
			description: "Link to the pull request",
			control: "text",
		},
		state: {
			description: "Current state of the PR",
			control: "select",
			options: ["OPEN", "CLOSED"],
		},
		isDraft: {
			description: "Whether the PR is a draft",
			control: "boolean",
		},
		isMerged: {
			description: "Whether the PR has been merged",
			control: "boolean",
		},
		repositoryName: {
			description: "Name of the repository the PR belongs to",
			control: "text",
		},
		createdAt: {
			description: "Creation date of the PR (Date object)",
			control: "date",
		},
		updatedAt: {
			description: "Last update date of the PR (Date object)",
			control: "date",
		},
		pullRequestLabels: {
			description: "Array of labels attached to the PR",
			control: "object",
		},
		badPractices: {
			description: "List of identified practices (good or bad) in the PR",
			control: "object",
		},
		badPracticeSummary: {
			description: "Summary text about the practices identified in the PR",
			control: "text",
		},
		isLoading: {
			description: "Whether the card is in a loading state",
			control: "boolean",
		},
		currUserIsDashboardUser: {
			description: "Whether the current user has dashboard access",
			control: "boolean",
		},
		oldBadPractices: {
			description:
				"List of previously identified practices that have been resolved",
			control: "object",
		},
		openCard: {
			description: "Whether the card should be expanded by default",
			control: "boolean",
		},
		onDetectBadPractices: {
			description: "Callback when detect bad practices button is clicked",
			action: "detect bad practices clicked",
		},
		onResolveBadPracticeAsFixed: {
			description: "Callback when a bad practice is resolved as fixed",
			action: "resolve as fixed clicked",
		},
		onResolveBadPracticeAsWontFix: {
			description: "Callback when a bad practice is resolved as won't fix",
			action: "resolve as won't fix clicked",
		},
		onResolveBadPracticeAsWrong: {
			description: "Callback when a bad practice is resolved as wrong",
			action: "resolve as wrong clicked",
		},
		onProvideBadPracticeFeedback: {
			description: "Callback when feedback is provided for a bad practice",
			action: "feedback provided",
		},
	},
	args: {
		onDetectBadPractices: fn(),
		onResolveBadPracticeAsFixed: fn(),
		onResolveBadPracticeAsWontFix: fn(),
		onResolveBadPracticeAsWrong: fn(),
		onProvideBadPracticeFeedback: fn(),
	},
} satisfies Meta<typeof PullRequestBadPracticeCard>;

export default meta;

type Story = StoryObj<typeof PullRequestBadPracticeCard>;

/**
 * Default view of an open pull request with multiple labels and bad practices.
 * Shows how the component displays various types of bad practices and their severity levels.
 */
export const Default: Story = {
	args: {
		id: 1,
		title: "`General`: Add feature X",
		number: 12,
		additions: 10,
		deletions: 5,
		htmlUrl: "https://github.com/Artemis/repo/pull/12",
		state: "OPEN",
		isDraft: false,
		isMerged: false,
		repositoryName: "Artemis",
		createdAt: new Date("2024-01-01T12:00:00Z"),
		updatedAt: new Date("2024-01-05T14:30:00Z"),
		pullRequestLabels: [
			{ id: 1, name: "bug", color: "d73a4a" },
			{ id: 2, name: "enhancement", color: "0e8a16" },
		],
		badPractices: [
			{
				id: 1,
				title: "Avoid using any type",
				description:
					"Using the any type defeats the purpose of TypeScript. Consider using a more specific type or unknown if the type is truly not known.",
				state: "CRITICAL_ISSUE",
			},
			{
				id: 2,
				title: "Unchecked checkbox in description",
				description:
					"Unchecked checkboxes in the description are not allowed. Please complete all checklist items before merging.",
				state: "MINOR_ISSUE",
			},
		],
		badPracticeSummary:
			"We found 2 bad practices in this pull request. Please fix them. Thank you!",
	},
};

/**
 * Shows the loading state of the component.
 * Used to display while fetching data or analyzing the pull request.
 */
export const Loading: Story = {
	args: {
		id: 1,
		isLoading: true,
	},
};

/**
 * Displays a pull request with only good practices detected.
 * Shows how the component acknowledges good code quality.
 */
export const WithGoodPractices: Story = {
	args: {
		id: 1,
		title: "Add new accessibility features",
		number: 12,
		htmlUrl: "https://github.com/Artemis/repo/pull/12",
		state: "OPEN",
		badPractices: [
			{
				id: 1,
				title: "Good code structure",
				description:
					"The code follows a clean structure with proper separation of concerns.",
				state: "GOOD_PRACTICE",
			},
			{
				id: 2,
				title: "Well-documented functions",
				description:
					"Functions are properly documented with JSDoc comments, making the code self-documenting and easier to maintain.",
				state: "GOOD_PRACTICE",
			},
		],
		badPracticeSummary: "Great work! We found 2 good practices in your code.",
		openCard: true,
	},
};

/**
 * Shows the component with enabled user controls for dashboard users.
 * Dashboard users can resolve issues as fixed, won't fix, or provide feedback.
 */
export const WithUserControls: Story = {
	args: {
		id: 1,
		title: "Refactor authentication system",
		number: 12,
		htmlUrl: "https://github.com/Artemis/repo/pull/12",
		state: "OPEN",
		badPractices: [
			{
				id: 1,
				title: "Avoid using any type",
				description:
					"Using the any type defeats the purpose of TypeScript. Consider using a more specific type or unknown if the type is truly not known.",
				state: "CRITICAL_ISSUE",
			},
		],
		badPracticeSummary:
			"We found 1 bad practice in this pull request. Please fix it.",
		currUserIsDashboardUser: true,
		openCard: true,
	},
};

/**
 * Displays a mix of good practices and issues of different severity levels.
 * Shows how the component handles and displays varied content.
 */
export const WithMixedPractices: Story = {
	args: {
		id: 1,
		title: "Refactor database access layer",
		number: 12,
		htmlUrl: "https://github.com/Artemis/repo/pull/12",
		state: "OPEN",
		badPractices: [
			{
				id: 1,
				title: "Good code structure",
				description:
					"The code follows a clean structure with proper separation of concerns.",
				state: "GOOD_PRACTICE",
			},
			{
				id: 2,
				title: "Avoid using any type",
				description:
					"Using the any type defeats the purpose of TypeScript. Consider using a more specific type.",
				state: "CRITICAL_ISSUE",
			},
			{
				id: 3,
				title: "Missing error handling",
				description:
					"Error handling is missing in async functions. Add try-catch blocks to handle potential errors.",
				state: "NORMAL_ISSUE",
			},
		],
		badPracticeSummary:
			"We found 1 good practice and 2 issues in this pull request.",
		openCard: true,
	},
};

/**
 * Shows a pull request with both current and previously fixed issues.
 * Demonstrates how the component handles history and resolution of bad practices.
 */
export const WithOldPractices: Story = {
	args: {
		id: 1,
		title: "Implement new feature flags system",
		number: 12,
		htmlUrl: "https://github.com/Artemis/repo/pull/12",
		state: "OPEN",
		badPractices: [
			{
				id: 1,
				title: "Avoid using any type",
				description:
					"Using the any type defeats the purpose of TypeScript. Consider using a more specific type.",
				state: "CRITICAL_ISSUE",
			},
		],
		oldBadPractices: [
			{
				id: 2,
				title: "Missing error handling",
				description: "Error handling is missing in async functions.",
				state: "FIXED",
			},
			{
				id: 3,
				title: "Unnecessary comments",
				description: "Code has redundant comments that don't add value.",
				state: "WONT_FIX",
			},
			{
				id: 4,
				title: "Function too long",
				description:
					"This was flagged incorrectly, function is appropriate length for its complexity.",
				state: "WRONG",
			},
		],
		badPracticeSummary:
			"We found 1 issue in this pull request. 3 previous issues have been resolved.",
		openCard: true,
	},
};

/**
 * Displays a merged pull request with resolved issues.
 * Shows how the component displays completed pull requests.
 */
export const MergedPullRequest: Story = {
	args: {
		id: 1,
		title: "Add unit tests for authentication module",
		number: 12,
		additions: 235,
		deletions: 7,
		htmlUrl: "https://github.com/Artemis/repo/pull/12",
		state: "CLOSED",
		isDraft: false,
		isMerged: true,
		repositoryName: "Artemis",
		createdAt: new Date("2023-12-20T09:15:00Z"),
		updatedAt: new Date("2024-01-02T16:45:00Z"),
		pullRequestLabels: [
			{ id: 1, name: "tests", color: "0075ca" },
			{ id: 2, name: "approved", color: "0e8a16" },
		],
		badPractices: [
			{
				id: 1,
				title: "Good test coverage",
				description: "Tests cover all critical paths and edge cases.",
				state: "GOOD_PRACTICE",
			},
			{
				id: 2,
				title: "Well-structured test scenarios",
				description:
					"Tests are properly organized with clear arrange-act-assert pattern.",
				state: "GOOD_PRACTICE",
			},
		],
		oldBadPractices: [
			{
				id: 3,
				title: "Missing test for error condition",
				description: "Add test for network timeout scenario.",
				state: "FIXED",
			},
		],
		badPracticeSummary:
			"Great work! All issues resolved with 2 good practices identified.",
	},
};

/**
 * Shows a draft pull request with pending analysis.
 * Demonstrates the component appearance for work-in-progress PRs.
 */
export const DraftPullRequest: Story = {
	args: {
		id: 1,
		title: "[WIP] Implement new API endpoints",
		number: 15,
		additions: 78,
		deletions: 12,
		htmlUrl: "https://github.com/Artemis/repo/pull/15",
		state: "OPEN",
		isDraft: true,
		isMerged: false,
		repositoryName: "Artemis",
		createdAt: new Date("2024-02-05T10:30:00Z"),
		pullRequestLabels: [
			{ id: 1, name: "WIP", color: "fbca04" },
			{ id: 2, name: "api", color: "1d76db" },
		],
		badPractices: [],
		badPracticeSummary:
			"No analysis has been run on this draft pull request yet.",
	},
};
