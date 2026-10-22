import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, screen, userEvent, waitFor, within } from "storybook/test";

import { expectSettledVisible } from "@/stories/overlay";
import { expectNoPageOverflow } from "@/stories/reflow";

import { WorkspaceGitLabProjectSelection } from "./WorkspaceGitLabProjectSelection";

const unavailable = Object.assign(new Error("request failed"), {
	status: 503,
	detail: "GitLab is unavailable.",
});

const meta = {
	component: WorkspaceGitLabProjectSelection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		selection: "ALL",
		isLoading: false,
		error: null,
		isSaving: false,
		saveError: null,
		onRetry: fn(),
		onSelectionChange: fn(async () => true),
	},
} satisfies Meta<typeof WorkspaceGitLabProjectSelection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const AllProjects: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("radio", { name: "All projects in the group" })).toBeChecked();
		await expect(
			canvas.getByRole("radio", { name: "Only the projects listed below" }),
		).not.toBeChecked();
		canvas.getByText(/members and teams still come from the whole group/iu);
		canvas.getByText(/Projects you stopped stay stopped/u);
	},
};

export const SelectedProjects: Story = {
	args: { selection: "SELECTED" },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("radio", { name: "Only the projects listed below" }),
		).toBeChecked();
		canvas.getByText(/New projects in the group are not added/u);
		canvas.getByText(/To change the list, use Manage repositories below\./u);
	},
};

/** The choice's shape before it reads: no option is offered, and none is shown as chosen. */
export const Loading: Story = {
	args: { selection: undefined, isLoading: true },
	play: async ({ canvas }) => {
		canvas.getByText("Loading the project choice…");
		await expect(canvas.queryByRole("radio")).not.toBeInTheDocument();
	},
};

/** Saving keeps the stored choice shown and accepts no second change until the server answers. */
export const Saving: Story = {
	args: { selection: "ALL", isSaving: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("status")).toHaveTextContent("Saving the project choice…");
		await expect(canvas.getByRole("radio", { name: "All projects in the group" })).toBeChecked();
		// The radio is a `<span role="radio">`; Base UI marks a disabled one with `data-disabled`.
		await expect(
			canvas.getByRole("radio", { name: "Only the projects listed below" }),
		).toHaveAttribute("data-disabled");
	},
};

export const LoadError: Story = {
	args: { selection: undefined, error: unavailable },
	play: async ({ canvas }) => {
		canvas.getByText("We could not load the project choice");
		canvas.getByText(/GitLab is unavailable/u);
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
		await expect(canvas.queryByRole("radio")).not.toBeInTheDocument();
	},
};

/** A refused read offers no choice and no Retry. */
export const LoadRefused: Story = {
	args: {
		selection: undefined,
		error: Object.assign(new Error("request failed"), {
			status: 403,
			detail: "You cannot manage this workspace’s source control.",
		}),
	},
	play: async ({ canvas }) => {
		canvas.getByText("You do not have access to the project choice");
		await expect(canvas.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
		await expect(canvas.queryByRole("radio")).not.toBeInTheDocument();
	},
};

/** A failed refresh keeps the choice last read, still changeable, beside the failure. */
export const RefreshFailed: Story = {
	args: {
		selection: "SELECTED",
		refreshFailure: { error: unavailable, pending: false, onRetry: fn() },
	},
	play: async ({ canvas }) => {
		canvas.getByText("The project choice may be out of date");
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
		await expect(
			canvas.getByRole("radio", { name: "Only the projects listed below" }),
		).toBeChecked();
		await expect(
			canvas.queryByText("We could not load the project choice"),
		).not.toBeInTheDocument();
	},
};

/** A refused change leaves the stored choice in place and says why, so choosing again retries it. */
export const SaveFailed: Story = {
	args: {
		selection: "ALL",
		saveError: Object.assign(new Error("request failed"), {
			status: 409,
			detail:
				"The GitLab connection, project selection or monitored projects changed while we checked them. Try again.",
		}),
	},
	play: async ({ canvas }) => {
		canvas.getByText("We could not change the project choice");
		canvas.getByText(/changed while we checked them\. Try again\./u);
		await expect(canvas.getByRole("radio", { name: "All projects in the group" })).toBeChecked();
		await expect(
			canvas.getByRole("radio", { name: "Only the projects listed below" }),
		).not.toHaveAttribute("data-disabled");
	},
};

/** Widening asks first, and says that stopped projects stay stopped. */
export const WideningConfirmation: Story = {
	args: { selection: "SELECTED" },
	play: async ({ canvas }) => {
		// Taken before the modal opens: the open dialog makes the page behind it inert.
		const selected = canvas.getByRole("radio", { name: "Only the projects listed below" });
		await userEvent.click(canvas.getByRole("radio", { name: "All projects in the group" }));

		const dialog = await screen.findByRole("alertdialog", {
			name: "Add new projects from the group?",
		});
		await expectSettledVisible(dialog);
		within(dialog).getByText(/including projects created later/u);
		within(dialog).getByText(/Projects you stopped stay stopped until you add them again/u);
		await expect(within(dialog).getByRole("button", { name: "Add new projects" })).toBeEnabled();
		await expect(selected).toBeChecked();
	},
};

/** Cancel closes the question and leaves the stored choice as it was. */
export const WideningCancelled: Story = {
	args: { selection: "SELECTED" },
	play: async ({ canvas }) => {
		await userEvent.click(canvas.getByRole("radio", { name: "All projects in the group" }));
		const dialog = await screen.findByRole("alertdialog");
		await expectSettledVisible(dialog);
		await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

		await waitFor(async () => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
		await expect(
			canvas.getByRole("radio", { name: "Only the projects listed below" }),
		).toBeChecked();
		await expect(
			canvas.getByRole("radio", { name: "All projects in the group" }),
		).not.toBeChecked();
	},
};

/** The narrowest supported width: every line wraps inside the card instead of widening the page. */
export const Phone320: Story = {
	args: {
		selection: "SELECTED",
		saveError: Object.assign(new Error("request failed"), {
			status: 409,
			detail:
				"The GitLab connection, project selection or monitored projects changed while we checked them. Try again.",
		}),
	},
	globals: { viewport: { value: "reflow", isRotated: false } },
	parameters: { chromatic: { viewports: [320] } },
	play: async ({ canvas }) => {
		await expect(document.documentElement.clientWidth).toBe(320);
		await expect(window.innerWidth).toBe(320);
		await expectNoPageOverflow();
		const width = document.documentElement.clientWidth;
		for (const name of ["All projects in the group", "Only the projects listed below"]) {
			const label = canvas.getByText(name);
			await expect(label.scrollWidth).toBeLessThanOrEqual(label.clientWidth);
			await expect(label.getBoundingClientRect().right).toBeLessThanOrEqual(width);
		}
		const explanation = canvas.getByText(/members and teams still come from the whole group/iu);
		await expect(explanation.getBoundingClientRect().right).toBeLessThanOrEqual(width);
	},
};
