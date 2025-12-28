/**
 * Link survey question for routing respondents to external resources or follow-up flows.
 */
import type { Meta, StoryObj } from "@storybook/react-vite";
import { fn } from "storybook/test";
import { QuestionLink } from "./question-link";

const meta = {
	component: QuestionLink,
	tags: ["autodocs"],
	args: {
		id: "link-demo",
		question: "Want to learn more about our roadmap?",
		description: "We will open the link in a new tab so you can explore and come back.",
		descriptionContentType: "text",
		required: false,
		buttonText: "Read the roadmap",
		linkUrl: "https://example.com/roadmap",
		value: "",
		error: undefined,
		onChange: fn(),
	},
	argTypes: {
		descriptionContentType: {
			control: "select",
			options: ["text", "html"],
		},
		buttonText: { control: "text" },
		linkUrl: { control: "text" },
		required: { control: "boolean" },
		value: { control: "text" },
		error: { control: "text" },
	},
	parameters: {
		layout: "centered",
	},
} satisfies Meta<typeof QuestionLink>;

export default meta;

type Story = StoryObj<typeof meta>;

/**
 * Primary path encouraging respondents to view an external resource.
 */
export const Default: Story = {};

/**
 * Illustrates how the component looks after the respondent has clicked the link.
 */
export const AlreadyVisited: Story = {
	args: {
		value: "link_clicked",
	},
};

/**
 * Shows validation messaging when the click is required to advance.
 */
export const WithError: Story = {
	args: {
		required: true,
		error: "Open the linked resource before continuing.",
	},
};
