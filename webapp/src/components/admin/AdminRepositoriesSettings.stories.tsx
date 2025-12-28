import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { AdminRepositoriesSettings } from "./AdminRepositoriesSettings";

/**
 * Component for managing monitored repositories in admin settings
 */
const meta = {
	component: AdminRepositoriesSettings,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	argTypes: {
		isLoading: {
			control: "boolean",
			description: "Whether the repositories are loading",
		},
		error: {
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
		isReadOnly: {
			control: "boolean",
			description:
				"Whether repository management is disabled (for GitHub App Installation workspaces)",
		},
	},
	args: {
		repositories: [
			{ nameWithOwner: "octocat/Hello-World" },
			{ nameWithOwner: "microsoft/vscode" },
			{ nameWithOwner: "facebook/react" },
		],
		isLoading: false,
		error: null,
		addRepositoryError: null,
		isAddingRepository: false,
		isRemovingRepository: false,
		isReadOnly: false,
		onAddRepository: fn(),
		onRemoveRepository: fn(),
	},
} satisfies Meta<typeof AdminRepositoriesSettings>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Default state with a list of repositories
 */
export const Default: Story = {};

/**
 * Loading state while fetching repositories
 */
export const Loading: Story = {
	args: {
		repositories: [],
		isLoading: true,
	},
};

/**
 * Error state when failing to load repositories
 */
export const LoadError: Story = {
	args: {
		repositories: [],
		isLoading: false,
		error: new Error("Failed to load repositories"),
	},
};

/**
 * Error state when failing to add a repository
 */
export const AddError: Story = {
	args: {
		addRepositoryError: new Error("Failed to add repository"),
	},
};

/**
 * Loading state when adding a repository
 */
export const AddingRepository: Story = {
	args: {
		isAddingRepository: true,
	},
};

/**
 * Empty state with no repositories
 */
export const Empty: Story = {
	args: {
		repositories: [],
	},
};

/**
 * Read-only state for GitHub App Installation managed workspaces.
 * Repository add/remove controls are hidden and an info message is shown.
 */
export const ReadOnly: Story = {
	args: {
		isReadOnly: true,
	},
};
