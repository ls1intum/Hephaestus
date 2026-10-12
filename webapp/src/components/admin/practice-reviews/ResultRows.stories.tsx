import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import type { ReviewResult } from "@/api/types.gen";
import { expectNoPageOverflow } from "@/stories/reflow";
import { daysBefore, hoursBefore } from "@/stories/story-clock";
import { levelsOpenedBy } from "@/test/detail-stack";

import { feedbackCounts, reviewResults } from "./fixtures";
import { ResultRows } from "./ResultRows";

const [firstResult] = reviewResults;
if (!firstResult) {
	throw new Error("The review fixtures must contain a result");
}

const longContent = {
	...firstResult,
	id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
	summary:
		"The review keeps every boundary visible even when the result needs enough words to wrap across a narrow screen twice over",
	subject: {
		id: 10,
		login: "alexandria-occasional-contributor",
		name: "Alexandria Catherine Montgomery-Worthington",
	},
} satisfies ReviewResult;

/** The fixture's results spread over a week, about three a day. */
const overAWeek: ReviewResult[] = reviewResults.map((result, index) => ({
	...result,
	observedAt: index < 3 ? hoursBefore(index + 1) : daysBefore(Math.floor(index / 3)),
}));

/** Storybook resets a spy that appears in `args` between runs, so one instance is enough. */
const clearFilters = fn();
const retry = fn();

const TABLE = { name: "Results" } as const;

/**
 * The Results stream, drawn as the Reviews table: one body per day under its heading, the time and
 * the outcome as an icon, named with the problems a not met result counts, the result with its
 * practice, the work, the developer and what came of it. The whole row opens the result's sheet;
 * Track and Trace end it.
 */
const meta = {
	component: ResultRows,
	parameters: {
		layout: "padded",
		chromatic: { viewports: [320, 768, 1440] },
	},
	tags: ["autodocs"],
	args: {
		state: { status: "ready", results: reviewResults },
	},
} satisfies Meta<typeof ResultRows>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The mixed result list from the review fixtures. */
export const Default: Story = {
	play: async ({ canvas }) => {
		const table = canvas.getByRole("table", TABLE);
		await expect(
			within(table)
				.getAllByRole("columnheader")
				.map((head) => head.textContent),
		).toEqual(["Recorded", "Result", "Work", "Developer", "Actions", "Open"]);
		// The outcome is each row's leading icon, named for a screen reader with the problems a not
		// met result counts, and no button.
		await expect(canvas.getAllByText("Not met · 1 problem")).toHaveLength(3);
		await expect(canvas.getAllByText("Not met · 2 problems")).toHaveLength(3);
		await expect(canvas.getAllByText("Not met · 3 problems")).toHaveLength(1);
		await expect(canvas.queryByRole("button", { name: /^Not met/u })).not.toBeInTheDocument();
		canvas.getByText("Does not apply");
		// Severity is named only where it costs something: critical or major.
		canvas.getByText("Critical");
		await expect(canvas.queryByText("Minor")).not.toBeInTheDocument();
		await expect(canvas.queryByText("Trivial")).not.toBeInTheDocument();
		await expect(canvas.getAllByText("Backfilled")).toHaveLength(2);
		// A row opens its result over the list, so the list stays where the reader left it.
		const open = canvas
			.getAllByRole("link", { name: /^Open result /u })
			.find((link) => link.getAttribute("aria-label")?.includes(firstResult.summary) === true);
		await expect(open === undefined ? [] : levelsOpenedBy(open)).toEqual([
			`result:${firstResult.id}`,
		]);
	},
};

/** A week of results: one heading per day, newest first, and each row carries only its time. */
export const ManyDays: Story = {
	args: { state: { status: "ready", results: overAWeek } },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const days = within(canvas.getByRole("table", TABLE)).getAllByRole("rowheader");
		await expect(days.length).toBeGreaterThan(2);
	},
};

/** Captured evidence can remain ambiguous without implying a met or not met result. */
export const CouldNotDetermine: Story = {
	args: {
		state: {
			status: "ready",
			results: [
				{
					...firstResult,
					outcome: "UNDETERMINED",
					severity: undefined,
					problemCount: 0,
					summary: "The captured discussion leaves the chosen approach ambiguous",
				},
			],
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Could not determine");
		// The row's own controls are its Track and Trace, nothing else.
		await expect(canvas.getAllByRole("button").map((button) => button.title)).toEqual([
			"",
			"Track",
		]);
		await expect(canvas.queryByText("Critical")).not.toBeInTheDocument();
	},
};

/**
 * A row shows the result, not what became of it: feedback counts are the trace's to tell, so
 * none appear here even when the result led to feedback.
 */
export const NoFeedbackCounts: Story = {
	args: {
		state: {
			status: "ready",
			results: [{ ...firstResult, feedback: feedbackCounts(["UNCONFIRMED"]) }],
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("1 unconfirmed")).toBeNull();
		await expect(canvas.queryByText("No feedback")).toBeNull();
	},
};

/**
 * A row opens one thing, its result's sheet. The practice and the work are plain text; Track
 * and Trace end the row.
 */
export const OneLinkPerRow: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas }) => {
		const rows = within(canvas.getByRole("table", TABLE))
			.getAllByRole("row")
			.filter((row) => within(row).queryAllByRole("cell").length > 0);
		await expect(rows).toHaveLength(reviewResults.length);
		for (const row of rows) {
			await expect(
				within(row)
					.getAllByRole("link")
					.map((link) => link.title),
			).toEqual(["Trace", ""]);
			await expect(
				within(row)
					.getAllByRole("button")
					.map((button) => button.title),
			).toEqual(["Track"]);
		}
		await expect(
			canvas.queryByRole("link", { name: /^Thin controllers/u }),
		).not.toBeInTheDocument();
		await expect(canvas.getAllByText("Thin controllers").length).toBeGreaterThan(0);
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

export const LongContent: Story = {
	args: { state: { status: "ready", results: [longContent] } },
	parameters: {
		chromatic: { viewports: [320] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async () => {
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
			within(canvas.getByRole("table", TABLE)).getByText("No results yet"),
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
			error: { status: 500, detail: "The result index is unavailable." },
			onRetry: retry,
		},
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("We could not load results");
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(retry).toHaveBeenCalled();
	},
};

/** The verdict icons, the day headings and the exception badges keep their contrast on dark. */
export const Dark: Story = {
	globals: { theme: "dark" },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		canvas.getByRole("table", TABLE);
	},
};

/**
 * Tracking one result: its row is tinted and its Track pressed, read from the address, as on
 * the list every table narrows to it.
 */
export const Tracked: Story = {
	parameters: {
		router: {
			initialUrl: `/?practiceResultId=${firstResult.id}&agentJobId=${firstResult.agentJobId}&selected=true`,
		},
	},
	play: async ({ canvas }) => {
		const [tracked] = canvas.getAllByRole("button", { name: /^Track result /u });
		await expect(tracked).toHaveAttribute("aria-pressed", "true");
		await expect(tracked?.closest("tr")).toHaveAttribute("data-variant", "highlighted");
	},
};
