import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";
import { FeedbackComment } from "./FeedbackComment";

const meta = {
	title: "Profile/Review runs/Feedback comment",
	component: FeedbackComment,
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"The written half of a developer's response to feedback. The draft stays local until it is " +
					"saved, so abandoning it leaves the recorded comment untouched — and disputing an " +
					"observation is the one case where the server insists on an explanation.",
			},
		},
	},
	tags: ["autodocs"],
	args: { isRequired: false, onSave: fn() },
	decorators: [
		(Story) => (
			<div className="max-w-lg">
				<Story />
			</div>
		),
	],
} satisfies Meta<typeof FeedbackComment>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing written yet: the field is optional, and there is nothing to save or cancel. */
export const Empty: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Save comment" })).toBeDisabled();
		await expect(canvas.queryByRole("button", { name: "Cancel" })).toBeNull();
	},
};

/** A comment already recorded. Saving stays disabled until the text actually differs. */
export const Recorded: Story = {
	args: { comment: "Split into two commits so the reasoning reads on its own." },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Save comment" })).toBeDisabled();
	},
};

/** Typing offers both actions; cancelling puts the recorded text back rather than clearing it. */
export const Editing: Story = {
	args: { comment: "Split into two commits." },
	play: async ({ canvas, userEvent }) => {
		const field = canvas.getByRole("textbox");
		await userEvent.type(field, " The second one is the fix.");
		await expect(canvas.getByRole("button", { name: "Save comment" })).toBeEnabled();

		await userEvent.click(canvas.getByRole("button", { name: "Cancel" }));
		await expect(field).toHaveValue("Split into two commits.");
	},
};

/**
 * Disputing an observation: the server rejects an empty explanation, so the field says it is
 * required and saving stays out of reach until something is written.
 */
export const RequiredForDispute: Story = {
	args: { isRequired: true },
	play: async ({ args, canvas, userEvent }) => {
		const field = canvas.getByRole("textbox");
		await expect(field).toHaveAttribute("aria-invalid", "true");
		await expect(canvas.getByRole("button", { name: "Save comment" })).toBeDisabled();

		await userEvent.type(field, "The timeout was raised on purpose; the comment above says why.");
		await userEvent.click(canvas.getByRole("button", { name: "Save comment" }));
		await expect(args.onSave).toHaveBeenCalledWith(
			"The timeout was raised on purpose; the comment above says why.",
		);
	},
};

/** While the response is in flight every control is out of action, including cancel. */
export const Saving: Story = {
	args: { comment: "Handled in the follow-up.", isPending: true },
};
