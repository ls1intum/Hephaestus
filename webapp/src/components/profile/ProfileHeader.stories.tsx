import type { Meta, StoryObj } from "@storybook/react";
import { ProfileHeader } from "./ProfileHeader";

/**
 * Header component for the user profile page displaying key user information:
 * the user's avatar, name, provider link, and a shortcut to their achievements.
 */
const meta = {
	component: ProfileHeader,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Header section of the profile page showing user identity information.",
			},
		},
	},
	argTypes: {
		isLoading: {
			description: "Whether the component is in a loading state",
			control: "boolean",
		},
		user: {
			description: "User profile data",
			control: "object",
		},
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "example-workspace",
	},
} satisfies Meta<typeof ProfileHeader>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Standard profile header showing an active contributor.
 */
export const Default: Story = {
	args: {
		isLoading: false,
		user: {
			id: 1,
			login: "johndoe",
			name: "John Doe",
			avatarUrl: "https://github.com/github.png",
			htmlUrl: "https://github.com/johndoe",
		},
	},
};

/**
 * Loading state displayed while user data is being fetched.
 */
export const Loading: Story = {
	args: {
		isLoading: true,
	},
};

/**
 * Achievements shortcut hidden when the workspace has achievements disabled.
 */
export const AchievementsDisabled: Story = {
	args: {
		isLoading: false,
		achievementsEnabled: false,
		user: {
			id: 2,
			login: "janedoe",
			name: "Jane Doe",
			avatarUrl: "https://github.com/octocat.png",
			htmlUrl: "https://github.com/janedoe",
		},
	},
};
