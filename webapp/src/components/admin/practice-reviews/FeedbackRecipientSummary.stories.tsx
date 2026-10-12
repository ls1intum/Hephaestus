import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import type { ReviewFeedbackLocationCounts } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/stories/reflow";

import { FeedbackRecipientSummary } from "./FeedbackRecipientSummary";
import { feedbackCounts } from "./fixtures";

/** Every location, each with the delivery states that location can reach. */
const EVERY_LOCATION: ReviewFeedbackLocationCounts[] = [
	{
		location: "GITHUB",
		feedback: feedbackCounts(["DELIVERED", "DELIVERED", "FAILED", "SUPPRESSED"]),
	},
	{ location: "GITLAB", feedback: feedbackCounts(["DELIVERED", "AWAITING_APPROVAL"]) },
	{ location: "OTHER_WORK", feedback: feedbackCounts(["PARTIALLY_FAILED"]) },
	{ location: "HEPH", feedback: feedbackCounts(["DELIVERED", "UNCONFIRMED"]) },
	{ location: "SLACK", feedback: feedbackCounts(["DELIVERED"]) },
	{ location: "NEXT_CONVERSATION", feedback: feedbackCounts(["PREPARED", "SUPPRESSED"]) },
	{ location: "PRACTICE_PAGES", feedback: feedbackCounts(["DELIVERED", "PREPARED", "SUPERSEDED"]) },
];

const meta = {
	component: FeedbackRecipientSummary,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 1440] },
	},
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		recipientUserId: 9,
		recipientName: "Grace Hopper",
		state: { status: "ready", counts: EVERY_LOCATION },
	},
} satisfies Meta<typeof FeedbackRecipientSummary>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Everything addressed to one developer: the delivery mix over all of it, then one row per location
 * with what became of the feedback there. Every count opens the list filtered to exactly it.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: "Feedback for Grace Hopper" });
		await expect(canvas.getByText("15")).toBeVisible();
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
		// GitHub's row total opens the list filtered to Grace on GitHub, and its failed count to the
		// failed states there and nothing else.
		const github = within(table).getByRole("row", { name: /On GitHub/u });
		const cells = within(github).getAllByRole("cell");
		const [, total, delivered] = cells;
		const failed = cells.at(-1);
		const totalLink = within(total ?? github).getByRole("link", { name: "4" });
		await expect(totalLink).toHaveAttribute("href", expect.stringContaining("recipientUserId=9"));
		await expect(totalLink).toHaveAttribute("href", expect.stringContaining("GITHUB"));
		await expect(within(delivered ?? github).getByRole("link", { name: "2" })).toBeVisible();
		await expect(within(failed ?? github).getByRole("link", { name: "1" })).toHaveAttribute(
			"href",
			expect.stringContaining("PARTIALLY_FAILED"),
		);
	},
};

/** The person's name is not known yet: the summary still reads, without a wrong name. */
export const WithoutAName: Story = {
	args: { recipientName: undefined },
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
