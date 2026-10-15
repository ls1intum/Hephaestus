import type { Meta, StoryContext, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import { Card, CardContent } from "@/components/ui/card";
import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/stories/reflow";
import { minutesBefore } from "@/stories/story-clock";
import { levelsOpenedBy } from "@/test/detail-stack";

import { busyReviewQueue, idleReviewQueue, reviewQueueHead } from "./fixtures";
import { ReviewQueueNow } from "./ReviewQueueNow";

/**
 * The queue as it stands now, whatever the range: the first part of the Queue card, above the
 * review time. Only a long wait and a hold for budget are marked.
 */
/** The card's figures as "label: number", in their order; each reads number first. */
function figuresOf(canvas: StoryContext["canvas"]) {
	const list = canvas.getByLabelText("Queue now");
	return [...list.querySelectorAll("li")].map((figure) => {
		const value = figure.firstElementChild?.textContent ?? "";
		return `${figure.textContent.slice(value.length).trim()}: ${value}`;
	});
}

/** The reviews listed in line, apart from the figures above them. */
function rowsOf(canvas: StoryContext["canvas"]) {
	const table = within(canvas.getByRole("table", { name: "First in the queue" }));
	return table.getAllByRole("row").slice(1);
}

/** One figure's number, by its key. */
function figureValue(canvas: StoryContext["canvas"], key: string) {
	return canvas.getByLabelText("Queue now").querySelector(`[data-figure="${key}"] > span`);
}

const meta = {
	component: ReviewQueueNow,
	decorators: [
		(Story) => (
			<Card size="sm" className="max-w-xl">
				<CardContent className="@container">
					<Story />
				</CardContent>
			</Card>
		),
		withStandardPage,
	],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		state: { status: "ready", queue: busyReviewQueue },
	},
	argTypes: { state: { control: false } },
} satisfies Meta<typeof ReviewQueueNow>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing waits or runs: quiet zeroes, no oldest wait, one calm line. No row and no link. */
export const Idle: Story = {
	args: { state: { status: "ready", queue: idleReviewQueue } },
	play: async ({ canvas }) => {
		const now = canvas.getByRole("region", { name: "The queue" });
		await expect(now).toHaveTextContent("Nothing waits or runs.");
		await expect(figuresOf(canvas)).toEqual(["waiting: 0", "running: 0", "on hold for budget: 0"]);
		await expect(canvas.queryByRole("list", { name: "First in the queue" })).toBeNull();
		await expect(canvas.queryByRole("link", { name: /^See all/u })).toBeNull();
		await expect(now.querySelector(".lucide-triangle-alert")).toBeNull();
	},
};

/**
 * Two running and five waiting, the oldest for four minutes: busy, not stuck, so nothing is
 * marked. The first three in line each open their review, and the rest are one link away.
 */
export const Busy: Story = {
	args: { state: { status: "ready", queue: { ...busyReviewQueue, waiting: 5, heldForBudget: 0 } } },
	play: async ({ canvas }) => {
		const now = canvas.getByRole("region", { name: "The queue" });
		await expect(figuresOf(canvas)).toEqual([
			"waiting: 5",
			"running: 2",
			"on hold for budget: 0",
			"oldest wait: 4 min",
		]);
		// Waiting and running open the Reviews table filtered to them, whatever the range.
		const opens = (name: string) =>
			new URL(canvas.getByRole<HTMLAnchorElement>("link", { name }).href).searchParams;
		// Waiting is a count; running opens its matching reviews.
		await expect(canvas.queryByRole("link", { name: "5 reviews waiting" })).toBeNull();
		await expect(opens("2 reviews running").getAll("status")).toStrictEqual(["RUNNING"]);
		await expect(opens("2 reviews running").get("from")).toBeNull();
		// A figure's number is black whether it is a link or not; its label is muted.
		const running = canvas.getByRole("link", { name: "2 reviews running" });
		await expect(getComputedStyle(running).color).toBe(getComputedStyle(now).color);
		await expect(getComputedStyle(running).fontWeight).toBe("600");
		const runningFigure = canvas
			.getByLabelText("Queue now")
			.querySelector('[data-figure="running"]');
		await expect(getComputedStyle(runningFigure ?? now).color).not.toBe(
			getComputedStyle(running).color,
		);
		await expect(canvas.queryByRole("link", { name: /on hold$/u })).toBeNull();
		const rows = rowsOf(canvas);
		await expect(rows).toHaveLength(3);
		// A small table: work, developer, state and how long, each column lined up down the rows.
		await expect(
			within(canvas.getByRole("table", { name: "First in the queue" }))
				.getAllByRole("columnheader")
				.map((head) => head.textContent),
		).toEqual(["Work", "Developer", "State", "Since / Waited"]);
		await expect(rows[0]).toHaveTextContent("#1423Ada LovelaceRunning3 min");
		await expect(rows[2]).toHaveTextContent("#1431Alan TuringWaiting4 min");
		const lefts = (column: number) =>
			new Set(
				rows.map((row) => Math.round(row.children[column]?.getBoundingClientRect().left ?? 0)),
			);
		await expect(lefts(2).size).toBe(1);
		// A queued review opens its sheet over the page.
		await expect(levelsOpenedBy(canvas.getByRole("link", { name: "#1423" }))).toEqual([
			"review:0c0c0c0c-0000-4000-8000-000000000001",
		]);
		const all = canvas.getByRole("link", { name: "See all 7" });
		const search = new URL(all.getAttribute("href") ?? "", "http://x").searchParams;
		await expect(search.getAll("status")).toStrictEqual(["RUNNING", "QUEUED"]);
		await expect(now.querySelector(".lucide-triangle-alert")).toBeNull();
	},
};

/**
 * The oldest has waited three quarters of an hour, past the overview's mark at half an hour: the
 * wait is the one thing marked, in words and an icon, without colour, since slow is not broken.
 */
export const LongWait: Story = {
	args: {
		state: {
			status: "ready",
			queue: {
				...busyReviewQueue,
				running: 1,
				activeWorkers: 1,
				waiting: 2,
				heldForBudget: 0,
				oldestWaitingSince: minutesBefore(45),
				head: [
					reviewQueueHead.runningLong,
					{ ...reviewQueueHead.waitingLong, since: minutesBefore(45) },
					reviewQueueHead.waitingShort,
				],
			},
		},
	},
	play: async ({ canvas }) => {
		const wait = figureValue(canvas, "oldest");
		await expect(wait).toHaveTextContent("Needs a look: 45 min");
		await expect(wait?.querySelector(".lucide-triangle-alert")).not.toBeNull();
		await expect(wait?.querySelector(".text-warning, .text-destructive")).toBeNull();
		await expect(canvas.queryByRole("link", { name: /^See all/u })).toBeNull();
	},
};

/**
 * A quarter of an hour is an ordinary wait on a course day, about the median: below the half hour,
 * nothing is marked.
 */
export const WaitBelowMark: Story = {
	args: {
		state: {
			status: "ready",
			queue: {
				...busyReviewQueue,
				waiting: 2,
				heldForBudget: 0,
				oldestWaitingSince: minutesBefore(15),
			},
		},
	},
	play: async ({ canvas }) => {
		const now = canvas.getByRole("region", { name: "The queue" });
		await expect(figuresOf(canvas)).toContain("oldest wait: 15 min");
		await expect(now.querySelector(".lucide-triangle-alert")).toBeNull();
	},
};

/** The budget is used up: the hold is marked, and its row waits last, after the work that can run. */
export const OnHold: Story = {
	args: {
		state: {
			status: "ready",
			queue: {
				...idleReviewQueue,
				heldForBudget: 1,
				finishedLastHour: 0,
				finishedLastDay: 3,
				finishedPerHourLastDay: 0.125,
				head: [
					{
						...reviewQueueHead.waitingLong,
						heldForBudget: true,
						since: minutesBefore(42),
					},
				],
			},
		},
	},
	play: async ({ canvas }) => {
		await expect(figureValue(canvas, "held")).toHaveTextContent("Needs a look: 1");
		// Nothing runs until an admin acts: broken, so red.
		await expect(figureValue(canvas, "held")?.querySelector(".text-destructive")).not.toBeNull();
		await expect(canvas.queryByRole("link", { name: "1 review on hold" })).toBeNull();
		await expect(rowsOf(canvas)).toHaveLength(1);
		await expect(rowsOf(canvas)[0]).toHaveTextContent("#1431Alan TuringOn hold42 min");
	},
};

/** Many in line: still three rows, and the count opens them all. */
export const ManyItems: Story = {
	args: {
		state: {
			status: "ready",
			queue: {
				...busyReviewQueue,
				running: 4,
				activeWorkers: 4,
				waiting: 37,
				oldestWaitingSince: minutesBefore(9),
				heldForBudget: 2,
				finishedLastHour: 48,
				finishedLastDay: 610,
				finishedPerHourLastDay: 610 / 24,
			},
		},
	},
	play: async ({ canvas, userEvent }) => {
		await expect(rowsOf(canvas)).toHaveLength(3);
		canvas.getByRole("link", { name: "See all 43" });
		// How fast the queue empties now is in the card's (i), not under the figures.
		await userEvent.click(canvas.getByRole("button", { name: "About the queue" }));
		await expect(await screen.findByRole("dialog", { name: "About the queue" })).toHaveTextContent(
			"Right now, 25 finish an hour, over the last 24 hours.",
		);
	},
};

export const Loading: Story = {
	args: { state: { status: "loading" } },
	play: async ({ canvas }) => {
		canvas.getByText("Loading the queue…");
	},
};

export const LoadError: Story = {
	args: { state: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async ({ canvas }) => {
		canvas.getByText("We could not load the queue");
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

/** At 320 px the rows truncate the developer before the time, and nothing scrolls sideways. */
export const Narrow: Story = {
	args: { state: { status: "ready", queue: busyReviewQueue } },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvas }) => {
		await expect(rowsOf(canvas)[0]).toHaveTextContent(/Running3 min$/u);
		await expectNoPageOverflow();
	},
};

/** The status icons and the hold's warning keep their contrast on the dark surface. */
export const Dark: Story = {
	args: {
		state: {
			status: "ready",
			queue: { ...busyReviewQueue, heldForBudget: 1, oldestWaitingSince: minutesBefore(14) },
		},
	},
	globals: { theme: "dark" },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: "See all 7" })).toBeVisible();
	},
};
