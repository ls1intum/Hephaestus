/**
 * Open text survey question for collecting free-form feedback.
 */
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { QuestionOpenText } from "./question-open-text";

const meta = {
	component: QuestionOpenText,
	tags: ["autodocs"],
	args: {
		id: "open-text-demo",
		question: "Tell us about your experience with the product.",
		description: "Specific examples help us improve faster.",
		descriptionContentType: "text",
		required: true,
		value: "",
		error: undefined,
		onChange: fn(),
	},
	argTypes: {
		descriptionContentType: {
			control: "select",
			options: ["text", "html"],
		},
		required: { control: "boolean" },
		value: { control: "text" },
		error: { control: "text" },
	},
	parameters: {
		layout: "centered",
	},
} satisfies Meta<typeof QuestionOpenText>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Default state prompting for detailed qualitative feedback.
 */
export const Default: Story = {};

/**
 * Illustrates optional questions with existing feedback in place.
 */
export const Prefilled: Story = {
	args: {
		required: false,
		value: "I love the new debugging experience!",
	},
};

/**
 * Highlights validation state when the user submits an empty answer.
 */
export const WithError: Story = {
	args: {
		error: "Please share a quick note before you continue.",
	},
};
