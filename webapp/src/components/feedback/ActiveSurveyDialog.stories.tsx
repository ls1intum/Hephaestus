import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, userEvent, within } from "storybook/test";

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
