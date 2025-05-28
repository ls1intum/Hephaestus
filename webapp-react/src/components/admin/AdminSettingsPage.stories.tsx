import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { AdminSettingsPage } from "./AdminSettingsPage";

/**
 * Admin settings page component that combines repository management and league settings
 */
const meta = {
	component: AdminSettingsPage,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		repositories: {
			control: "object",
			description: "List of repositories to display",
		},
		isLoadingRepositories: {
			control: "boolean",
			description: "Whether repositories are loading",
		},
		repositoriesError: {
			control: "object",
			description: "Error that occurred while loading repositories",
		},
		addRepositoryError: {
			control: "object",
			description: "Error that occurred while adding a repository",
		},
		isAddingRepository: {
			control: "boolean",
			description: "Whether a repository is currently being added",
		},
		isRemovingRepository: {
			control: "boolean",
			description: "Whether a repository is currently being removed",
		},
		isResettingLeagues: {
			control: "boolean",
			description: "Whether leagues are currently being reset",
		},
		onAddRepository: {
			description: "Function called when adding a repository",
		},
		onRemoveRepository: {
			description: "Function called when removing a repository",
		},
		onResetLeagues: {
			description: "Function called when resetting leagues",
		},
	},
	args: {
		repositories: [
			{ nameWithOwner: "octocat/Hello-World" },
			{ nameWithOwner: "microsoft/vscode" },
			{ nameWithOwner: "facebook/react" },
		],
		isLoadingRepositories: false,
		repositoriesError: null,
		addRepositoryError: null,
		isAddingRepository: false,
		isRemovingRepository: false,
		isResettingLeagues: false,
		onAddRepository: fn(),
		onRemoveRepository: fn(),
		onResetLeagues: fn(),
	},
} satisfies Meta<typeof AdminSettingsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state of the admin settings page
 */
export const Default: Story = {};

/**
 * Loading state while fetching repositories
 */
export const LoadingRepositories: Story = {
	args: {
		isLoadingRepositories: true,
		repositories: [],
	},
};

/**
 * Error state when failing to load repositories
 */
export const RepositoriesError: Story = {
	args: {
		repositoriesError: new Error("Failed to load repositories"),
		repositories: [],
	},
};

/**
 * Adding a repository loading state
 */
export const AddingRepository: Story = {
	args: {
		isAddingRepository: true,
	},
};

/**
 * League reset loading state
 */
export const ResettingLeagues: Story = {
	args: {
		isResettingLeagues: true,
	},
};
