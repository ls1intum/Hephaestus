import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, within } from "storybook/test";
import { AdminRepositoriesSettings } from "./AdminRepositoriesSettings";

/**
 * Admin surface for the monitored-repositories plane. Presentational: the add/remove mutations and
 * their loading/error flags are supplied by the container, so these stories mock the callbacks with
 * `fn()`. The stories pin every state the container can drive — populated, loading, empty, load
 * error, add-validation error, remove-in-progress, and the GitHub App Installation read-only mode —
 * plus the interaction gates (the `owner/name` add gate and the destructive remove confirm).
 */
const meta = {
	component: AdminRepositoriesSettings,
	parameters: { layout: "padded" },
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
 * Default state with a list of monitored repositories. The play drives the add gate: a value without
 * a `/` keeps Add disabled, while an `owner/name` value enables it and submits through the callback.
 */
export const Default: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		const input = canvas.getByLabelText("Add a repository");
		const addButton = canvas.getByRole("button", { name: /^add$/i });

		// Empty and slash-less input keep Add disabled.
		await expect(addButton).toBeDisabled();
		await userEvent.type(input, "not-a-repo");
		await expect(addButton).toBeDisabled();

		// A valid owner/name enables Add and submits.
		await userEvent.clear(input);
		await userEvent.type(input, "owner/name");
		await expect(addButton).toBeEnabled();
		await userEvent.click(addButton);
		await expect(args.onAddRepository).toHaveBeenCalledWith("owner/name");
	},
};

/**
 * Removing a repository is guarded by a destructive confirm dialog that spells out the consequence.
 */
export const RemoveConfirm: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /remove octocat\/Hello-World/i }));

		const dialog = await screen.findByRole("alertdialog");
		await expect(
			within(dialog).getByText(/stop monitoring octocat\/Hello-World/i),
		).toBeInTheDocument();
		await expect(within(dialog).getByText(/cannot be undone/i)).toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /stop monitoring/i })).toBeEnabled();
	},
};

/**
 * A remove is in flight — the confirm's destructive action is disabled until the mutation settles.
 */
export const RemoveInProgress: Story = {
	args: {
		isRemovingRepository: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /remove microsoft\/vscode/i }));

		const dialog = await screen.findByRole("alertdialog");
		await expect(within(dialog).getByRole("button", { name: /stop monitoring/i })).toBeDisabled();
	},
};

/**
 * Loading state — three skeleton rows while the repository list resolves.
 */
export const Loading: Story = {
	args: {
		repositories: [],
		isLoading: true,
	},
};

/**
 * Empty state — no repositories monitored yet; the empty affordance points at the add field.
 */
export const Empty: Story = {
	args: {
		repositories: [],
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/no repositories monitored yet/i)).toBeInTheDocument();
	},
};

/**
 * The repository-list query failed — the shared error alert, not the empty or populated state.
 */
export const LoadError: Story = {
	args: {
		repositories: [],
		isLoading: false,
		error: new Error("The repositories service is unavailable."),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/couldn't load the monitored repositories/i)).toBeInTheDocument();
		await expect(canvas.getByText(/repositories service is unavailable/i)).toBeInTheDocument();
	},
};

/**
 * The add mutation failed — the field surfaces the error under the input.
 */
export const AddValidationError: Story = {
	args: {
		addRepositoryError: new Error("Failed to add repository"),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(/an error occurred while adding the repository/i),
		).toBeInTheDocument();
	},
};

/**
 * A repository is being added — the Add button and input are disabled while the mutation runs.
 */
export const AddingRepository: Story = {
	args: {
		isAddingRepository: true,
	},
};

/**
 * GitHub App Installation managed workspace — the list is read-only, the managed-by alert shows, and
 * the manual add/remove controls are withheld.
 */
export const GitHubAppManaged: Story = {
	args: {
		isReadOnly: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/managed by a github app installation/i)).toBeInTheDocument();
		// No manual controls in read-only mode.
		await expect(canvas.queryByLabelText("Add a repository")).not.toBeInTheDocument();
		await expect(
			canvas.queryByRole("button", { name: /remove octocat\/Hello-World/i }),
		).not.toBeInTheDocument();
	},
};
