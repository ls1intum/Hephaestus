import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewSummary } from "@/api/types.gen";
import { formatWeekdayDay } from "@/lib/dates";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { settledPopup } from "@/stories/overlay";
import { loadedList, narrowedList } from "@/stories/paged-list";
import { expectNoPageOverflow } from "@/stories/reflow";
import { StatefulPatch } from "@/stories/stateful";
import { STORY_NOW } from "@/stories/story-clock";
import { levelsOpenedBy } from "@/test/detail-stack";

import { reviews } from "./fixtures";
import { REVIEWABLE_ON_DEMAND } from "./job-utils";
import { REVIEW_PAGE_SIZE, type ReviewsSearch, reviewsQuery } from "./review-search";
import { type ReviewRequestActions, ReviewsPage } from "./ReviewsPage";
import { STORY_GROUPS, STORY_PEOPLE, STORY_PRACTICES } from "./story-scope-sources";

const loadMoreReviews = fn();
const retryReviews = fn();

/** Request review as the route wires it, for the kinds a person can ask about. */
function storyRequestActions(): ReviewRequestActions {
	return {
		canRequest: (item) => REVIEWABLE_ON_DEMAND.has(item.kind),
		onRequest: fn(),
		isRequesting: () => false,
	};
}

/** The list's own name, which the table carries while it loads and once it has rows. */
const LIST = { name: "Practice reviews, newest first" } as const;

/** The heading a day's reviews sit under, as the table names it for the reader's clock. */
const dayOf = (review: ReviewSummary) => formatWeekdayDay(review.createdAt, new Date(STORY_NOW));

/**
 * The reviews the endpoint would return for a search, computed from the fixture instead of mocked
 * over HTTP, through `reviewsQuery`, the very transformation the route sends, so a story cannot
 * "prove" a filter the screen never asks for.
 */
function reviewsFor(
	list: PagedListState<ReviewSummary>,
	search: ReviewsSearch,
): PagedListState<ReviewSummary> {
	const query = reviewsQuery(search, REVIEW_PAGE_SIZE);
	return narrowedList(
		list,
		(review) =>
			(query.status === undefined || query.status.includes(review.status)) &&
			(!query.from || review.createdAt >= query.from) &&
			(!query.to || review.createdAt < query.to),
		REVIEW_PAGE_SIZE,
	);
}

const meta = {
	component: ReviewsPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		search: { status: undefined },
		onSearchChange: fn(),
		reviews: loadedList(reviews),
		groups: STORY_GROUPS,
		practices: STORY_PRACTICES,
		people: STORY_PEOPLE,
	},
	// The screen is controlled: with a frozen `search` prop every facet reads as dead. The rows
	// follow the search the same way the route's query would.
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, patch) => (
				<ReviewsPage
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
} satisfies Meta<typeof ReviewsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * The reviews as the developer's own list draws them, one body per day under its heading: the time
 * and status, the work, and what started it. A row opens its review over the list.
 */
export const Default: Story = {
	parameters: { viewport: { defaultViewport: "desktop" } },
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		const days = [...new Set(reviews.map(dayOf))];
		await expect(days.length).toBeGreaterThan(1);
		for (const day of days) {
			within(table).getByText(day);
		}
		await expect(within(table).getAllByText("Complete").length).toBeGreaterThan(0);
		await expect(within(table).queryByRole("button", { name: "Complete" })).not.toBeInTheDocument();
		// One link per row, the review: the work is text, not a link to the provider.
		await expect(
			within(table).queryByRole("link", { name: /opens in a new tab/u }),
		).not.toBeInTheDocument();
		await expect(
			levelsOpenedBy(within(table).getByRole("link", { name: /^Open review of #1423,/u })),
		).toEqual(["review:11111111-1111-1111-1111-111111111111"]);
	},
};

/** Choosing a status narrows the list to the reviews in it. */
export const StatusFilter: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: /^Status/u }));
		await settledPopup();
		const listbox = screen.getByRole("listbox", { name: "Status options" });
		await userEvent.click(within(listbox).getByRole("option", { name: /Failed/u }));
		await userEvent.keyboard("{Escape}");
		await waitFor(() => {
			void expect(screen.queryByRole("listbox", { name: "Status options" })).toBeNull();
		});
		await expect(args.onSearchChange).toHaveBeenLastCalledWith({ status: ["FAILED"] });
		const table = await canvas.findByRole("table", LIST);
		await expect(within(table).queryByText("Complete")).not.toBeInTheDocument();
	},
};

/**
 * Request review, a row action: it names the work it would review, and is blocked with the reason
 * while a review of the same work is queued or running.
 */
export const RequestReviewFromARow: Story = {
	args: { requestReview: storyRequestActions() },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		await expect(
			within(table).getAllByRole("button", { name: /^Request review of /u }).length,
		).toBeGreaterThan(0);
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("table", LIST);
		await expectNoPageOverflow();
	},
};

/** The skeleton draws `REVIEW_PAGE_SIZE` rows, so the first page replaces it without a jump. */
export const Loading: Story = {
	args: { reviews: { status: "loading" } },
	parameters: { chromatic: { viewports: [1440] } },
	render: (args) => <ReviewsPage {...args} />,
	play: async ({ canvas }) => {
		const table = await canvas.findByRole("table", LIST);
		await expect(table).toHaveAttribute("aria-busy", "true");
		await expect(within(table).queryByRole("link")).not.toBeInTheDocument();
	},
};

export const NoReviewsYet: Story = {
	args: { reviews: loadedList([]) },
	parameters: { chromatic: { viewports: [1440] } },
	render: (args) => <ReviewsPage {...args} />,
	play: async ({ canvas }) => {
		await canvas.findByText("No reviews yet");
		// Nothing is filtered, so the empty state must not offer an action that would change nothing.
		await expect(
			canvas.queryByRole("button", { name: "Clear all filters" }),
		).not.toBeInTheDocument();
	},
};

/** A filter that matches nothing says so and offers to clear every filter. */
export const NoReviewsMatch: Story = {
	args: { reviews: loadedList([]), search: { status: ["CANCELLED"] } },
	parameters: { chromatic: { viewports: [1440] } },
	render: (args) => <ReviewsPage {...args} />,
	play: async ({ canvas }) => {
		await canvas.findByText("No reviews match these filters");
		canvas.getByRole("button", { name: "Clear all filters" });
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
	render: (args) => <ReviewsPage {...args} />,
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
	args: { reviews: loadedList(reviews, { hasMore: true, onLoadMore: loadMoreReviews }) },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		const more = await canvas.findByRole("button", { name: "Show more reviews" });
		more.scrollIntoView();
		await waitFor(async () => expect(loadMoreReviews).toHaveBeenCalled());
	},
};
