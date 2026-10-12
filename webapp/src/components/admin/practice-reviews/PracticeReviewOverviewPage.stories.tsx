import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import type { ReviewRange } from "@/components/admin/practice-reviews/review-range";
import { ReviewRunningBanner } from "@/components/admin/practices/review/ReviewRunningBanner";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/stories/reflow";
import { Stateful } from "@/stories/stateful";
import { minutesBefore } from "@/stories/story-clock";
import { precedes } from "@/test/dom";

import {
	busyReviewQueue,
	deliveredByChannelCounts,
	feedbackCounts,
	awaitingApprovalFeedback,
	practiceReviewOverview,
	practiceReviewTiming,
	readyReviewOverview,
	reviewOverviewScope,
	withheldByReasonCounts,
	workspacePractices,
} from "./fixtures";
import { PracticeReviewOverviewPage } from "./PracticeReviewOverviewPage";
import type { ReviewCountState, ReviewSectionState } from "./review-states";

function ready<T>(items: T[], total = items.length): ReviewSectionState<T> {
	return { status: "ready", items, total };
}

function counted(total: number): ReviewCountState {
	return { status: "ready", total, stale: false };
}

const RANGE = { from: reviewOverviewScope.from, to: reviewOverviewScope.to };

/**
 * The home of Practice reviews, in the order an admin's questions come: can reviews run, what do I
 * owe them, what did they do, and which practices did it. The range is the page's; the decisions
 * owed have none, because a decision does not expire with the range. Each region's own empty,
 * loading and stale states are in its own stories.
 */
const meta = {
	component: PracticeReviewOverviewPage,
	parameters: { layout: "fullscreen" },
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		range: "30d",
		onRangeChange: fn(),
		scope: reviewOverviewScope,
		overview: readyReviewOverview(),
		previous: {
			status: "ready",
			period: { overview: practiceReviewOverview },
		},
		attention: {
			approvals: {
				state: ready(awaitingApprovalFeedback),
			},
		},
		delivery: {
			delivered: { status: "ready", counts: deliveredByChannelCounts, stale: false },
			withheld: { status: "ready", counts: withheldByReasonCounts, stale: false },
			unprocessedResults: {
				state: counted(1),
				list: { list: "runs", search: { ...RANGE, resultProcessing: ["FAILED"] } },
				of: 31,
			},
		},
		timing: { status: "ready", timing: practiceReviewTiming, stale: false },
		queue: { status: "ready", queue: busyReviewQueue },
		practices: workspacePractices,
	},
	argTypes: {
		overview: { control: false },
		timing: { control: false },
		queue: { control: false },
		previous: { control: false },
		attention: { control: false },
		delivery: { control: false },
		banner: { control: false },
	},
	// Controlled: the range comes back through the same prop the choice is reported on.
	render: (args) => (
		<Stateful<ReviewRange> initial={args.range}>
			{(range, setRange) => (
				<PracticeReviewOverviewPage
					{...args}
					range={range}
					onRangeChange={(next) => {
						setRange(next);
						args.onRangeChange(next);
					}}
				/>
			)}
		</Stateful>
	),
} satisfies Meta<typeof PracticeReviewOverviewPage>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await expect(
			canvas.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent),
		).toEqual(["Needs you", "Queue", "What the reviews did", "Delivery", "Practices"]);
		// The range heads the page, above what it governs and what it does not.
		const rangeControl = canvas.getByRole("button", { name: "30 days" });
		await expect(precedes(rangeControl, canvas.getByRole("heading", { name: "Needs you" }))).toBe(
			true,
		);
		canvas.getByRole("link", { name: /^Review \d+ (?:piece|pieces) of feedback$/u });
		// What failed is counted where it happened, over the range, not in a line under Needs you.
		await expect(canvas.queryByRole("button", { name: /^Why last/u })).not.toBeInTheDocument();
		const failed = new URL(
			canvas.getByRole<HTMLAnchorElement>("link", {
				name: "9 % failed or timed out, 3 of 34 finished reviews",
			}).href,
		);
		await expect(failed.searchParams.getAll("status")).toStrictEqual(["FAILED", "TIMED_OUT"]);
		await expect(failed.searchParams.get("from")).toBe(RANGE.from);
		const unprocessed = new URL(
			canvas.getByRole<HTMLAnchorElement>("link", { name: "1 of 31 completed" }).href,
		);
		await expect(unprocessed.searchParams.getAll("resultProcessing")).toStrictEqual(["FAILED"]);
		canvas.getByRole("list", { name: "Stages" });
		// A header row, five practices and the totals; the sixth, not checked, is no row and no line.
		const practices = within(canvas.getByRole("region", { name: "Practices" }));
		await expect(practices.getAllByRole("row")).toHaveLength(7);
		// Queue is one card: the queue now, then the review time of the range under its name; the
		// reviews stand beside the results, the feedback beside where it went.
		const queue = within(canvas.getByRole("region", { name: "Queue" }));
		await expect(
			canvas.getByRole("region", { name: "Queue" }).querySelectorAll("[data-slot=card]"),
		).toHaveLength(1);
		const queueNow = within(queue.getByRole("region", { name: "The queue" }));
		const reviewTime = within(queue.getByRole("region", { name: "Review time" }));
		await expect(canvas.getAllByRole("region", { name: "The queue" })).toHaveLength(1);
		// What waits and runs is now; what finished follows the range, as everything else in the area.
		queueNow.getByText("Now");
		reviewTime.getByText("Last 30 days");
		reviewTime.getByRole("link", { name: "34 reviews finished" });
		const stages = canvas.getByRole("list", { name: "Stages" }).querySelectorAll(":scope > li");
		await expect([...stages].map((card) => card.textContent)).toEqual([
			expect.stringMatching(/^Reviews/u),
			expect.stringMatching(/^Results/u),
		]);
		const delivery = canvas.getByRole("list", { name: "Delivery" }).querySelectorAll(":scope > li");
		await expect(delivery).toHaveLength(2);
		await expect(delivery[0]).toHaveTextContent(/^Feedback/u);
		// Beside it, where the feedback went: by channel, by reason, and the share that failed to
		// deliver over the range, 1 of the 15 pieces sent in the last 30 days.
		await expect(delivery[1]).toHaveTextContent(/^By channel and reason/u);
		await expect(delivery[1]?.querySelector("[role=group][aria-label=Failures]")).toHaveTextContent(
			/^Feedback failed to deliver · 1 of 15 sent7 %/u,
		);
		// Awaiting approval is counted under Delivery; the way to approve stays under Needs you.
		await expect(canvas.getByRole("list", { name: "Feedback by delivery" })).toHaveTextContent(
			"3 awaiting approval",
		);
		// Every count of the Feedback tile opens the Feedback table filtered to it, over the range.
		const feedbackLegend = within(canvas.getByRole("list", { name: "Feedback by delivery" }));
		const opens = (name: string) => {
			const url = new URL(feedbackLegend.getByRole<HTMLAnchorElement>("link", { name }).href);
			return [
				url.pathname,
				url.searchParams.getAll("deliveryState").join(","),
				url.searchParams.get("from"),
				url.searchParams.get("to"),
			];
		};
		await expect(opens("3 awaiting approval")).toStrictEqual([
			"/w/demo/admin/practices/reviews/feedback",
			"AWAITING_APPROVAL",
			RANGE.from,
			RANGE.to,
		]);
		await expect(opens("1 prepared")[1]).toBe("PREPARED");
		await expect(opens("14 delivered")[1]).toBe("DELIVERED,PARTIALLY_DELIVERED");
		await expect(opens("4 withheld")[1]).toBe("SUPPRESSED,DISCARDED");
		await expect(opens("1 failed to deliver")[1]).toBe("FAILED,PARTIALLY_FAILED");
		await expect(opens("1 replaced by newer")[1]).toBe("SUPERSEDED");
		// The Results tile says each outcome in one short form, with marked incorrect as the
		// quiet last item of the same row; each count opens the Results table filtered to it.
		const resultsLegend = canvas.getByRole("list", { name: "Results by outcome" });
		await expect(resultsLegend).toHaveTextContent(
			"35 met27 not met10 does not apply6 could not determine",
		);
		const checked = canvas.getByRole("list", { name: "Results checked by an admin" });
		await expect(checked.parentElement).toBe(resultsLegend.parentElement);
		await expect(checked.querySelector("svg")).toHaveClass("text-muted-foreground");
		const resultLink = (name: string) =>
			new URL(canvas.getByRole<HTMLAnchorElement>("link", { name }).href);
		await expect(resultLink("27 not met").searchParams.getAll("outcome")).toStrictEqual([
			"NOT_MET",
		]);
		await expect(resultLink("3 marked incorrect").searchParams.get("invalidated")).toBe("true");
		await expect(resultLink("3 marked incorrect").pathname).toBe(
			"/w/demo/admin/practices/reviews/results",
		);
		// The Reviews tile alike: each outcome, and what is not finished yet, open the Reviews table.
		await expect(resultLink("2 failed").searchParams.getAll("status")).toStrictEqual(["FAILED"]);
		await expect(resultLink("3 not finished yet").searchParams.getAll("status")).toStrictEqual([
			"QUEUED",
			"RUNNING",
		]);
		canvas.getByRole("link", { name: "9 reviews finished after the merge" });
		await expect(canvas.queryByText(/recorded nothing/u)).not.toBeInTheDocument();

		// The range belongs to the whole page: the practices' sentence follows it.
		canvas.getByText(
			"How the reviews judged each practice in the last 30 days, the ones that need a look first.",
		);
		await userEvent.click(canvas.getByRole("button", { name: "90 days" }));
		await expect(args.onRangeChange).toHaveBeenCalledWith("90d");
		canvas.getByText(
			"How the reviews judged each practice in the last 90 days, the ones that need a look first.",
		);
	},
};

/**
 * Today is a range of its own, so the day's reviews are counted as they arrive and set against
 * yesterday. The rates under Delivery and the practices' sentence follow it.
 */
export const Today: Story = {
	args: {
		range: "today",
		previous: {
			status: "ready",
			period: { overview: practiceReviewOverview },
		},
	},
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.getByRole("button", { name: "Today" })).toHaveAttribute(
			"aria-pressed",
			"true",
		);
		canvas.getByText(
			"How the reviews judged each practice today, the ones that need a look first.",
		);
		await userEvent.click(canvas.getByRole("button", { name: "7 days" }));
		await expect(args.onRangeChange).toHaveBeenCalledWith("7d");
		canvas.getByText(
			"How the reviews judged each practice in the last 7 days, the ones that need a look first.",
		);
	},
};

/**
 * All time in place of 12 months: every review since the workspace's first. There is no period
 * before it, so no total is set against anything, and the lists its counts open have no days.
 */
export const AllTime: Story = {
	args: {
		range: "all",
		scope: {},
		previous: { status: "none" },
		delivery: {
			...meta.args.delivery,
			unprocessedResults: {
				...meta.args.delivery.unprocessedResults,
				list: { list: "runs", search: { resultProcessing: ["FAILED"] } },
			},
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "All time" })).toHaveAttribute(
			"aria-pressed",
			"true",
		);
		await expect(canvas.queryByRole("button", { name: "12 months" })).not.toBeInTheDocument();
		await expect(canvas.queryByText(/previous/u)).not.toBeInTheDocument();
		canvas.getByText(
			"How the reviews judged each practice over all time, the ones that need a look first.",
		);
		const delivered = new URL(
			canvas.getByRole<HTMLAnchorElement>("link", { name: "14 delivered" }).href,
		);
		await expect(delivered.searchParams.has("from")).toBe(false);
		// No list a count opens carries days.
		const withDays = canvas
			.getAllByRole<HTMLAnchorElement>("link")
			.filter((link) => new URL(link.href).searchParams.has("from"))
			.map((link) => link.textContent);
		await expect(withDays).toStrictEqual([]);
	},
};

export const Reflow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async ({ canvas }) => {
		canvas.getByRole("heading", { name: "Practices", level: 2 });
		await expectNoPageOverflow();
	},
};

/** A course afternoon that needs a look: 30 % of the prepared feedback came too late for the merge. */
const COURSE_DAY = {
	...practiceReviewOverview,
	feedback: {
		...feedbackCounts([]),
		delivered: 60,
		prepared: 5,
		failed: 2,
		suppressed: 30,
		discarded: 3,
		superseded: 20,
	},
	practices: [
		{
			feedback: feedbackCounts([]),
			practiceSlug: "thin-controllers",
			practiceName: "Wiki pages are kept up to date",
			practiceResults: { met: 0, notMet: 0, notApplicable: 29, undetermined: 1 },
			practiceResultsInvalidated: 0,
		},
		...practiceReviewOverview.practices.filter(
			(practice) => practice.practiceSlug !== "thin-controllers",
		),
	],
};

/**
 * Every attention signal past its threshold at once, as a course day can have it: the queue backed
 * up past half an hour, and a practice that mostly does not apply leading the table. Each says so in its own tone, and nothing else on the page takes a colour.
 */
export const NeedsAttention: Story = {
	args: {
		queue: {
			status: "ready",
			queue: { ...busyReviewQueue, waiting: 14, oldestWaitingSince: minutesBefore(42) },
		},
		overview: readyReviewOverview(COURSE_DAY),
		timing: {
			status: "ready",
			timing: {
				...practiceReviewTiming,
				total: { count: 140, medianSeconds: 25 * 60, p90Seconds: 3 * 3600 + 54 * 60 },
				feedbackWithheldAsMerged: 21,
			},
			stale: false,
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("Mostly not applicable");
	},
};

/**
 * Reviews cannot run, so the page says so first; the route passes the banner only while something
 * stops them, so a working workspace is not told so on every visit.
 */
export const ReviewsCannotStart: Story = {
	args: {
		banner: <ReviewRunningBanner running={{ enabled: true, model: { status: "ready" } }} />,
	},
	play: async ({ canvas }) => {
		const banner = canvas.getByText("Reviews cannot start");
		await expect(precedes(banner, canvas.getByRole("heading", { name: "Needs you" }))).toBe(true);
	},
};

/**
 * A range just chosen is loading: the previous range's figures stand in, drained of colour and
 * marked busy, and after a moment the range control says "Updating…" where the reader pressed.
 */
export const RangeUpdating: Story = {
	args: { overview: { status: "ready", overview: practiceReviewOverview, stale: true } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("list", { name: "Stages" })).toHaveAttribute("aria-busy", "true");
		const practices = within(canvas.getByRole("region", { name: "Practices" }));
		await expect(practices.getByRole("table")).toHaveAttribute("aria-busy", "true");
		await expect(await canvas.findByText("Updating…", undefined, { timeout: 3000 })).toBeVisible();
	},
};

/**
 * One read feeds the stages, Delivery and the practices, so its failure is said once, with one
 * retry, and Delivery and the practices are left out rather than failing a second time. What is owed still answers.
 */
export const OverviewFailed: Story = {
	args: {
		overview: {
			status: "error",
			error: { status: 500 },
			onRetry: fn(),
		},
	},
	play: async ({ canvas }) => {
		canvas.getByText("We could not load what the reviews did");
		await expect(canvas.queryByRole("heading", { name: "Practices" })).not.toBeInTheDocument();
		// Delivery counts from the same overview, so its failure is not said a second time.
		await expect(canvas.queryByRole("heading", { name: "Delivery" })).not.toBeInTheDocument();
		canvas.getByRole("link", { name: /^Review \d+ (?:piece|pieces) of feedback$/u });
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};
