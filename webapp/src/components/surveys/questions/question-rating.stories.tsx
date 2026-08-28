/**
 * Rating survey question capturing scaled feedback with number or emoji display.
 */
import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn } from "storybook/test";

import { QuestionRating } from "./question-rating";

const meta = {
	component: QuestionRating,
	tags: ["autodocs"],
	args: {
		id: "rating-demo",
		question: "How likely are you to recommend us to a friend?",
		description: "1 means not at all likely; the max means very likely.",
		descriptionContentType: "text",
		required: true,
		display: "number" as const,
		scale: 5,
		lowerBoundLabel: "Unlikely",
		upperBoundLabel: "Very likely",
		value: 4,
		error: undefined,
		onChange: fn(),
	},
	argTypes: {
		display: {
			control: "select",
			options: ["number", "emoji"],
		},
		scale: { control: { type: "number", min: 2, max: 10, step: 1 } },
		lowerBoundLabel: { control: "text" },
		upperBoundLabel: { control: "text" },
		value: { control: "number" },
		error: { control: "text" },
	},
	parameters: {
		layout: "centered",
	},
} satisfies Meta<typeof QuestionRating>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Standard five-point number scale with descriptive bounds. Which rating is selected (4) reads from
 * the attribute Base UI puts on the pressed item; how it then looks is the kit's, styled once in
 * `ui/toggle.tsx` and covered by `ui/toggle.stories.tsx`. Nothing here restyles the pressed state —
 * a scale whose selection has to be repainted locally is a scale whose kit is broken.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		const selected = canvas.getByRole("button", { name: "Rating 4 out of 5" });
		await expect(selected).toHaveAttribute("data-pressed");
		const unselected = canvas.getByRole("button", { name: "Rating 3 out of 5" });
		await expect(unselected).not.toHaveAttribute("data-pressed");
	},
};

/**
 * Emoji display with three-point scale for rapid sentiment capture.
 */
export const EmojiScale: Story = {
	args: {
		display: "emoji",
		scale: 3,
		value: 2,
	},
};

/**
 * Shows validation messaging when a response is required but missing.
 */
export const WithError: Story = {
	args: {
		value: undefined,
		error: "Please share a rating before continuing.",
	},
};
