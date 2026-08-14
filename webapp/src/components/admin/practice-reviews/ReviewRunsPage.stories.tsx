import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { StatefulPatch } from "@/stories/stateful";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewRunsPage } from "./ReviewRunsPage";
import { reviewHandlers } from "./story-mock-server";

const meta = {
	title: "Workspace admin/Practice reviews/Reviews",
	component: ReviewRunsPage,
	parameters: {
		layout: "fullscreen",
		msw: { handlers: reviewHandlers() },
		chromatic: { viewports: [320, 768, 1440] },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: {},
		onSearchChange: fn(),
	},
	/** See `ObservationsListPage.stories`: a controlled screen needs somewhere to put its answer. */
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onSearchChange) => (
				<ReviewRunsPage {...args} search={search} onSearchChange={onSearchChange} />
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ReviewRunsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Seven reviews across four kinds of work, at three different points in their life.
 *
 * <p>Each row is named after the work rather than after the review, because a review has no name an
 * operator knows — it has a UUID. What it produced sits underneath as two numeric strips, and only
 * when there is something true to count: a running review says results are still coming, and one
 * that stopped early says it produced nothing rather than drawing nine zeroes.
 *
 * <p>Five of the seven finished with output, which is what an ordinary week looks like. A set that
 * was all failures and empties — which is what this screen used to show — teaches the reader that
 * the product mostly does not work.
 */
export const Default: Story = {
	parameters: { viewport: { defaultViewport: "desktop" } },
	play: async ({ canvas }) => {
		const list = await canvas.findByRole("list", { name: /Practice reviews/ });
		within(list).getByRole("link", {
			name: "Cache the workspace member lookup on the review path",
		});
		within(list).getByRole("link", { name: "How should we roll back the pricing migration?" });
		within(list).getByRole("link", { name: "Runbook: restoring a workspace from backup" });
		canvas.getByText("Results appear as it finishes.");
		canvas.getByText("It produced nothing before it stopped.");
	},
};

/**
 * The nine numbers, every one of them drawn.
 *
 * <p>This row used to read "1 strength · 2 improvements" — a sentence with the zeroes dropped, so
 * the second number started at a different x on every row, "no shortfalls" looked the same as "we do
 * not show shortfalls here", and the whole line reflowed under the reader every five seconds while
 * the poll refreshed an active review. Each strip now draws all of its slots in a fixed grid, and
 * the zeroes are the point: this review withheld two pieces of feedback and failed to deliver none,
 * which the sentence could not say.
 */
export const WhatEachReviewProduced: Story = {
	parameters: { viewport: { defaultViewport: "desktop" }, chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("list", { name: /Practice reviews/ });
		// The first review with any output at all is the webhook-retry one; the review above it is
		// still running and so has no tally to show.
		const observations = canvas.getAllByRole("list", { name: "Observations" })[0];
		const feedback = canvas.getAllByRole("list", { name: "Feedback" })[0];

		// Asserted on the strip rather than per cell: a count and its word are two elements, which is
		// what lets the number carry its own weight and the word stay quiet.
		for (const pair of [
			"1 strength",
			"2 improvements",
			"0 not applicable",
			"0 could not be determined",
		]) {
			await expect(observations).toHaveTextContent(pair);
		}
		for (const pair of [
			"1 delivered",
			"2 withheld",
			"0 failed to deliver",
			"0 queued for conversation",
		]) {
			await expect(feedback).toHaveTextContent(pair);
		}

		// Every row that has a tally draws the same nine slots, which is what puts each number at one
		// x down the list.
		for (const strip of canvas.getAllByRole("list", { name: "Observations" })) {
			await expect(within(strip).getAllByRole("listitem")).toHaveLength(4);
		}
		for (const strip of canvas.getAllByRole("list", { name: "Feedback" })) {
			await expect(within(strip).getAllByRole("listitem")).toHaveLength(5);
		}
	},
};

/**
 * The status filter, drawn as the tags the rows wear.
 *
 * It used to be plain grey text beside a list of coloured tags, so choosing a filter meant matching a
 * word to a tag from memory. Trigger and list now render the same `StatusBadge` the row does.
 */
export const StatusFilter: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByRole("list", { name: /Practice reviews/ });
		await userEvent.click(canvas.getByRole("combobox"));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(within(listbox).getByRole("option", { name: /Failed/ }));
		await expect(canvas.getByRole("combobox")).toHaveTextContent("Failed");
		// And the list narrows to the one review that failed, rather than staying as it was.
		await canvas.findByText("1 review matches your filters.");

		// Narrow to a status nothing holds, and the empty state offers the way back rather than
		// advising "try another status" with nothing to press.
		await userEvent.click(canvas.getByRole("combobox"));
		await userEvent.click(
			within(await screen.findByRole("listbox")).getByRole("option", { name: /Cancelled/ }),
		);
		await canvas.findByText("No reviews found");
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await canvas.findByText("7 reviews.");
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { viewports: [320, 768] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement);
		const list = await canvas.findByRole("list", { name: /Practice reviews/ });
		await within(list).findByRole("link", { name: /Retry webhook deliveries with backoff/ });
		await expectNoPageOverflow();
	},
};

/** A workspace whose practices have never been triggered, which is what a new one looks like. */
export const NoReviewsYet: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews", () =>
					HttpResponse.json({
						content: [],
						page: { number: 0, size: 20, totalElements: 0, totalPages: 1 },
					}),
				),
				...reviewHandlers(),
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("No reviews found");
		// Nothing is filtered, so there is nothing to clear — the empty state says why the list is
		// empty instead of offering an action that would change nothing.
		await expect(
			canvas.queryByRole("button", { name: "Clear all filters" }),
		).not.toBeInTheDocument();
	},
};

export const LoadFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews",
					() => new HttpResponse(null, { status: 500 }),
				),
				...reviewHandlers(),
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load reviews");
	},
};
