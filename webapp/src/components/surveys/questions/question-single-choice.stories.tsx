/**
 * Single choice survey question used for mutually exclusive answers with optional open response.
 */
import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { QuestionSingleChoice } from "./question-single-choice";

const meta = {
	component: QuestionSingleChoice,
	tags: ["autodocs"],
	args: {
		id: "single-choice-demo",
		question: "Have you found this tutorial useful?",
		description: "We use this feedback to improve our onboarding material.",
		descriptionContentType: "text",
		required: true,
		choices: ["Yes", "No", "Other"],
		hasOpenChoice: true,
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
		hasOpenChoice: { control: "boolean" },
		choices: { control: "object" },
		error: { control: "text" },
		value: { control: "text" },
	},
	parameters: {
		layout: "centered",
	},
} satisfies Meta<typeof QuestionSingleChoice>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Default configuration with three options and an open answer card.
 */
export const Default: Story = {};

/**
 * Scenario without an open answer and an initial selection.
 */
export const Preselected: Story = {
	args: {
		hasOpenChoice: false,
		value: "Yes",
	},
};

/**
 * Demonstrates validation messaging when the selection is missing.
 */
export const WithError: Story = {
	args: {
		required: true,
		error: "Please choose an option before continuing.",
	},
};
