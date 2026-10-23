import type { Meta, StoryContext, StoryObj } from "@storybook/react-vite";
import { expect, userEvent } from "storybook/test";

import type { FeedbackCheck } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/stories/reflow";

import { FeedbackChecks } from "./FeedbackChecks";
import { deniedFeedbackCheck } from "./fixtures";

const allowedCheck: FeedbackCheck = {
	...deniedFeedbackCheck,
	allowed: true,
	reason: null,
	facts: {
		...deniedFeedbackCheck.facts,
		evaluatedRolloutRevision: deniedFeedbackCheck.facts.admittedRolloutRevision,
		currentVersion: true,
	},
};

const outOfScopeCheck: FeedbackCheck = {
	...deniedFeedbackCheck,
	stage: "AUTOMATIC",
	reason: "OUTSIDE_CURRENT_SCOPE",
	facts: { ...deniedFeedbackCheck.facts, inScope: false, currentVersion: true },
};

async function openCheck(canvas: StoryContext["canvas"], name: RegExp) {
	await userEvent.click(canvas.getByRole("button", { name }));
}

const meta = {
	component: FeedbackChecks,
	tags: ["autodocs"],
	args: { checks: [deniedFeedbackCheck] },
} satisfies Meta<typeof FeedbackChecks>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Stopped: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Why this was not sent" })).toBeVisible();
		await openCheck(canvas, /Final send/u);
		await expect(canvas.getByText("Stopped")).toBeVisible();
		await expect(
			await canvas.findByText("Admitted under workspace settings 4, checked against 5"),
		).toBeVisible();
		await expect(canvas.getByText("Reviewed version is current").nextSibling).toHaveTextContent(
			"no",
		);
	},
};

export const Allowed: Story = {
	args: { checks: [allowedCheck] },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Feedback checks" })).toBeVisible();
		await openCheck(canvas, /Final send/u);
		await expect(canvas.getByText("Allowed")).toBeVisible();
		await expect(await canvas.findByText("Workspace settings 4")).toBeVisible();
	},
};

/** Under a section already called "Checks", the box names itself only when a check stopped. */
export const InChecksSection: Story = {
	args: { checks: [allowedCheck], inChecksSection: true },
	play: async ({ canvas }) => {
		await expect(
			canvas.queryByRole("heading", { name: "Feedback checks" }),
		).not.toBeInTheDocument();
		await expect(canvas.getByRole("heading", { level: 4, name: /Final send/u })).toBeVisible();
		await openCheck(canvas, /Final send/u);
		await expect(canvas.getByText("Allowed")).toBeVisible();
	},
};

export const StoppedInChecksSection: Story = {
	args: { checks: [deniedFeedbackCheck], inChecksSection: true },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Why this was not sent" })).toBeVisible();
		await expect(canvas.getByRole("heading", { level: 5, name: /Final send/u })).toBeVisible();
	},
};

export const PassedThenStopped: Story = {
	args: { checks: [{ ...allowedCheck, stage: "COMPOSITION" }, outOfScopeCheck] },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { name: "Why this was not sent" })).toBeVisible();
		await openCheck(canvas, /Automatic approval/u);
		await expect(await canvas.findByText("In review scope")).toBeVisible();
	},
};

export const NoChecks: Story = {
	args: { checks: [] },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("heading")).not.toBeInTheDocument();
	},
};

export const Reflow: Story = {
	args: {
		checks: [
			{
				...outOfScopeCheck,
				facts: {
					...outOfScopeCheck.facts,
					admittedRolloutRevision:
						"9007199254740991900719925474099190071992547409919007199254740991",
				},
			},
		],
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
	},
	play: async ({ canvas }) => {
		await openCheck(canvas, /Automatic approval/u);
		await expect(await canvas.findByText("In review scope")).toBeVisible();
		await expectNoPageOverflow();
	},
};
