import type { Decorator, Meta, StoryObj } from "@storybook/react";
import { fn } from "storybook/test";
import type { SurveyQuestion as SurveyQuestionType } from "@/types/survey";
import { SurveyQuestion } from "./survey-question";

const withContainer: Decorator = (StoryComponent) => (
	<div className="w-full max-w-md">
		<StoryComponent />
	</div>
);

const meta = {
	title: "Surveys/SurveyQuestion",
	component: SurveyQuestion,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		error: undefined,
		question: {
			id: "open-1",
			type: "open",
			question: "What can we do to improve your experience?",
			description: "Tell us anything that's on your mind.",
			required: true,
		} satisfies SurveyQuestionType,
		value: undefined,
		onChange: fn(),
	},
	argTypes: {
		question: { control: false },
		error: { control: "text" },
		value: { control: false },
		onChange: { control: false },
	},
	decorators: [withContainer],
} satisfies Meta<typeof SurveyQuestion>;

export default meta;

type Story = StoryObj<typeof meta>;

export const OpenText: Story = {};

export const LinkQuestion: Story = {
	args: {
		question: {
			id: "link-1",
			type: "link",
			question: "Read our changelog?",
			description: "You'll be redirected to the latest release notes.",
			required: false,
			buttonText: "Open changelog",
			linkUrl: "https://posthog.com/changelog",
		},
		value: "",
	},
};

export const RatingNumbers: Story = {
	args: {
		question: {
			id: "rating-1",
			type: "rating",
			question: "How satisfied are you with Hephaestus?",
			description: "10 means we're exceeding expectations.",
			required: true,
			scale: 10,
			display: "number",
			lowerBoundLabel: "Needs work",
			upperBoundLabel: "Outstanding",
		},
		value: 7,
	},
};

export const RatingEmoji: Story = {
	args: {
		question: {
			id: "rating-2",
			type: "rating",
			question: "How does this feature make you feel?",
			description: "Pick the emoji that matches your mood.",
			required: true,
			scale: 5,
			display: "emoji",
		},
		value: 3,
	},
};

export const SingleChoice: Story = {
	args: {
		question: {
			id: "single-1",
			type: "single_choice",
			question: "What's your primary use case?",
			description: "Pick the option that matches best.",
			required: true,
			choices: ["Personal projects", "Professional work", "Team collaboration", "Learning"],
		},
		value: "",
	},
};

export const MultipleChoice: Story = {
	args: {
		question: {
			id: "multi-1",
			type: "multiple_choice",
			question: "Which features do you use most often?",
			description: "Select all that apply.",
			required: false,
			choices: [
				"Analytics dashboard",
				"User surveys",
				"Session recordings",
				"Feature flags",
				"A/B testing",
			],
		},
		value: [],
	},
};
