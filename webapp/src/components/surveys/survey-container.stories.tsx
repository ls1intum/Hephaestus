import type { Meta, StoryObj } from "@storybook/react";
import { fn } from "@storybook/test";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
	Popover,
	PopoverContent,
	PopoverTrigger,
} from "@/components/ui/popover";
import type { PostHogSurvey } from "@/types/survey";
import { SurveyContainer } from "./survey-container";

const mockSurvey: PostHogSurvey = {
	id: "demo-survey-001",
	name: "Product Feedback Survey",
	description: "Help us improve your experience",
	type: "api",
	questions: [
		{
			id: "q1",
			type: "rating",
			question: "How would you rate your overall experience?",
			description: "Your honest feedback helps us improve",
			required: true,
			scale: 10,
			display: "number",
			lowerBoundLabel: "Poor",
			upperBoundLabel: "Excellent",
		},
		{
			id: "q2",
			type: "rating",
			question: "How do you feel about our product?",
			description: "Select the emoji that best represents your feeling",
			required: true,
			scale: 5,
			display: "emoji",
		},
		{
			id: "q3",
			type: "single_choice",
			question: "What is your primary use case?",
			description:
				"Select the option that best describes how you use our product",
			required: true,
			choices: [
				"Personal projects",
				"Professional work",
				"Team collaboration",
				"Learning and education",
				"Other",
			],
			hasOpenChoice: true,
		},
		{
			id: "q4",
			type: "multiple_choice",
			question: "Which features do you use most often?",
			description: "Select all that apply",
			required: false,
			choices: [
				"Analytics dashboard",
				"User surveys",
				"Session recordings",
				"Feature flags",
				"A/B testing",
				"Heatmaps",
				"Other",
			],
			hasOpenChoice: true,
		},
		{
			id: "q5",
			type: "open",
			question: "What can we do to improve your experience?",
			description: "Share any suggestions, feedback, or ideas",
			required: false,
		},
		{
			id: "q6",
			type: "link",
			question: "Would you like to join our beta program?",
			description:
				"Get early access to new features and help shape the product",
			required: false,
			buttonText: "Join Beta Program",
			linkUrl: "https://posthog.com/signup",
		},
	],
	conditions: undefined,
	start_date: null,
	end_date: null,
	enable_partial_responses: true,
	current_iteration: 1,
	current_iteration_start_date: new Date().toISOString(),
};

const meta = {
	title: "Surveys/SurveyContainer",
	component: SurveyContainer,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		survey: mockSurvey,
		onComplete: fn(),
		onDismiss: fn(),
		onProgress: fn(),
	},
	argTypes: {
		survey: { control: false },
		onComplete: { control: false },
		onDismiss: { control: false },
		onProgress: { control: false },
	},
} satisfies Meta<typeof SurveyContainer>;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
	args: {},
};

export const InPagePopover: Story = {
	parameters: {
		layout: "fullscreen",
	},
	render: (args) => {
		const [open, setOpen] = useState(true);

		return (
			<div className="relative flex min-h-[480px] w-full justify-center bg-background sm:min-h-[600px]">
				<Popover open={open} onOpenChange={setOpen} modal={false}>
					<PopoverTrigger asChild>
						<Button className="absolute bottom-4 right-4 z-20 shadow-lg sm:bottom-6 sm:right-6">
							Share feedback
						</Button>
					</PopoverTrigger>
					<PopoverContent
						side="top"
						align="end"
						sideOffset={16}
						className="pointer-events-auto w-[min(90vw,420px)] border border-border bg-background p-0 shadow-xl"
					>
						<SurveyContainer
							{...args}
							onDismiss={(step) => {
								args.onDismiss?.(step);
								setOpen(false);
							}}
							onComplete={(responses) => {
								args.onComplete?.(responses);
								setOpen(false);
							}}
						/>
					</PopoverContent>
				</Popover>
			</div>
		);
	},
};
