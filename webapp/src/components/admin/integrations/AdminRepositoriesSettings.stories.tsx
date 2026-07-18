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
	},
	args: {
		repositories: [
			{ nameWithOwner: "octocat/Hello-World" },
			{ nameWithOwner: "microsoft/vscode" },
			{ nameWithOwner: "facebook/react" },
		],
		providerLabel: "GitHub",
		isLoading: false,
		error: null,
		addRepositoryError: null,
		isAddingRepository: false,
		isRemovingRepository: false,
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
 * A large fleet. The list is a compact pane that grows to content for a few repos but caps and scrolls
 * for many, so it never becomes a second full-height copy of the sync-state table nor overflows onto
 * the add form. The play asserts the real layout in browser mode: the Base UI ScrollArea viewport is
 * height-bounded (≤ the max-h-80 cap) and its content overflows it (scrollable) — the exact regression
 * that shipped when the viewport was left unbounded and every row rendered at full height.
 */
export const ManyRepositories: Story = {
	args: {
		repositories: Array.from({ length: 40 }, (_, index) => ({
			nameWithOwner: `ls1intum/repository-number-${index + 1}`,
		})),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);

		const viewport = canvasElement.querySelector<HTMLElement>('[data-slot="scroll-area-viewport"]');
		await expect(viewport).not.toBeNull();
		if (!viewport) return;

		// Bounded: clipped to the max-h-80 (20rem = 320px) cap, not grown to fit all 40 rows.
		await expect(viewport.clientHeight).toBeLessThanOrEqual(320);
		// Scrollable: the 40 rows overflow the bounded viewport rather than pushing the page taller.
		await expect(viewport.scrollHeight).toBeGreaterThan(viewport.clientHeight);

		// The add form still sits below the pane and is interactive — not overlapped by the list.
		await expect(canvas.getByLabelText("Add a repository")).toBeVisible();
	},
};

/**
 * Removing a repository is guarded by a destructive confirm dialog. The copy names WHAT is erased and
 * states the upstream is untouched and re-syncable.
 */
export const RemoveConfirm: Story = {
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /remove octocat\/Hello-World/i }));

		const dialog = await screen.findByRole("alertdialog");
		await expect(
			within(dialog).getByText(/stop monitoring octocat\/Hello-World/i),
		).toBeInTheDocument();
		await expect(
			within(dialog).getByText(/permanently erases everything Hephaestus has mirrored/i),
		).toBeInTheDocument();
		// Names the provider and says the upstream repository survives and can be re-monitored.
		await expect(
			within(dialog).getByText(/repository on GitHub itself is not affected/i),
		).toBeInTheDocument();
		await expect(within(dialog).getByText(/monitoring it again later/i)).toBeInTheDocument();
		await expect(within(dialog).getByRole("button", { name: /stop monitoring/i })).toBeEnabled();
	},
};

/**
 * A remove is in flight.
 *
 * The dialog is controlled and held open across the mutation: an uncontrolled `AlertDialogAction`
 * closes on click, unmounting the node that carries `disabled={isRemovingRepository}` before the flag
 * can flip. Holding it open gives the confirm somewhere to show "Stopping…" and gives a second click
 * something to bounce off.
 */
export const RemoveInProgress: Story = {
	args: {
		isRemovingRepository: true,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /remove microsoft\/vscode/i }));

		const dialog = await screen.findByRole("alertdialog");
		const confirm = within(dialog).getByRole("button", { name: /stopping/i });
		await expect(confirm).toBeDisabled();
		// Cancel is held too: dismissing mid-flight would strand a mutation the admin can't see.
		await expect(within(dialog).getByRole("button", { name: /cancel/i })).toBeDisabled();
	},
};

/**
 * The confirm survives the click and stays open while the mutation runs, so the pending state has
 * somewhere to live. Pressing it once fires exactly one remove.
 */
export const RemoveHoldsDialogOpen: Story = {
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: /remove facebook\/react/i }));

		const dialog = await screen.findByRole("alertdialog");
		await userEvent.click(within(dialog).getByRole("button", { name: /stop monitoring/i }));

		await expect(args.onRemoveRepository).toHaveBeenCalledWith("facebook/react");
		await expect(await screen.findByRole("alertdialog")).toBeInTheDocument();
	},
};

/**
 * Loading state. The placeholders carry the same `Item` chrome as the rows they become — border,
 * height, trailing action slot — rather than bare grey bars that resize the card on resolve.
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
		onRetry: fn(),
	},
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText(/couldn't load the monitored repositories/i)).toBeInTheDocument();
		await expect(canvas.getByText(/repositories service is unavailable/i)).toBeInTheDocument();
		// Every sibling section offers a retry.
		await userEvent.click(canvas.getByRole("button", { name: /retry/i }));
		await expect(args.onRetry).toHaveBeenCalledTimes(1);
	},
};

/**
 * The add mutation failed — the field surfaces the server's own reason under the input rather than a
 * fixed string, so the admin sees the one piece of information they can act on.
 */
export const AddValidationError: Story = {
	args: {
		addRepositoryError: Object.assign(new Error("request failed"), {
			status: 404,
			detail: "Repository owner/name was not found, or the token cannot see it.",
		}),
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(
			canvas.getByText(/was not found, or the token cannot see it/i),
		).toBeInTheDocument();
		await expect(
			canvas.queryByText(/an error occurred while adding the repository/i),
		).not.toBeInTheDocument();
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
