import type { Meta, StoryObj } from "@storybook/react";
import { expect, fn, userEvent, within } from "storybook/test";
import type { PracticeReportCard } from "@/api/types.gen";
import { PracticeReflectionSection } from "./PracticeReflectionSection";

const practices: PracticeReportCard[] = [
	{
		name: "Write a clear pull request description",
		areaName: "Pull requests",
		slug: "clear-pr-description",
		status: "MIXED",
		trend: "STEADY",
		whyItMatters: "A clear description helps reviewers understand the change quickly.",
		strengths: [
			{
				artifactId: 42,
				artifactType: "PULL_REQUEST",
				observationId: "s1",
				title: "Explained the user-facing impact up front",
				guidance: "Reviewers get oriented fast.",
				locator: "PR #42",
				severity: "INFO",
			},
		],
		toWorkOn: [
			{
				artifactId: 42,
				artifactType: "PULL_REQUEST",
				observationId: "w1",
				title: "Link the issue this pull request closes",
				guidance: "Add a closing keyword so the issue and PR stay connected.",
				locator: "PR #42",
				severity: "MINOR",
			},
		],
	},
	{
		name: "Keep pull requests small",
		areaName: "Pull requests",
		slug: "small-prs",
		status: "STRENGTH",
		trend: "IMPROVING",
		whyItMatters: "Smaller changes are easier to review and safer to ship.",
		strengths: [
			{
				artifactId: 51,
				artifactType: "PULL_REQUEST",
				observationId: "s2",
				title: "Scoped the change to one concern",
			},
		],
		toWorkOn: [],
	},
];

/** The developer's own practice reflection list on the workspace home — recent feedback for growth, never a score. */
const meta = {
	component: PracticeReflectionSection,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div className="mx-auto max-w-3xl">
				<Story />
			</div>
		),
	],
	args: {
		isLoading: false,
		practices,
	},
} satisfies Meta<typeof PracticeReflectionSection>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvasElement }) => {
		// A reflection card renders per practice, and the copy affordance appears once there's
		// something to copy.
		const canvas = within(canvasElement);
		await expect(canvas.getByRole("heading", { name: practices[0].name })).toBeVisible();
		await expect(canvas.getByRole("heading", { name: practices[1].name })).toBeVisible();
		await expect(canvas.getByRole("button", { name: /copy my practice summary/i })).toBeVisible();
	},
};

export const Loading: Story = {
	args: { isLoading: true, practices: undefined },
};

export const Empty: Story = {
	args: { practices: [] },
};

export const ErrorState: Story = {
	args: { isError: true, onRetry: fn() },
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement);
		await expect(canvas.getByText("Couldn't load your practice feedback")).toBeVisible();

		const retry = canvas.getByRole("button", { name: /retry/i });
		await userEvent.click(retry);
		await expect(args.onRetry).toHaveBeenCalledOnce();
	},
};
