import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import type { ReasonCount } from "@/api/types.gen";
import {
	FEEDBACK_LOCATION_DEFS,
	type FeedbackLocation,
} from "@/components/practice-vocabulary/feedback-location-defs";
import { expectNoPageOverflow } from "@/stories/reflow";

import { FeedbackDeveloperSummary } from "./FeedbackDeveloperSummary";
import { reviewDevelopers } from "./fixtures";

/** Every location, as `countReviewFeedbackByLocation` returns it for one developer. */
const LOCATION_COUNTS: [FeedbackLocation, number][] = [
	["GITHUB", 4],
	["GITLAB", 2],
	["OTHER_WORK", 1],
	["HEPH", 2],
	["SLACK", 1],
	["NEXT_CONVERSATION", 2],
	["PRACTICE_PAGES", 3],
];
const EVERY_LOCATION: ReasonCount[] = LOCATION_COUNTS.map(([reason, count]) => ({
	reason,
	description: FEEDBACK_LOCATION_DEFS[reason].label,
	count,
}));

const meta = {
	component: FeedbackDeveloperSummary,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		developerId: reviewDevelopers.grace.accountId,
		developerName: "Grace Hopper",
		state: { status: "ready", counts: EVERY_LOCATION },
	},
} satisfies Meta<typeof FeedbackDeveloperSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Everything for one developer: one row per location, then the total. Every count opens the list
 * filtered to exactly the feedback it counts.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: "Feedback for Grace Hopper" });
		const table = canvas.getByRole("table");
		for (const name of [
			"On GitHub",
			"On GitLab",
			"On the work",
			"With Heph in the app",
			"With Heph in Slack",
			"In conversation",
			"On their practice pages",
		]) {
			within(table).getByRole("cell", { name });
		}
		const github = within(table).getByRole("row", { name: /On GitHub/u });
		const githubLink = within(github).getByRole("link", { name: "4" });
		await expect(githubLink).toHaveAttribute("href", expect.stringContaining("developerId=9"));
		await expect(githubLink).toHaveAttribute("href", expect.stringContaining("GITHUB"));
		const all = within(table).getByRole("row", { name: /All locations/u });
		await expect(within(all).getByRole("link", { name: "15" })).toBeVisible();
	},
};

/** A location with no feedback for this developer has no row. */
export const OneLocation: Story = {
	args: {
		state: {
			status: "ready",
			counts: [{ reason: "SLACK", description: FEEDBACK_LOCATION_DEFS.SLACK.label, count: 2 }],
		},
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table");
		within(table).getByRole("cell", { name: "With Heph in Slack" });
		await expect(within(table).queryByRole("cell", { name: "On GitHub" })).toBeNull();
	},
};

/** The person's name is not known yet: the summary still reads, without a wrong name. */
export const WithoutAName: Story = {
	args: { developerName: undefined },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: "Feedback for this developer" });
	},
};

export const NoFeedbackYet: Story = {
	args: { state: { status: "ready", counts: [] } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("No feedback for Grace Hopper yet");
		await expect(canvas.queryByRole("table")).not.toBeInTheDocument();
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("table")).not.toBeInTheDocument();
	},
};

export const LoadFailed: Story = {
	args: { state: { status: "error", error: { status: 500 }, onRetry: fn() } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("We could not count the feedback for Grace Hopper");
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

export const Reflow: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: "Feedback for Grace Hopper" });
		await expectNoPageOverflow();
	},
};
