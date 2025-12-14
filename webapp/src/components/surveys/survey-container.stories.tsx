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

/**
 * Survey with diverse question types and response-based branching.
 * Tests: open text, single choice with branching, rating scale, multiple choice.
 */
const branchingSurvey: PostHogSurvey = {
	id: "019a1bba-e554-0000-ba61-39946d83e49c",
	name: "Open feedback",
	description: "",
	type: "api",
	questions: [
		{
			id: "a5fee21d-eeaf-475a-9849-1f908e28fa79",
			type: "open",
			question: "What can we do to improve our product?",
			description: "",
			descriptionContentType: "text",
			required: false,
		},
		{
			id: "4af1e564-2a8b-4d6f-8e5a-13708bdfc551",
			type: "single_choice",
			choices: ["Yes", "No", "Other"],
			question: "Have you found this tutorial useful?",
			description: "",
			descriptionContentType: "text",
			required: true,
			hasOpenChoice: true,
			buttonText: "Submit",
			branching: {
				type: "response_based",
				responseValues: {
					Yes: "045bc9da-bdd7-4d93-8700-a7604aff9e94",
					No: "077bc1cc-85e5-4a3a-9283-b386ecf2d299",
					__other__: "077bc1cc-85e5-4a3a-9283-b386ecf2d299",
					default: "045bc9da-bdd7-4d93-8700-a7604aff9e94",
				},
			},
		},
		{
			id: "045bc9da-bdd7-4d93-8700-a7604aff9e94",
			type: "rating",
			scale: 10,
			display: "number",
			question: "How likely are you to recommend us to a friend?",
			description: "",
			descriptionContentType: "text",
			required: true,
			buttonText: "Submit",
			lowerBoundLabel: "Unlikely",
			upperBoundLabel: "Very likely",
		},
		{
			id: "077bc1cc-85e5-4a3a-9283-b386ecf2d299",
			type: "multiple_choice",
			choices: [
				"Tutorials",
				"Customer case studies",
				"Product announcements",
				"Other",
			],
			question: "Which types of content would you like to see more of?",
			description: "",
			descriptionContentType: "text",
			required: false,
			hasOpenChoice: true,
			buttonText: "Submit",
		},
	],
	conditions: null,
	start_date: "2025-10-25T14:17:09.855000Z",
	end_date: null,
	enable_partial_responses: true,
	current_iteration: 1,
	current_iteration_start_date: new Date().toISOString(),
};

/** Production Hephaestus feedback survey - all open-text questions */
const hephaestusFeedbackSurvey: PostHogSurvey = {
	id: "019b03ae-f2e3-0000-dadd-f988e31a45fa",
	name: "Help us improve Hephaestus!",
	description: "",
	type: "api",
	questions: [
		{
			id: "e72a329b-e4c2-4fc0-bf22-9d7f651d8d4a",
			type: "open",
			question:
				"What specific changes to the Leaderboard (e.g., scoring logic, league thresholds, visibility) would make the competition feel fairer or more motivating for you?",
			buttonText: "Submit",
			description: "",
			descriptionContentType: "text",
			required: false,
		},
		{
			id: "65ae02ef-c975-4f9a-b124-5c856514da27",
			type: "open",
			question:
				"What specific information or guidance is currently missing from your Profile page that you wish you could see to track your progress?",
			buttonText: "Submit",
			description: "",
			descriptionContentType: "text",
			required: false,
		},
		{
			id: "bf45ae86-3980-469e-8b5b-0ca3568ec938",
			type: "open",
			question:
				"Which page (Leaderboard vs. Profile) would you prefer as your default landing view upon login, and specifically how does that view better support your decision on what to do next?",
			buttonText: "Submit",
			description: "",
			descriptionContentType: "text",
			required: false,
		},
		{
			id: "d843c301-5a50-4f1d-a935-223084684d70",
			type: "open",
			question:
				"What other specific aspects of the Hephaestus experience (e.g., bugs, usability friction, or new feature ideas) have we not covered that you believe are critical for improving the platform?",
			buttonText: "Submit",
			description: "",
			descriptionContentType: "text",
			required: false,
		},
	],
	conditions: null,
	start_date: "2025-12-09T15:17:30.991000Z",
	end_date: null,
	enable_partial_responses: true,
	current_iteration: 1,
	current_iteration_start_date: new Date().toISOString(),
};

/**
 * SurveyContainer renders multi-step PostHog API surveys with branching logic and progress handling.
 */
const meta = {
	title: "Surveys/SurveyContainer",
	component: SurveyContainer,
	parameters: { layout: "centered" },
	tags: ["autodocs"],
	args: {
		survey: branchingSurvey,
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

/**
 * Default survey with diverse question types and branching logic.
 */
export const Default: Story = {
	args: {},
};

/**
 * Production Hephaestus feedback survey with all open-text questions.
 */
export const HephaestusFeedback: Story = {
	args: {
		survey: hephaestusFeedbackSurvey,
	},
};

/**
 * Presents the survey inside a floating popover for contextual collection.
 */
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

/**
 * Highlights response-based branching by logging the active question after each response.
 * Uses the branching survey to demonstrate different paths based on user answers.
 */
export const ResponseBasedBranching: Story = {
	args: {
		survey: branchingSurvey,
		onProgress: fn((responses, meta) => {
			console.info("Progress updated", { responses, meta });
		}),
	},
};
