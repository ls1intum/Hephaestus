import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";

import { ConsentDialog } from "./ConsentDialog";

const notice = {
	completed: false,
	noticeVersion: "2026-09-01",
	participateInResearch: false,
	noticeText: [
		"Hephaestus reads the work you already do in GitHub, GitLab, Slack and Outline, and reviews it against the practices your team has chosen.",
		"It stores the work it reviewed, the observations it recorded, and the feedback it wrote for you. Nobody outside your workspace sees any of it.",
		"You can export or delete everything from your settings at any time.",
	].join("\n\n"),
};

const meta = {
	component: ConsentDialog,
	args: { notice, onSubmit: fn(), onSignOut: fn(), onRetry: fn() },
	parameters: { layout: "fullscreen" },
} satisfies Meta<typeof ConsentDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

/** Nothing can be submitted until the required acceptance is given. */
export const RequiresAcceptance: Story = {
	play: async ({ canvas, args }) => {
		const dialog = within(await canvas.findByRole("dialog"));
		const submit = dialog.getByRole("button", { name: "Continue" });
		await expect(submit).toBeDisabled();

		await userEvent.click(dialog.getByRole("checkbox", { name: /terms of use/i }));
		await expect(submit).toBeEnabled();
		await userEvent.click(submit);
		await expect(args.onSubmit).toHaveBeenCalledWith({
			noticeVersion: "2026-09-01",
			termsAccepted: true,
			participateInResearch: false,
		});
	},
};

/** The optional choice is off until it is chosen, and never gates the button. */
export const ResearchIsOptional: Story = {
	play: async ({ canvas, args }) => {
		const dialog = within(await canvas.findByRole("dialog"));
		await expect(dialog.getByRole("checkbox", { name: /research/i })).not.toBeChecked();

		await userEvent.click(dialog.getByRole("checkbox", { name: /terms of use/i }));
		await userEvent.click(dialog.getByRole("checkbox", { name: /research/i }));
		await userEvent.click(dialog.getByRole("button", { name: "Continue" }));
		await expect(args.onSubmit).toHaveBeenCalledWith(
			expect.objectContaining({ participateInResearch: true }),
		);
	},
};

export const Submitting: Story = { args: { submitting: true } };

/** The choice reached the server and was refused; the reader is told and can retry. */
export const SubmitFailed: Story = {
	args: { failedToSubmit: true },
	play: async ({ canvas }) => {
		const dialog = within(await canvas.findByRole("dialog"));
		await expect(dialog.getByRole("alert")).toHaveTextContent(/wasn't saved/i);
	},
};

/** Still open while the notice loads, so the application never paints behind it first. */
export const Loading: Story = { args: { notice: undefined } };

/** The notice could not be fetched. The reader stays blocked, because the choice is still required. */
export const FailedToLoad: Story = {
	args: { notice: undefined, failedToLoad: true },
	play: async ({ canvas }) => {
		const dialog = within(await canvas.findByRole("dialog"));
		await expect(dialog.getByRole("alert")).toHaveTextContent(/couldn't load/i);
	},
};

/** A mandatory dialog still needs a way out: declining is an answer, not a dead end. */
export const CanDeclineAndSignOut: Story = {
	play: async ({ canvas, args }) => {
		const dialog = within(await canvas.findByRole("dialog"));
		await userEvent.click(dialog.getByRole("button", { name: /sign out/i }));
		await expect(args.onSignOut).toHaveBeenCalled();
	},
};

/** The load failure offers a retry rather than stranding the reader on a reload instruction. */
export const FailedToLoadCanRetry: Story = {
	args: { notice: undefined, failedToLoad: true },
	play: async ({ canvas, args }) => {
		const dialog = within(await canvas.findByRole("dialog"));
		await userEvent.click(dialog.getByRole("button", { name: /try again/i }));
		await expect(args.onRetry).toHaveBeenCalled();
	},
};
