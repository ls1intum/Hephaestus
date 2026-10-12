import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import { expectNoPageOverflow } from "@/stories/reflow";
import { daysBefore, hoursBefore } from "@/stories/story-clock";
import { levelsOpenedBy } from "@/test/detail-stack";

import { FeedbackResults } from "./FeedbackResults";
import { PRIVATE_FEEDBACK_TEXT, reviewFeedback } from "./fixtures";

const [firstFeedback] = reviewFeedback;
if (!firstFeedback) {
	throw new Error("The review fixtures must contain a piece of feedback");
}

const postedFeedback = reviewFeedback.find(
	(item) => item.channel === "IN_CONTEXT" && item.deliveryState === "DELIVERED",
);
if (!postedFeedback) {
	throw new Error("The review fixtures must contain delivered feedback on the work");
}

/** Storybook resets a spy that appears in `args` between runs, so one instance is enough. */
const clearFilters = fn();
const retry = fn();

const TABLE = { name: "Feedback" } as const;

/** The fixture's feedback spread over a week, about three a day. */
const overAWeek = reviewFeedback.map((item, index) => ({
	...item,
	createdAt: index < 3 ? hoursBefore(index + 1) : daysBefore(Math.floor(index / 3)),
}));

/**
 * The Feedback stream, drawn as the Reviews table: one body per day under its heading, the time and
 * the delivery as an icon, the first line of the text, where it went, the work, who it was for, and
 * what became of it. The whole row opens the feedback's sheet; Track and Trace end it.
 */
const meta = {
	component: FeedbackResults,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: { state: { status: "ready", feedback: reviewFeedback } },
} satisfies Meta<typeof FeedbackResults>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Every outcome, on both places feedback can go, with a withheld row from each reason family. No
 * column previews the text: a row is told apart by its time, channel, work and recipient.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(
			within(canvas.getByRole("table", TABLE))
				.getAllByRole("columnheader")
				.map((head) => head.textContent),
		).toEqual(["Composed", "Channel", "Work", "Recipient", "Outcome", "Actions", "Open"]);
		// The outcome is its icon and label in its own column. A conversation row's outcome is refined
		// by its place, and still begins with the stem of the stored state so the Outcome facet remains
		// findable from the row.
		await expect(canvas.getAllByText("Prepared for conversation").length).toBeGreaterThan(0);
		await expect(canvas.getAllByText("Delivered in conversation").length).toBeGreaterThan(0);
		await expect(canvas.getAllByText("Failed to deliver").length).toBeGreaterThan(0);
		await expect(canvas.getAllByText("Replaced by newer").length).toBeGreaterThan(0);
		// A row opens one thing, its sheet; Track and Trace end it. The review and the recipient are text.
		// The head's (i) is the one button outside the rows.
		await expect(canvas.getAllByRole("button")).toHaveLength(reviewFeedback.length + 1);
		await expect(canvas.queryByRole("link", { name: "a review" })).not.toBeInTheDocument();
		await expect(canvas.getAllByRole("link")).toHaveLength(reviewFeedback.length * 2);
		// A withheld row says why in the words the "Why withheld" filter offers, and its precise reason
		// on hover.
		await expect(canvas.getAllByText("The work moved on").length).toBeGreaterThan(0);
		await expect(canvas.getAllByText("The developer’s choice").length).toBeGreaterThan(0);
		await expect(canvas.getAllByText("Housekeeping").length).toBeGreaterThan(0);
		canvas.getByTitle("The work was already merged, so a note on it would arrive too late.");
		canvas.getByTitle("The developer has opted out of AI feedback.");
		canvas.getByTitle("Nearly the same as other feedback from the same review.");
		// No text is previewed, private or not.
		await expect(canvas.queryByText(/Before this thread scrolls away/u)).toBeNull();
		await expect(canvas.queryByText(PRIVATE_FEEDBACK_TEXT)).toBeNull();
		// Each row is named by where it went, for whom and on which work, so no two read alike.
		const names = canvas
			.getAllByRole("link", { name: /^Open feedback /u })
			.map((link) => link.getAttribute("aria-label"));
		await expect(new Set(names).size).toBe(names.length);
		await expect(names[0]).toMatch(/^Open feedback .+ for .+/u);
		// A row opens its feedback over the list rather than navigating away from it.
		await expect(canvas.getAllByRole("link").map(levelsOpenedBy)).toContainEqual([
			`feedback:${firstFeedback.id}`,
		]);
	},
};

/** A long note previews nothing in the row: its text is on its sheet. */
export const LongFeedback: Story = {
	args: {
		state: {
			status: "ready",
			feedback: reviewFeedback.filter((item) => item.bodyTruncated),
		},
	},
	parameters: { chromatic: { viewports: [320, 1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("link", { name: /^Open feedback /u });
		await expect(canvas.queryByText(/2 issues to tighten/u)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/```/u)).not.toBeInTheDocument();
		await expectNoPageOverflow();
	},
};

/** A week of feedback: one heading per day, newest first, and each row carries only its time. */
export const ManyDays: Story = {
	args: { state: { status: "ready", feedback: overAWeek } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const days = within(canvas.getByRole("table", TABLE)).getAllByRole("rowheader");
		await expect(days.length).toBeGreaterThan(2);
	},
};

/** On a phone the table scrolls sideways in its frame; the page does not. */
export const Narrow: Story = {
	parameters: {
		chromatic: { viewports: [320] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		canvas.getByRole("table", TABLE);
		await expectNoPageOverflow();
	},
};

/** Feedback posted with Windows line endings: the row stays named, and the hidden marker never shows. */
export const PostedWithCrLf: Story = {
	args: {
		state: {
			status: "ready",
			feedback: [
				{
					...postedFeedback,
					bodyPreview:
						"<!-- hephaestus:practice-review:00000000-0000-0000-0000-000000000001 -->\r\nKeep the lookup focused.\r\n",
					bodyTruncated: false,
				},
			],
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: /^Open feedback /u })).toBeVisible();
		await expect(canvas.queryByText(/hephaestus:practice-review/u)).toBeNull();
	},
};

/** Feedback with no text still names its row by channel, recipient and work. */
export const WithoutAPreview: Story = {
	args: {
		state: {
			status: "ready",
			feedback: [{ ...firstFeedback, bodyPreview: undefined, bodyTruncated: false }],
		},
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const link = canvas.getByRole("link", { name: /^Open feedback /u });
		await expect(link.getAttribute("aria-label")).toContain(" for ");
	},
};

const githubWork = reviewFeedback.find((item) => item.location === "GITHUB")?.reviewedWork;
const gitlabWork = reviewFeedback.find((item) => item.location === "GITLAB")?.reviewedWork;

/**
 * One row per location. Each names where its feedback went in words and with the provider's mark: a
 * comment on the GitHub pull request or GitLab merge request, a conversation with Heph in the app or
 * in Slack, the next conversation, or a card on the developer's practice pages.
 */
export const EveryLocation: Story = {
	args: {
		state: {
			status: "ready",
			feedback: [
				{
					...firstFeedback,
					id: "location-github",
					channel: "IN_CONTEXT",
					location: "GITHUB",
					reviewedWork: githubWork,
				},
				{
					...firstFeedback,
					id: "location-gitlab",
					channel: "IN_CONTEXT",
					location: "GITLAB",
					reviewedWork: gitlabWork,
				},
				{
					...firstFeedback,
					id: "location-other-work",
					channel: "IN_CONTEXT",
					location: "OTHER_WORK",
					reviewedWork: undefined,
				},
				{
					...firstFeedback,
					id: "location-heph",
					channel: "IN_CHAT",
					location: "HEPH",
					deliveryState: "DELIVERED",
				},
				{
					...firstFeedback,
					id: "location-slack",
					channel: "IN_CHAT",
					location: "SLACK",
					deliveryState: "DELIVERED",
				},
				{
					...firstFeedback,
					id: "location-next-conversation",
					channel: "IN_CHAT",
					location: "NEXT_CONVERSATION",
					deliveryState: "PREPARED",
				},
				{
					...firstFeedback,
					id: "location-practice-pages",
					channel: "IN_APP",
					location: "PRACTICE_PAGES",
					deliveryState: "DELIVERED",
				},
			],
		},
	},
	parameters: { chromatic: { viewports: [320, 1440] } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("On GitHub")).toBeVisible();
		await expect(canvas.getByText("On GitLab")).toBeVisible();
		canvas.getByText("On the work");
		canvas.getByText("With Heph in the app");
		canvas.getByText("With Heph in Slack");
		canvas.getByText("In conversation");
		canvas.getByText("On their practice pages");
		await expectNoPageOverflow();
	},
};

/** The head stands and the rows wait in their shape, so nothing jumps when they arrive. */
export const Loading: Story = {
	args: { state: { status: "loading" } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", TABLE)).toHaveAttribute("aria-busy", "true");
	},
};

export const Empty: Story = {
	args: { state: { status: "empty", filtered: false } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expect(
			within(canvas.getByRole("table", TABLE)).getByText("No feedback yet"),
		).toBeVisible();
	},
};

export const FilteredToNothing: Story = {
	args: { state: { status: "empty", filtered: true, onClearFilters: clearFilters } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await expect(clearFilters).toHaveBeenCalled();
	},
};

export const LoadFailed: Story = {
	args: {
		state: {
			status: "error",
			error: { status: 500, detail: "The feedback index is unavailable." },
			onRetry: retry,
		},
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("We could not load feedback");
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(retry).toHaveBeenCalled();
	},
};

/** The delivery icons, the location marks and the day headings keep their contrast on dark. */
export const Dark: Story = {
	globals: { theme: "dark" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		canvas.getByRole("table", TABLE);
	},
};

/** Tracking one piece of feedback: its row is tinted and its Track pressed, read from the address. */
export const Tracked: Story = {
	parameters: {
		router: {
			initialUrl: `/?feedbackId=${firstFeedback.id}&agentJobId=${firstFeedback.agentJobId}&selected=true`,
		},
	},
	play: async ({ canvas }) => {
		const [tracked] = canvas.getAllByRole("button", { name: /^Track feedback /u });
		await expect(tracked).toHaveAttribute("aria-pressed", "true");
		await expect(tracked?.closest("tr")).toHaveAttribute("data-variant", "highlighted");
	},
};
