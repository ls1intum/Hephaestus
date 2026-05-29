import type { Meta, StoryObj } from "@storybook/react";
import { endOfISOWeek, formatISO, startOfISOWeek } from "date-fns";
import { fn } from "storybook/test";
import { ProfilePage } from "./ProfilePage";

const now = new Date();
const defaultAfter = formatISO(startOfISOWeek(now));
const defaultBefore = formatISO(endOfISOWeek(now));

/**
 * Complete user profile page that combines header and content sections.
 * Displays a user's profile information, contributions, review activity,
 * and open pull requests in a unified interface.
 */
const meta = {
	component: ProfilePage,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The main profile page that integrates all profile components into a cohesive user profile view.",
			},
		},
	},
	argTypes: {
		isLoading: {
			description: "Whether the page is in a loading state",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "false" },
			},
		},
		error: {
			description: "Whether there was an error loading the profile data",
			control: "boolean",
			table: {
				type: { summary: "boolean" },
				defaultValue: { summary: "false" },
			},
		},
		username: {
			description: "Username of the profile owner",
			control: "text",
			table: {
				type: { summary: "string" },
			},
		},
		profileData: {
			description: "Complete profile data object containing user info and activity",
			control: "object",
			table: {
				type: { summary: "object" },
			},
		},
		workspaceSlug: {
			description: "Active workspace slug for routing",
			control: "text",
		},
		after: {
			description: "Start of the activity window (ISO string)",
			control: "text",
		},
		before: {
			description: "End of the activity window (ISO string)",
			control: "text",
		},
		onTimeframeChange: {
			description: "Callback when the timeframe is adjusted",
			table: { type: { summary: "function" } },
		},
	},
	tags: ["autodocs"],
} satisfies Meta<typeof ProfilePage>;

export default meta;
type Story = StoryObj<typeof meta>;

const baseMonitorArgs = {
	activityMonitorFilters: { repositoryIds: [], limit: 5 },
	onActivityMonitorFiltersChange: fn(),
};

/**
 * Standard profile view showing a user with activity across multiple repositories.
 */
export const Default: Story = {
	args: {
		...baseMonitorArgs,
		isLoading: false,
		error: false,
		username: "johndoe",
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
		after: defaultAfter,
		before: defaultBefore,
		onTimeframeChange: fn(),
		profileData: {
			userInfo: {
				id: 1,
				login: "johndoe",
				name: "John Doe",
				avatarUrl: "https://github.com/github.png",
				htmlUrl: "https://github.com/johndoe",
				leaguePoints: 150,
			},
			xpRecord: {
				currentLevel: 5,
				currentLevelXP: 450,
				xpNeeded: 1000,
				totalXP: 5450,
			},
			firstContribution: new Date("2022-05-15T00:00:00Z"),
			contributedRepositories: [
				{
					id: 1,
					name: "Hephaestus",
					nameWithOwner: "ls1intum/Hephaestus",
					description: "A GitHub contribution tracking tool",
					htmlUrl: "https://github.com/ls1intum/Hephaestus",
					hiddenFromContributions: false,
				},
				{
					id: 2,
					name: "Artemis",
					nameWithOwner: "ls1intum/Artemis",
					description: "Interactive learning platform",
					htmlUrl: "https://github.com/ls1intum/Artemis",
					hiddenFromContributions: false,
				},
			],
		},
	},
};

/**
 * Loading state shown while profile data is being fetched.
 */
export const Loading: Story = {
	args: {
		...baseMonitorArgs,
		isLoading: true,
		error: false,
		username: "johndoe",
		profileData: undefined,
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
		after: defaultAfter,
		before: defaultBefore,
		onTimeframeChange: fn(),
	},
};

/**
 * Error state displayed when profile data could not be loaded.
 */
export const ErrorState: Story = {
	args: {
		...baseMonitorArgs,
		isLoading: false,
		error: true,
		username: "johndoe",
		profileData: undefined,
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
		after: defaultAfter,
		before: defaultBefore,
		onTimeframeChange: fn(),
	},
};

/**
 * Shows how the profile page appears for a new user with no activity.
 */
export const Empty: Story = {
	args: {
		...baseMonitorArgs,
		isLoading: false,
		error: false,
		username: "emptydoe",
		currUserIsDashboardUser: true,
		workspaceSlug: "aet",
		after: defaultAfter,
		before: defaultBefore,
		onTimeframeChange: fn(),
		profileData: {
			userInfo: {
				id: 3,
				login: "emptydoe",
				name: "Empty Doe",
				avatarUrl: "https://github.com/octocat.png",
				htmlUrl: "https://github.com/emptydoe",
				leaguePoints: 0,
			},
			xpRecord: {
				currentLevel: 1,
				currentLevelXP: 0,
				xpNeeded: 150,
				totalXP: 0,
			},
			firstContribution: new Date("2023-10-15T00:00:00Z"),
			contributedRepositories: [],
		},
	},
};
