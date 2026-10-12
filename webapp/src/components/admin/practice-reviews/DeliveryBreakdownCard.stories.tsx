import type { Meta, StoryContext, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/stories/reflow";

import { DeliveryBreakdownCard } from "./DeliveryBreakdownCard";
import {
	deliveredByChannelCounts,
	practiceReviewOverview,
	readyReviewOverview,
	reviewOverviewScope,
	withheldByReasonCounts,
} from "./fixtures";
import type { ReviewListTarget } from "./review-outcomes";
import type { ReviewCountState } from "./review-states";

const FAILED_DELIVERIES: ReviewListTarget = {
	list: "feedback",
	search: {
		from: reviewOverviewScope.from,
		to: reviewOverviewScope.to,
		deliveryState: ["FAILED", "PARTIALLY_FAILED"],
	},
};

const UNPROCESSED_RESULTS: ReviewListTarget = {
	list: "runs",
	search: {
		from: reviewOverviewScope.from,
		to: reviewOverviewScope.to,
		resultProcessing: ["FAILED"],
	},
};

function counted(total: number, stale = false): ReviewCountState {
	return { status: "ready", total, stale };
}

/** A split's rows as "label: count", in their order. */
function rowsOf(canvas: StoryContext["canvas"], label: string) {
	return [...canvas.getByRole("table", { name: label }).querySelectorAll("tr")].map(
		(row) =>
			`${row.querySelector("th")?.textContent ?? ""}: ${row.querySelector("td")?.textContent ?? ""}`,
	);
}

/** A failure figure's row of the card's foot, which carries its tone. */
function rateOf(canvas: StoryContext["canvas"], term: RegExp) {
	return canvas.getByText(term, { selector: "[data-rate]" }).parentElement;
}

/** Where a count's link opens, as the search params the Feedback table reads. */
function linkOf(canvas: StoryContext["canvas"], name: string) {
	const url = new URL(canvas.getByRole<HTMLAnchorElement>("link", { name }).href);
	// A list is a repeated key: one value reads as itself, several as the list.
	const search = Object.fromEntries(
		[...new Set(url.searchParams.keys())].map((key) => {
			const values = url.searchParams.getAll(key);
			return [key, values.length > 1 ? values : values[0]];
		}),
	);
	return { path: url.pathname, search };
}

/**
 * Under Delivery, beside the Feedback tile: where the feedback of the range went. Delivered
 * feedback by channel and withheld feedback by reason, two short tables in the Feedback table's
 * filter words, each row opening the table filtered to it; then the failure figures as the foot.
 */
const meta = {
	component: DeliveryBreakdownCard,
	decorators: [
		(Story) => (
			<div className="flex max-w-xl">
				<Story />
			</div>
		),
		withStandardPage,
	],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		scope: reviewOverviewScope,
		range: { label: "Last 30 days", during: "in the last 30 days" },
		overview: readyReviewOverview(),
		delivered: { status: "ready", counts: deliveredByChannelCounts, stale: false },
		withheld: { status: "ready", counts: withheldByReasonCounts, stale: false },
		failedDeliveries: { state: counted(1), list: FAILED_DELIVERIES, of: 15 },
		unprocessedResults: { state: counted(1), list: UNPROCESSED_RESULTS, of: 31 },
	},
	argTypes: {
		overview: { control: false },
		delivered: { control: false },
		withheld: { control: false },
		failedDeliveries: { control: false },
		unprocessedResults: { control: false },
	},
} satisfies Meta<typeof DeliveryBreakdownCard>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * The 14 delivered pieces by channel and the 4 withheld by reason, the admin's rejection among
 * them, add up to the Feedback tile's counts. Each count opens exactly its rows over the range.
 */
export const Default: Story = {
	play: async ({ canvas, userEvent }) => {
		await expect(rowsOf(canvas, "Delivered by channel")).toEqual([
			"On GitHub: 8",
			"With Heph in Slack: 2",
			"On their practice pages: 4",
		]);
		await expect(rowsOf(canvas, "Withheld by reason")).toEqual([
			"The work moved on: 2",
			"Policy or settings: 1",
			"Rejected: 1",
		]);
		await expect(linkOf(canvas, "8, On GitHub")).toEqual({
			path: "/w/demo/admin/practices/reviews/feedback",
			search: {
				from: reviewOverviewScope.from,
				to: reviewOverviewScope.to,
				deliveryState: ["DELIVERED", "PARTIALLY_DELIVERED"],
				location: "GITHUB",
			},
		});
		await expect(linkOf(canvas, "2, The work moved on").search).toEqual({
			from: reviewOverviewScope.from,
			to: reviewOverviewScope.to,
			deliveryState: "SUPPRESSED",
			withheldFamily: "WORK_MOVED_ON",
		});
		await expect(linkOf(canvas, "1, Rejected").search.deliveryState).toBe("DISCARDED");
		// Each split heads its table with its total, which the Feedback tile beside it counts too.
		await expect(canvas.getByText("Delivered by channel").nextElementSibling).toHaveTextContent(
			"14",
		);
		await expect(canvas.getByText("Withheld by reason").nextElementSibling).toHaveTextContent("4");
		// The whole row is the link: its words stretch over the row.
		await expect(canvas.getByRole("link", { name: "8, On GitHub" }).closest("tr")).toHaveClass(
			"relative",
		);
		// The failure figures are the card's foot, over the range on screen, as its (i) says.
		await expect(rateOf(canvas, /^Feedback failed to deliver/u)).toHaveAttribute(
			"data-tone",
			"destructive",
		);
		await expect(linkOf(canvas, "1 of 15 sent").search.deliveryState).toStrictEqual([
			"FAILED",
			"PARTIALLY_FAILED",
		]);
		// Beside it, over the range too, the reviews whose results could not be processed or
		// delivered: 1 of the 31 that completed is 3 %, within its 5 %, so grey.
		await expect(rateOf(canvas, /^Reviews not processed or delivered/u)).toHaveAttribute(
			"data-tone",
			"secondary",
		);
		await expect(linkOf(canvas, "1 of 31 completed").search.resultProcessing).toBe("FAILED");
		await userEvent.click(canvas.getByRole("button", { name: "About channels and reasons" }));
		await expect(
			await screen.findByRole("dialog", { name: "About channels and reasons" }),
		).toHaveTextContent(
			"Feedback failed to deliver is the share of the feedback sent that failed. Above 5 % it is red.",
		);
	},
};

/**
 * More than 5 % of the completed reviews' results were not processed: still plain, as the overview
 * colours only what is broken and fixable now, in red.
 */
export const ResultsOverThreshold: Story = {
	args: { unprocessedResults: { state: counted(3), list: UNPROCESSED_RESULTS, of: 31 } },
	play: async ({ canvas }) => {
		await expect(rateOf(canvas, /^Reviews not processed or delivered/u)).toHaveAttribute(
			"data-tone",
			"secondary",
		);
	},
};

/** 1 of 20 is 5 %: at the mark, not past it, so grey. */
export const RateWithinThreshold: Story = {
	args: { failedDeliveries: { state: counted(1), list: FAILED_DELIVERIES, of: 20 } },
	play: async ({ canvas }) => {
		await expect(rateOf(canvas, /^Feedback failed to deliver/u)).toHaveAttribute(
			"data-tone",
			"secondary",
		);
		canvas.getByText("5 %");
	},
};

/** Withheld feedback whose reason is not recorded is a row, but no filter selects it alone. */
export const ReasonNotRecorded: Story = {
	args: {
		withheld: {
			status: "ready",
			counts: [...withheldByReasonCounts, { count: 1 }],
			stale: false,
		},
	},
	play: async ({ canvas }) => {
		await expect(rowsOf(canvas, "Withheld by reason")).toContain("Reason not recorded: 1");
		await expect(canvas.queryByRole("link", { name: /Reason not recorded/u })).toBeNull();
	},
};

/** Nothing delivered or withheld, and nothing failed: each part says so in a line. */
export const NothingYet: Story = {
	args: {
		overview: readyReviewOverview({
			...practiceReviewOverview,
			feedback: { ...practiceReviewOverview.feedback, suppressed: 0, discarded: 0 },
		}),
		delivered: { status: "ready", counts: [], stale: false },
		withheld: { status: "ready", counts: [], stale: false },
		failedDeliveries: { state: counted(0), list: FAILED_DELIVERIES, of: 0 },
		unprocessedResults: { state: counted(0), list: UNPROCESSED_RESULTS, of: 0 },
	},
	play: async ({ canvas }) => {
		canvas.getByText("Nothing delivered in the last 30 days");
		canvas.getByText("Nothing withheld in the last 30 days");
		await expect(canvas.queryByRole("link")).toBeNull();
	},
};

export const Loading: Story = {
	args: {
		delivered: { status: "loading" },
		withheld: { status: "loading" },
		failedDeliveries: { state: { status: "loading" }, list: FAILED_DELIVERIES },
		unprocessedResults: { state: { status: "loading" }, list: UNPROCESSED_RESULTS },
	},
	play: async ({ canvas }) => {
		canvas.getByText("Loading delivered feedback by channel");
		canvas.getByText("Loading withheld feedback by reason");
		canvas.getByText("Loading feedback that failed to deliver");
		canvas.getByText("Loading reviews whose results could not be processed or delivered");
	},
};

/** One split failed: it says so with its retry, and the other still answers. */
export const OnePartFailed: Story = {
	args: { withheld: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async ({ canvas }) => {
		canvas.getByText("We could not load withheld feedback by reason");
		await expect(rowsOf(canvas, "Delivered by channel")).toHaveLength(3);
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

/** The previous range's counts stand in while the new ones load: drained and marked busy. */
export const Stale: Story = {
	args: {
		delivered: { status: "ready", counts: deliveredByChannelCounts, stale: true },
		failedDeliveries: { state: counted(1, true), list: FAILED_DELIVERIES, of: 15 },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("table", { name: "Delivered by channel" })).toHaveAttribute(
			"aria-busy",
			"true",
		);
		await expect(rateOf(canvas, /^Feedback failed to deliver/u)).toHaveAttribute(
			"aria-busy",
			"true",
		);
	},
};

/** At 320 px long channel names truncate before the count, and nothing scrolls sideways. */
export const Narrow: Story = {
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
	play: async () => {
		await expectNoPageOverflow();
	},
};

export const Dark: Story = {
	globals: { theme: "dark" },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("link", { name: "8, On GitHub" })).toBeVisible();
	},
};
