import type { Meta, StoryContext, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewRunSummary } from "@/api/types.gen";
import { formatWeekdayDay } from "@/lib/dates";
import { hasText } from "@/lib/text";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { settledPopup } from "@/stories/overlay";
import { loadedList, narrowedList } from "@/stories/paged-list";
import { expectNoPageOverflow } from "@/stories/reflow";
import { StatefulPatch } from "@/stories/stateful";
import { STORY_NOW } from "@/stories/story-clock";
import { levelsOpenedBy } from "@/test/detail-stack";

import { tracedArtifacts } from "@/components/practice-trace/fixtures";

import { reviewRuns } from "./fixtures";
import { REQUEST_BLOCKED } from "./RequestReviewAction";
import { REVIEW_PAGE_SIZE, type RunsSearch, runsQuery } from "./review-search";
import { type ReviewRequestActions, ReviewRunsPage } from "./ReviewRunsPage";
import { STORY_GROUPS, STORY_PEOPLE, STORY_PRACTICES } from "./story-scope-sources";

const loadMoreReviews = fn();

/** Request review as the route wires it, for the kinds a person can ask about. */
function storyRequestActions(): ReviewRequestActions {
	return {
		canRequest: (work) =>
			work.artifactKind === "scm.pull_request" || work.artifactKind === "scm.issue",
		onRequest: fn(),
		isRequesting: () => false,
		refusalFor: () => undefined,
	};
}
const retryReviews = fn();

/** The fixture's reviews, one of which completed and then failed to process what it produced. */
const RUNS: ReviewRunSummary[] = reviewRuns.map((run) =>
	run.id === "11111111-1111-1111-1111-111111111111" ? { ...run, resultProcessing: "FAILED" } : run,
);

/** The work no review started on, as the route's `withoutReview` read returns it. */
const UNREVIEWED = tracedArtifacts.filter((work) => work.reviewedSignalCount === 0);

/** The list's own name, which the table carries while it loads and once it has rows. */
const LIST = { name: "Practice reviews, newest first" } as const;

/** The heading a day's reviews sit under, as the table names it for the reader's clock. */
const dayOf = (run: ReviewRunSummary) => formatWeekdayDay(run.createdAt, new Date(STORY_NOW));

/**
 * A second review of the work the first completed review read, asked for by hand an hour later and
 * about two developers, so one list shows a later review, every origin and a shared piece of work.
 */
function withSecondReview(runs: ReviewRunSummary[]): ReviewRunSummary[] {
	const first = runs.find((run) => run.id === "11111111-1111-1111-1111-111111111111");
	if (!first) {
		throw new Error("The fixture has no first review of #1423");
	}
	const second: ReviewRunSummary = {
		...first,
		id: "11111111-2222-2222-2222-222222222222",
		createdAt: new Date(first.createdAt.getTime() + 60 * 60_000),
		resultProcessing: "DELIVERED",
		origin: "MANUAL",
		positionOnWork: 2,
		developers: [...first.developers, { id: 9, login: "grace", name: "Grace Hopper" }],
	};
	return [second, ...runs].sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
}

/**
 * The reviews the endpoint would return for a search, computed from the fixture instead of mocked
 * over HTTP. The screen takes its rows as a prop, so a story that wants to prove a facet
 * narrows the list has to answer it — and answering it in a function keeps the whole file
 * network-free, which is what stops one story's failure from becoming every story's failure on the
 * shared Docs page.
 *
 * It filters through `runsQuery`, the very transformation the route sends, so a story cannot
 * "prove" a window the screen never asks for.
 */
function reviewsFor(
	list: PagedListState<ReviewRunSummary>,
	search: RunsSearch,
): PagedListState<ReviewRunSummary> {
	const query = runsQuery(search, REVIEW_PAGE_SIZE);
	return narrowedList(
		list,
		(run) =>
			// The listed statuses, "On hold" and "Waiting" are alternatives, as the endpoint applies them.
			((query.status === undefined && query.onHold !== true && query.waiting !== true) ||
				(query.status?.includes(run.status) ?? false) ||
				(query.onHold === true && run.status === "QUEUED" && hasText(run.holdReason)) ||
				(query.waiting === true && run.status === "QUEUED" && run.holdReason !== "BUDGET")) &&
			(query.resultProcessing === undefined ||
				(run.resultProcessing !== undefined &&
					query.resultProcessing.includes(run.resultProcessing))) &&
			(!query.from || run.createdAt >= query.from) &&
			(!query.to || run.createdAt < query.to),
		query.size,
	);
}

/**
 * Toggles one status in the facet and closes it again, waiting out both transitions: a press on the
 * trigger while the popup is still leaving lands on its inert layer.
 */
async function pickStatus(
	canvas: StoryContext["canvas"],
	userEvent: {
		click: (element: Element) => Promise<void>;
		keyboard: (text: string) => Promise<void>;
	},
	option: RegExp,
) {
	await userEvent.click(canvas.getByRole("combobox", { name: /^Status/u }));
	await settledPopup();
	const listbox = screen.getByRole("listbox", { name: "Status options" });
	await userEvent.click(within(listbox).getByRole("option", { name: option }));
	await userEvent.keyboard("{Escape}");
	await waitFor(() => {
		void expect(screen.queryByRole("listbox", { name: "Status options" })).toBeNull();
	});
}

const meta = {
	component: ReviewRunsPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		search: { status: undefined, resultProcessing: undefined },
		onSearchChange: fn(),
		reviews: loadedList(RUNS),
		workspaceSlug: "demo",
		unreviewed: loadedList(UNREVIEWED),
		groups: STORY_GROUPS,
		practices: STORY_PRACTICES,
		people: STORY_PEOPLE,
	},
	// The screen is controlled: with a frozen `search` prop every facet reads as dead. The rows
	// follow the search the same way the route's query would.
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, patch) => (
				<ReviewRunsPage
					{...args}
					search={search}
					onSearchChange={(next) => {
						patch(next);
						args.onSearchChange(next);
					}}
					reviews={reviewsFor(args.reviews, search)}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ReviewRunsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * The reviews as the developer's own list draws them, one body per day under its heading: the time
 * and status, the work, who it was about, and what it saw. A row opens its review over the list.
 */
export const Default: Story = {
	parameters: { viewport: { defaultViewport: "desktop" } },
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		// Several days, each named once, newest first.
		const days = [...new Set(RUNS.map(dayOf))];
		await expect(days.length).toBeGreaterThan(1);
		for (const day of days) {
			within(table).getByText(day);
		}
		within(table).getByText("Ada Lovelace");
		// The status is the icon, named for a screen reader, and no button.
		await expect(within(table).getAllByText("Completed").length).toBeGreaterThan(0);
		await expect(
			within(table).queryByRole("button", { name: "Completed" }),
		).not.toBeInTheDocument();
		// One link per row, the review: the work is text, not a link to the provider.
		await expect(
			within(table).queryByRole("link", { name: /opens in a new tab/u }),
		).not.toBeInTheDocument();
		// The whole row opens the review, through a real link at its end.
		await expect(
			levelsOpenedBy(within(table).getByRole("link", { name: /^Open review of #1423,/u })),
		).toEqual(["review:11111111-1111-1111-1111-111111111111"]);
		within(table).getByText("Result processing failed");
	},
};

/**
 * The list has no Result column: a maintainer scans it for the reviews that need a look, so their
 * notable states stand as chips beside the work, and what each review found is on its sheet.
 */
export const WhatEachReviewSaw: Story = {
	parameters: { viewport: { defaultViewport: "desktop" }, chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		await expect(
			within(table).queryByRole("columnheader", { name: "Result" }),
		).not.toBeInTheDocument();
		await expect(within(table).queryByText(/^\d+ not met$/u)).not.toBeInTheDocument();
		await expect(within(table).queryByText("Nothing to improve")).not.toBeInTheDocument();
		within(table).getByText("Result processing failed");
		await expect(within(table).queryByText(/^Feedback:/u)).not.toBeInTheDocument();
	},
};

/** A review still going says so rather than claiming it found nothing. */
export const Running: Story = {
	args: { reviews: loadedList(RUNS.filter((run) => run.status === "RUNNING")) },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		within(table).getByText("Running");
		// The icon says it is running; the result stays empty rather than claiming nothing slipped.
		await expect(within(table).queryByText("Nothing to improve")).not.toBeInTheDocument();
		// Before it observes anyone, the review already names whose work it reads.
		within(table).getByText("Grace Hopper");
	},
};

/** A review that stopped is marked by its status icon and a chip beside the work. */
export const Failed: Story = {
	args: { reviews: loadedList(RUNS.filter((run) => run.status === "FAILED")) },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		// The status icon and the chip beside the work.
		await expect(within(table).getAllByText("Failed")).toHaveLength(2);
		await expect(within(table).queryByText("Nothing to improve")).not.toBeInTheDocument();
	},
};

/**
 * Reviews occasioned three ways in one list. How each was occasioned is on its sheet, not in the
 * row. A later review of the same work is tagged, and a review about two people names the first and
 * counts the other.
 */
export const MixedTriggers: Story = {
	args: { reviews: loadedList(withSecondReview(RUNS)) },
	parameters: { viewport: { defaultViewport: "desktop" }, chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		await expect(within(table).queryByText("Requested")).not.toBeInTheDocument();
		await expect(within(table).queryByText("Backfilled")).not.toBeInTheDocument();
		within(table).getByText("Review 2");
		within(table).getByText("+1 more");
	},
};

/**
 * Every row ends in Track, Trace and Request review. Request review asks first, naming the work, and
 * only then asks for the review; a row whose work has a review queued or running, or whose work
 * nobody can ask about, keeps the button disabled and says why.
 */
export const RequestReviewFromARow: Story = {
	args: { requestReview: storyRequestActions() },
	parameters: { viewport: { defaultViewport: "desktop" }, chromatic: { viewports: [1440] } },
	play: async ({ canvas, args, userEvent }) => {
		const table = await canvas.findByRole("table", LIST);
		const onRequest = args.requestReview?.onRequest;
		// #1436 is still running: asking again would only join it.
		const running = within(table).getByRole("button", {
			name: "Request review of pull request #1436",
		});
		await expect(running).toHaveAttribute("aria-disabled", "true");
		await userEvent.hover(running);
		await waitFor(async () => expect(screen.getByText(REQUEST_BLOCKED.active)).toBeVisible());
		await userEvent.unhover(running);
		// A conversation is reviewed on its own occasions only.
		await expect(
			within(table).getByRole("button", { name: /^Request review of conversation/u }),
		).toHaveAttribute("aria-disabled", "true");
		const [first] = within(table).getAllByRole("button", {
			name: "Request review of pull request #1423",
		});
		if (first === undefined) {
			throw new Error("No row of #1423");
		}
		await userEvent.click(first);
		const dialog = await screen.findByRole("alertdialog", {
			name: "Request a review of pull request #1423?",
		});
		await expect(onRequest).not.toHaveBeenCalled();
		await userEvent.click(within(dialog).getByRole("button", { name: "Request review" }));
		await expect(onRequest).toHaveBeenCalledWith(
			expect.objectContaining({ artifactKind: "scm.pull_request" }),
		);
	},
};

/** Statuses add up: each one chosen widens the list by the reviews in it. */
export const StatusFilter: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByRole("table", LIST);
		await pickStatus(canvas, userEvent, /Failed/u);
		await canvas.findByText("1 review matches your filters.");
		await pickStatus(canvas, userEvent, /Running/u);
		await canvas.findByText("2 reviews match your filters.");
		await userEvent.click(canvas.getByRole("button", { name: "Reset" }));
		await canvas.findByText("7 reviews.");
	},
};

/** A queued review held for the AI budget, newest on the list. */
function queuedOnHold(): ReviewRunSummary {
	const [newest] = RUNS;
	if (newest === undefined) {
		throw new Error("The fixture has no reviews");
	}
	return { ...newest, id: "eeeeeeee-0000-0000-0000-000000000001", status: "QUEUED" };
}

/**
 * "On hold" is a Status value of its own: a queued review waiting on a hold, such as the AI budget.
 * Alone it lists only those; beside another status it adds them.
 */
export const OnHoldFilter: Story = {
	args: {
		reviews: loadedList([{ ...queuedOnHold(), holdReason: "BUDGET" }, ...RUNS]),
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		const table = await canvas.findByRole("table", LIST);
		await pickStatus(canvas, userEvent, /On hold/u);
		await canvas.findByText("1 review matches your filters.");
		within(table).getByText("On hold");
		await pickStatus(canvas, userEvent, /Failed/u);
		await canvas.findByText("2 reviews match your filters.");
	},
};

/**
 * A completed review whose results could not be processed is found by that failure, which its status
 * alone does not tell apart from a review that finished cleanly.
 */
export const ResultProcessingFilter: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("7 reviews.");
		const trigger = canvas.getByRole("combobox", { name: /^Result processing/u });
		await userEvent.click(trigger);
		await settledPopup();
		const listbox = screen.getByRole("listbox", { name: "Result processing options" });
		await userEvent.click(
			within(listbox).getByRole("option", { name: /Result processing failed/u }),
		);
		await userEvent.keyboard("{Escape}");
		await canvas.findByText("1 review matches your filters.");
		const table = canvas.getByRole("table", LIST);
		within(table).getByText("Cache the workspace member lookup on the review path");
		within(table).getByText("Result processing failed");
		await waitFor(() => {
			void expect(screen.queryByRole("listbox")).toBeNull();
		});
	},
};

/**
 * The range arrives through `args` rather than through the calendar: the state worth pinning is a
 * populated filtered list that then intersects with the status filter, which clicking two days on an
 * empty toolbar does not reach.
 */
export const FilterByCreatedDate: Story = {
	args: {
		search: {
			status: undefined,
			resultProcessing: undefined,
			from: "2026-07-28",
			to: "2026-07-29",
		},
	},
	parameters: { viewport: { defaultViewport: "desktop" }, chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("3 reviews match your filters.");
		const table = canvas.getByRole("table", LIST);
		within(table).getByText(/Retry webhook deliveries with backoff/u);
		within(table).getByText("Cache the workspace member lookup on the review path");
		await expect(
			within(table).queryByText(/Move invoice numbering behind the billing boundary/u),
		).not.toBeInTheDocument();

		canvas.getByRole("button", { name: "Created: 28 Jul – 29 Jul 2026" });

		// A status intersects with the range rather than replacing it.
		await pickStatus(canvas, userEvent, /Completed/u);
		await canvas.findByText("2 reviews match your filters.");

		// The failed review was requested outside the window, so failed alone intersects to nothing.
		await pickStatus(canvas, userEvent, /Failed/u);
		await pickStatus(canvas, userEvent, /Completed/u);
		await canvas.findByText("No reviews match these filters");
		canvas.getByText("Change or clear the filters to see more.");

		// One button clears the range and the status together.
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await canvas.findByText("7 reviews.");
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { viewports: [320, 768] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		await within(table).findByText(/Retry webhook deliveries with backoff/u);
		await expectNoPageOverflow();
	},
};

/**
 * `DateRangeFacet` keeps its badge at every width, unlike `FacetMultiSelect`, which collapses to
 * "N selected" — so the separate applied-pill row the sibling lists carry deliberately leaves the
 * date range out, rather than printing the same range twice.
 */
export const MobileAppliedDateRange: Story = {
	args: {
		search: {
			status: undefined,
			resultProcessing: undefined,
			from: "2026-07-28",
			to: "2026-07-29",
		},
	},
	parameters: { chromatic: { viewports: [320] }, viewport: { defaultViewport: "reflow" } },
	play: async ({ canvas }) => {
		await canvas.findByText("3 reviews match your filters.");
		canvas.getByRole("button", { name: "Created: 28 Jul – 29 Jul 2026" });
		await expectNoPageOverflow();
	},
};

/** The skeleton draws `REVIEW_PAGE_SIZE` rows, so the first page replaces it without a jump. */
export const Loading: Story = {
	args: { reviews: { status: "loading" } },
	parameters: { chromatic: { viewports: [1440] } },
	render: (args) => <ReviewRunsPage {...args} />,
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		await expect(table).toHaveAttribute("aria-busy", "true");
		await expect(within(table).queryByRole("link")).not.toBeInTheDocument();
	},
};

export const NoReviewsYet: Story = {
	args: { reviews: loadedList([]) },
	parameters: { chromatic: { viewports: [1440] } },
	render: (args) => <ReviewRunsPage {...args} />,
	play: async ({ canvas }) => {
		await canvas.findByText("No reviews yet");
		// Nothing is filtered, so the empty state must not offer an action that would change nothing.
		await expect(
			canvas.queryByRole("button", { name: "Clear all filters" }),
		).not.toBeInTheDocument();
	},
};

/** The status decides the wording and whether Retry is offered; this screen only forwards it. */
export const LoadFailed: Story = {
	args: {
		reviews: {
			status: "error",
			error: { status: 500, detail: "The review index is unavailable." },
			onRetry: retryReviews,
		},
	},
	parameters: { chromatic: { viewports: [1440] } },
	render: (args) => <ReviewRunsPage {...args} />,
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("We could not load reviews");
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(retryReviews).toHaveBeenCalled();
	},
};

/**
 * More reviews than the first page: the next page is asked for when the end of the list scrolls into
 * view, with no press.
 */
export const LoadsMoreAtTheEnd: Story = {
	args: { reviews: loadedList(RUNS, { hasMore: true, onLoadMore: loadMoreReviews }) },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const more = await canvas.findByRole("button", { name: "Show more reviews" });
		more.scrollIntoView();
		await waitFor(async () => expect(loadMoreReviews).toHaveBeenCalled());
	},
};

/** The next page on its way: rows in the list's shape stand in for it, and the press waits. */
export const LoadingMore: Story = {
	args: {
		reviews: loadedList(RUNS, { hasMore: true, isLoadingMore: true, onLoadMore: loadMoreReviews }),
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("button", { name: "Loading…" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(loadMoreReviews).not.toHaveBeenCalled();
	},
};

/**
 * The next page failed: the loaded reviews stay, the end says so and asks for nothing by itself, and
 * Retry asks again.
 */
export const LoadMoreFailed: Story = {
	args: {
		reviews: loadedList(RUNS, {
			hasMore: true,
			loadMoreError: new TypeError("Failed to fetch"),
			onLoadMore: loadMoreReviews,
		}),
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByRole("table", LIST);
		await expect(canvas.getByRole("alert")).toHaveTextContent("We could not load more reviews.");
		await expect(loadMoreReviews).not.toHaveBeenCalled();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(loadMoreReviews).toHaveBeenCalledOnce();
	},
};

/**
 * "No review started" in the Status filter swaps the reviews for the work none of whose moments
 * started one, each row saying why. Only the dates and one piece of work narrow it, so a developer in
 * the scope is kept but marked as not applied. A status a review has, chosen next, brings the
 * reviews back.
 */
export const NoReviewStarted: Story = {
	args: { search: { status: undefined, resultProcessing: undefined, subjectUserId: 7 } },
	parameters: { chromatic: { viewports: [320, 1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByRole("table", LIST);
		await expect(canvas.queryByRole("button", { name: "Without a review" })).toBeNull();
		await userEvent.click(canvas.getByRole("combobox", { name: "Status" }));
		await userEvent.click(await screen.findByRole("option", { name: "No review started" }));
		const list = await canvas.findByRole("list", {
			name: "Work without a review, most recent first",
		});
		await expect(within(list).getAllByRole("listitem")).toHaveLength(UNREVIEWED.length);
		// Why no review started is the work's sheet's: the row opens it.
		await expect(
			within(list).queryByText(
				"This workspace’s review settings did not allow a review of this work.",
			),
		).not.toBeInTheDocument();
		await expect(canvas.queryByRole("table", LIST)).not.toBeInTheDocument();
		canvas.getByText("Not applied on this list");
		await userEvent.click(await screen.findByRole("option", { name: "Completed" }));
		await canvas.findByRole("table", LIST);
		await expect(canvas.getByRole("combobox", { name: /^Status/u })).toHaveAccessibleName(
			"Status: Completed",
		);
	},
};

/**
 * Filtered to one piece of work — by an address from before, or the review page — the work is a
 * pill named like it, not the selection. With no review of it, the empty list offers the same work
 * under "No review started", which says why.
 */
export const FilteredToAWorkWithoutReviews: Story = {
	args: {
		search: {
			status: undefined,
			resultProcessing: undefined,
			artifactKind: "scm.issue",
			artifactId: 1430,
		},
		reviews: loadedList([]),
		record: { facet: "work", name: "Issue #1430" },
	},
	play: async ({ canvas, userEvent }) => {
		canvas.getByText("Work: Issue #1430");
		canvas.getByText("No review ran on this work");
		await userEvent.click(canvas.getByRole("button", { name: "Show why no review started" }));
		await canvas.findByRole("list", { name: "Work without a review, most recent first" });
	},
};

/** The status icons, the day headings and the problem markers keep their contrast on dark. */
export const Dark: Story = {
	globals: { theme: "dark" },
	parameters: { viewport: { defaultViewport: "desktop" }, chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("table", LIST);
	},
};

const retryRefresh = fn();
export const RefreshFailed: Story = {
	args: {
		reviews: loadedList(RUNS, {
			refreshFailure: { error: { status: 503 }, pending: false, onRetry: retryRefresh },
		}),
	},
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("These reviews may be out of date");
		await expect(canvas.getByRole("table").querySelectorAll("tbody tr").length).toBeGreaterThan(0);
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(retryRefresh).toHaveBeenCalledOnce();
	},
};
