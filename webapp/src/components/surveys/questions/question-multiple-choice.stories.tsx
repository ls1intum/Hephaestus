/**
 * Multiple choice survey question allowing several selections plus an optional open response.
 */
import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import { QuestionMultipleChoice } from "./question-multiple-choice";

const meta = {
	component: QuestionMultipleChoice,
	tags: ["autodocs"],
	args: {
		id: "multiple-choice-demo",
		question: "Which types of content would you like to see more of?",
		description: "Pick all that resonate with you.",
		descriptionContentType: "text",
		required: false,
		choices: ["Tutorials", "Customer case studies", "Product announcements", "Other"],
		hasOpenChoice: true,
		value: [] as string[],
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
		value: { control: "object" },
		error: { control: "text" },
	},
	parameters: {
		layout: "centered",
	},
} satisfies Meta<typeof QuestionMultipleChoice>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Default state with a free-form "Other" choice available.
 */
export const Default: Story = {};

/**
 * Demonstrates multiple selections and a populated open response.
 */
export const WithSelections: Story = {
	args: {
		value: ["Tutorials", "Other"],
	},
};

/**
 * Highlights validation messaging when at least one selection is required.
 */
export const WithError: Story = {
	args: {
		required: true,
		error: "Select at least one option to continue.",
	},
};
