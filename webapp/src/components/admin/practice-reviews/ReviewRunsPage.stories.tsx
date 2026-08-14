import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, within } from "storybook/test";
import type { ReviewRunSummary } from "@/api/types.gen";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { ReviewRunsPage } from "./ReviewRunsPage";
import { reviewRuns } from "./story-mock-data";
import { StatefulSearch } from "./story-search-harness";

const reviewsHandler = (content: ReviewRunSummary[] = reviewRuns) =>
	http.get("*/workspaces/:workspaceSlug/practices/reviews", () =>
		HttpResponse.json({
			content,
			page: { number: 0, size: 20, totalElements: content.length, totalPages: 1 },
		}),
	);

const meta = {
	title: "Workspace admin/Practice reviews/Reviews",
	component: ReviewRunsPage,
	parameters: {
		layout: "fullscreen",
		msw: { handlers: [reviewsHandler()] },
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
		<StatefulSearch initial={args.search}>
			{(search, onSearchChange) => (
				<ReviewRunsPage {...args} search={search} onSearchChange={onSearchChange} />
			)}
		</StatefulSearch>
	),
} satisfies Meta<typeof ReviewRunsPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Four reviews across four kinds of work, at four different points in their life.
 *
 * <p>Each row is named after the work rather than after the review, because a review has no name an
 * operator knows — it has a UUID. What it produced sits underneath as two sentences, and only when
 * there is something true to say: a running review says results are still coming, and one that
 * stopped early says it produced nothing rather than showing four zeroes.
 */
export const Default: Story = {
	parameters: { viewport: { defaultViewport: "desktop" } },
	play: async ({ canvas }) => {
		const list = await canvas.findByRole("list", { name: /Practice reviews/ });
		for (const review of reviewRuns) {
			within(list).getByRole("link", { name: review.target.title });
		}
		canvas.getByText("2 strengths · 1 improvement · 1 not applicable · 1 could not be determined");
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
		await within(list).findByRole("link", { name: reviewRuns[0].target.title });
		await expectNoPageOverflow();
	},
};

export const Empty: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: [reviewsHandler([])] },
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
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load reviews");
	},
};
