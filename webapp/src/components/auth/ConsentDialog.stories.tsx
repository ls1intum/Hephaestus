import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, userEvent, within } from "storybook/test";

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

export const RequiresAcceptance: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
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

export const ResearchIsOptional: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
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

export const SubmitFailed: Story = {
	args: { failedToSubmit: true },
	play: async () => {
		const dialog = within(await screen.findByRole("dialog"));
		await expect(dialog.getByRole("alert")).toHaveTextContent(/wasn't saved/i);
	},
};

export const FailedToLoad: Story = {
	args: { notice: undefined, failedToLoad: true },
	play: async () => {
		const dialog = within(await screen.findByRole("dialog"));
		await expect(dialog.getByRole("alert")).toHaveTextContent(/couldn't load/i);
	},
};

export const CanDeclineAndSignOut: Story = {
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.click(dialog.getByRole("button", { name: /sign out/i }));
		await expect(args.onSignOut).toHaveBeenCalled();
	},
};

export const FailedToLoadCanRetry: Story = {
	args: { notice: undefined, failedToLoad: true },
	play: async ({ args }) => {
		const dialog = within(await screen.findByRole("dialog"));
		await userEvent.click(dialog.getByRole("button", { name: /try again/i }));
		await expect(args.onRetry).toHaveBeenCalled();
	},
};
