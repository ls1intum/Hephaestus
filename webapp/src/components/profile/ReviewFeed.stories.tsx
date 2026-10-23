import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, waitFor } from "storybook/test";

import { expectListPlaceholders } from "@/stories/loading-more";
import { detailReviews } from "@/stories/practice-detail-story-mock-data";

import { ReviewFeed } from "./ReviewFeed";
import type { ReviewFeedState } from "./reviews";

const onLoadMore = fn();

const readyFeed = {
	status: "ready",
	reviews: detailReviews,
	hasMore: false,
	isLoadingMore: false,
	onLoadMore,
} satisfies ReviewFeedState;

const meta = {
	component: ReviewFeed,
	tags: ["autodocs"],
	parameters: { layout: "padded" },
	args: {
		feed: readyFeed,
		skeletonRows: 3,
		emptyTitle: "No reviews yet",
		emptyDescription: "Reviews appear here once your work has been reviewed.",
	},
} satisfies Meta<typeof ReviewFeed>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("list", { name: "Reviews" })).toBeVisible();
		await expect(canvas.queryByRole("button", { name: "View earlier reviews" })).toBeNull();
	},
};

export const LoadMore: Story = {
	args: { feed: { ...readyFeed, hasMore: true } },
	play: async ({ canvas, userEvent }) => {
		const press = canvas.getByRole("button", { name: "View earlier reviews" });
		press.scrollIntoView();
		await waitFor(async () => expect(onLoadMore).toHaveBeenCalled());
		const before = onLoadMore.mock.calls.length;
		await userEvent.click(press);
		await expect(onLoadMore).toHaveBeenCalledTimes(before + 1);
	},
};

/** The next page's placeholder cards continue the timeline, in its own shape, while it is busy. */
export const LoadingMore: Story = {
	args: { feed: { ...readyFeed, hasMore: true, isLoadingMore: true } },
	play: async ({ canvas }) => {
		const list = canvas.getByRole("list", { name: "Reviews" });
		// The day and time, then the card's work (title and place) and a result (mark and name).
		await expectListPlaceholders(list, { bars: 6 });
		await expect(list.lastElementChild).toHaveAttribute("data-loading-row");
		await expect(canvas.getByRole("button", { name: "Loading…" })).toHaveAttribute(
			"aria-disabled",
			"true",
		);
		await expect(onLoadMore).not.toHaveBeenCalled();
	},
};

export const LoadMoreFailed: Story = {
	args: { feed: { ...readyFeed, hasMore: true, loadMoreError: new Error("network") } },
	play: async ({ canvas, userEvent }) => {
		await expect(canvas.getByText("We could not load earlier reviews.")).toBeVisible();
		await expect(onLoadMore).not.toHaveBeenCalled();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(onLoadMore).toHaveBeenCalledOnce();
	},
};

export const Loading: Story = {
	args: { feed: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.getByText("Loading reviews…").closest("[aria-busy]")).toHaveAttribute(
			"aria-busy",
			"true",
		);
		await expect(canvas.queryByRole("status")).toBeNull();
	},
};

export const NoReviews: Story = {
	args: { feed: { ...readyFeed, reviews: [] } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("list", { name: "Reviews" })).toBeNull();
		await expect(canvas.queryByRole("button", { name: "View earlier reviews" })).toBeNull();
	},
};

export const NarrowedToNothing: Story = {
	args: {
		feed: { ...readyFeed, reviews: [] },
		emptyDescription: "No reviews mention Scope the change to one concern.",
		emptyAction: <button type="button">Show every review in this group</button>,
	},
};

// Empty loaded pages cannot establish an empty filtered feed while earlier pages remain.
export const NarrowedToNothingSoFar: Story = {
	args: {
		feed: { ...readyFeed, hasMore: true },
		reviews: [],
		emptyAction: <button type="button">Show every review in this group</button>,
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByText("No reviews yet")).toBeNull();
		await expect(canvas.getByText("The latest reviews have no observations here.")).toBeVisible();
		await expect(
			canvas.getByRole("button", { name: "Show every review in this group" }),
		).toBeVisible();
		await waitFor(async () => expect(onLoadMore).toHaveBeenCalledOnce());
		await expect(canvas.getByRole("button", { name: "View earlier reviews" })).toBeVisible();
	},
};

/** With no result loaded yet, the placeholders of the earlier reviews stand at full width. */
export const NarrowedToNothingLoadingMore: Story = {
	args: {
		feed: { ...readyFeed, hasMore: true, isLoadingMore: true },
		reviews: [],
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("The latest reviews have no observations here.")).toBeVisible();
		const list = canvas.getByRole("list", { name: "Reviews" });
		await expectListPlaceholders(list, { bars: 6 });
		const frame = list.parentElement;
		await expect(frame).not.toBeNull();
		await expect(list.getBoundingClientRect().width).toBe(frame?.getBoundingClientRect().width);
	},
};

const retryRefresh = fn();

export const RefreshFailed: Story = {
	args: {
		feed: {
			...readyFeed,
			refreshFailure: { error: { status: 503 }, pending: false, onRetry: retryRefresh },
		},
	},
	play: async ({ canvas, userEvent }) => {
		await expect(canvas.getByText("We could not refresh reviews")).toBeVisible();
		await expect(canvas.getByRole("list", { name: "Reviews" })).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		await expect(retryRefresh).toHaveBeenCalledOnce();
	},
};

export const RefreshFailedWhileEmpty: Story = {
	args: {
		feed: {
			...readyFeed,
			reviews: [],
			refreshFailure: { error: { status: 503 }, pending: false, onRetry: fn() },
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText("We could not refresh reviews")).toBeVisible();
		await expect(canvas.getByText("No reviews yet")).toBeVisible();
		await expect(canvas.getByRole("button", { name: "Retry" })).toBeEnabled();
	},
};

export const Failed: Story = {
	args: { feed: { status: "error", error: new Error("network"), onRetry: fn() } },
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.getByText("We could not load reviews")).toBeVisible();
		await userEvent.click(canvas.getByRole("button", { name: "Retry" }));
		if (args.feed.status !== "error") {
			throw new Error("The story's feed is the failed one.");
		}
		await expect(args.feed.onRetry).toHaveBeenCalledOnce();
	},
};
