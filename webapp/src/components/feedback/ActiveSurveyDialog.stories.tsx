import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, waitFor, within } from "storybook/test";

import { ActiveSurveyDialog } from "./ActiveSurveyDialog";

const meta = {
	title: "Surveys/Active survey dialog",
	component: ActiveSurveyDialog,
	args: {
		survey: {
			id: "11111111-1111-1111-1111-111111111111",
			title: "Help improve Hephaestus",
			description: "Tell the instance administrators what would make reviews more useful.",
			questions: [
				{ id: "useful", prompt: "What should improve?", type: "TEXT", options: [], required: true },
			],
			startsAt: new Date("2026-01-01T00:00:00Z"),
			active: true,
		},
		isSubmitting: false,
		isDismissing: false,
		onSubmit: fn(),
		onDismiss: fn(),
	},
	tags: ["autodocs"],
} satisfies Meta<typeof ActiveSurveyDialog>;

export default meta;
type Story = StoryObj<typeof meta>;

export const RequiredAnswer: Story = {
	play: async ({ args }) => {
		const dialog = within(await within(document.body).findByRole("dialog"));
		const submit = dialog.getByRole("button", { name: "Submit" });
		await expect(submit).toBeDisabled();
		await userEvent.type(
			dialog.getByRole("textbox", { name: "What should improve?" }),
			"Clearer feedback",
		);
		await userEvent.click(submit);
		await expect(args.onSubmit).toHaveBeenCalledWith({ useful: "Clearer feedback" });
	},
};

export const RatingQuestion: Story = {
	args: {
		survey: {
			...meta.args.survey,
			questions: [
				{
					id: "rating",
					prompt: "How useful are reviews?",
					type: "RATING",
					options: [],
					required: true,
				},
			],
		},
	},
	play: async ({ args }) => {
		const dialog = within(await within(document.body).findByRole("dialog"));
		// The group is named by its question legend, not left as an anonymous radiogroup.
		const group = dialog.getByRole("radiogroup", { name: /How useful are reviews\?/ });
		await userEvent.click(within(group).getByRole("radio", { name: "4" }));
		await userEvent.click(dialog.getByRole("button", { name: "Submit" }));
		await expect(args.onSubmit).toHaveBeenCalledWith({ rating: "4" });
	},
};

export const EscapeSnoozesWithoutDismissing: Story = {
	play: async ({ args }) => {
		await within(document.body).findByRole("dialog");
		await userEvent.keyboard("{Escape}");
		// Escape means "not now": the dialog hides until the next page load, but the permanent
		// "Don't ask me again" dismissal must never be recorded from an accidental close.
		await waitFor(() =>
			expect(within(document.body).queryByRole("dialog")).not.toBeInTheDocument(),
		);
		await expect(args.onDismiss).not.toHaveBeenCalled();
	},
};
