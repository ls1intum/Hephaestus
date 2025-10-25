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
 * Walks through the feedback survey using the default layout.
 */
export const Default: Story = {
	args: {},
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
 */
export const ResponseBasedBranching: Story = {
	args: {
		onProgress: fn((responses, meta) => {
			console.info("Progress updated", { responses, meta });
		}),
	},
};
