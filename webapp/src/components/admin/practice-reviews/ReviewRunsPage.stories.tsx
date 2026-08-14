import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewRunsPage } from "./ReviewRunsPage";
import { StatefulPatch } from "@/stories/stateful";
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
 * operator knows — it has a UUID. What it produced sits underneath as two sentences, and only when
 * there is something true to say: a running review says results are still coming, and one that
 * stopped early says it produced nothing rather than showing four zeroes.
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
		canvas.getByText("1 strength · 3 improvements");
		canvas.getByText("1 strength · 1 could not be determined");
		canvas.getByText("Results appear as it finishes.");
		canvas.getByText("It produced nothing before it stopped.");
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
