import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewFeedback } from "@/api/types.gen";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { loadedList, narrowedList } from "@/stories/paged-list";
import { expectNoPageOverflow } from "@/stories/reflow";
import { StatefulPatch } from "@/stories/stateful";

import { FeedbackListPage } from "./FeedbackListPage";
import {
	locationCounts,
	manyFeedback,
	practiceGroups,
	reviewFeedback,
	selects,
	workspaceMembers,
	workspacePractices,
} from "./fixtures";
import { practiceFacetOptions } from "./ResultFilters";
import { type FeedbackSearch, feedbackQuery, REVIEW_PAGE_SIZE } from "./review-search";
import type { ReviewPeople } from "./ReviewPersonFacet";
import { STORY_GROUPS } from "./story-scope-sources";

const GRACE_ID = workspaceMembers.find((member) => member.userLogin === "grace")?.userId ?? 0;

const PEOPLE: ReviewPeople = {
	options: workspaceMembers.map((member) => ({
		userId: member.userId,
		label: member.userName ?? `#${member.userId}`,
		secondary: member.userLogin,
	})),
	capped: false,
	isLoading: false,
	isError: false,
};
const PRACTICES: FacetSource = {
	options: practiceFacetOptions(workspacePractices, practiceGroups),
	isLoading: false,
	isError: false,
};

const loadMoreFeedback = fn();

/**
 * Every row a story has to choose from. It travels in the `feedback` arg because that is the prop
 * the screen reads, and {@link feedbackPage} narrows it to its first page before the screen sees it —
 * so an arg set in Controls is a pool to filter, not a page already cut.
 */
const pool = loadedList<ReviewFeedback>;

/**
 * The route fetches; this screen only draws what it is handed. To keep the facets live in a story
 * without a network, the filtering the server does is applied here — over the query object the route
 * would actually send. That is what keeps the "Why withheld" facet honest: the URL carries families
 * and `feedbackQuery` expands them to the individual reasons rows actually carry, so a story that
 * filtered on the family name would pass while the real request returned nothing.
 *
 * A row does not say which practices it belongs to, so the Practice facet narrows nothing here; that
 * it reaches the request is pinned by `-lists-route.test.tsx`.
 */
function feedbackPage(
	list: PagedListState<ReviewFeedback>,
	search: FeedbackSearch,
): PagedListState<ReviewFeedback> {
	const query = feedbackQuery(search, REVIEW_PAGE_SIZE);
	return narrowedList(
		list,
		(row) =>
			(!query.from || row.createdAt >= new Date(query.from)) &&
			(!query.to || row.createdAt < new Date(query.to)) &&
			selects(query.deliveryState, row.deliveryState) &&
			selects(query.location, row.location) &&
			selects(query.suppressionReason, row.suppressionReason) &&
			(query.recipientUserId === undefined || row.recipient?.id === query.recipientUserId),
		query.size,
	);
}

const meta = {
	component: FeedbackListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: { deliveryState: undefined, withheldFamily: undefined, location: undefined },
		onSearchChange: fn(),
		feedback: pool(reviewFeedback),
		groups: STORY_GROUPS,
		practices: PRACTICES,
		people: PEOPLE,
		// Recomputed in `render` from the pool and the recipient filtered to, as the route's query is.
		recipientCounts: { status: "ready", counts: [] },
	},
	// The screen is controlled: with a frozen `search` prop every facet reads as dead. The rows are
	// recomputed from that search the way the route's query would be re-run.
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onSearchChange) => (
				<FeedbackListPage
					{...args}
					search={search}
					onSearchChange={(patch) => {
						args.onSearchChange(patch);
						onSearchChange(patch);
					}}
					feedback={feedbackPage(args.feedback, search)}
					recipientCounts={
						args.recipientCounts.status === "ready"
							? {
									status: "ready",
									counts: locationCounts(
										(args.feedback.status === "ready" ? args.feedback.rows : []).filter(
											(row) => row.recipient?.id === search.recipientUserId,
										),
									),
								}
							: args.recipientCounts
					}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof FeedbackListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** The names of a group's comboboxes, in order. */
function comboboxes(group: HTMLElement) {
	return within(group)
		.getAllByRole("combobox")
		.map((box) => box.getAttribute("aria-label"));
}

/**
 * The withheld rows are what this set exists for: each carries its own precise sentence, and no two
 * of them say the same thing, which is what keeps the reason from reading as decoration.
 */
export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText("12 pieces of feedback.");
		// The same order as on every tab: the scope, which applies to every tab, then this list's own
		// facets and its sort, all in one row.
		const scope = canvas.getByRole("group", { name: "Applies to every tab" });
		await expect(comboboxes(scope)).toStrictEqual(["Developer", "Group", "Practice"]);
		within(scope).getByRole("button", { name: "Composed" });
		const filters = canvas.getByRole("group", { name: "Filters" });
		await expect(comboboxes(filters).slice(0, 3)).toStrictEqual([
			"Outcome",
			"Channel",
			"Why withheld",
		]);
		within(filters).getByRole("combobox", { name: /Sort/u });
		await expect(canvas.queryByRole("button", { name: /More filters/u })).toBeNull();
		canvas.getByTitle("Nearly the same as other feedback from the same review.");
		canvas.getByTitle("Found while reviewing past work, which is measured but never sent.");
		canvas.getByTitle("The developer has opted out of AI feedback.");
		canvas.getByTitle("The work was already merged, so a note on it would arrive too late.");
	},
};

/**
 * The endpoint sends a fixed-character cut of the stored Markdown, which on a real note ends inside
 * a fenced code block. The row previews no text at all, so none of it reaches the list: the note is
 * read on its sheet.
 */
export const LongFeedbackReadsAsProse: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findAllByRole("link", { name: /^Open feedback /u });
		await expect(canvas.queryByText(/2 issues to tighten in this change/u)).not.toBeInTheDocument();
		await expect(canvas.queryByText(/```java/u)).not.toBeInTheDocument();
	},
};

export const FilterToOneRecipient: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 pieces of feedback.");
		await userEvent.click(canvas.getByRole("combobox", { name: "Developer" }));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(
			await within(listbox).findByRole("option", { name: /Katherine Johnson/u }),
		);
		await canvas.findByRole("combobox", { name: "Developer: Katherine Johnson" });
		await canvas.findByText("3 pieces of feedback match your filters.");
		// The person filtered to leads the list with everything addressed to them.
		await canvas.findByRole("heading", { name: "Feedback for Katherine Johnson" });
	},
};

/**
 * A recipient in a row opens the list filtered to them, which leads with their summary: every
 * piece addressed to them by delivery and by channel, each count a link to exactly those rows.
 */
export const RecipientSummary: Story = {
	args: {
		search: {
			deliveryState: undefined,
			withheldFamily: undefined,
			location: undefined,
			recipientUserId: GRACE_ID,
		},
	},
	parameters: { chromatic: { viewports: [320, 1440] } },
	play: async ({ canvas }) => {
		await canvas.findByRole("heading", { name: "Feedback for Grace Hopper" });
		// The summary's table comes first; the list's own table is named.
		const table = canvas.getAllByRole("table").find((each) => !each.hasAttribute("aria-label"));
		if (table === undefined) {
			throw new Error("The recipient summary has no table");
		}
		await expect(within(table).getByRole("cell", { name: "With Heph in Slack" })).toBeVisible();
		await expect(within(table).getByRole("cell", { name: "In conversation" })).toBeVisible();
		// The legend is text; the table's counts open the rows they count.
		const legend = canvas.getByRole("list", { name: "Feedback by delivery" });
		within(legend).getByText("prepared", { exact: false });
		await expect(within(legend).queryByRole("link")).not.toBeInTheDocument();
		const [count] = within(table).getAllByRole("link");
		await expect(count).toHaveAttribute(
			"href",
			expect.stringContaining(`recipientUserId=${GRACE_ID}`),
		);
	},
};

/** The summary waits in its shape, and says so when the counts fail, without hiding the rows. */
export const RecipientSummaryFailed: Story = {
	args: {
		search: {
			deliveryState: undefined,
			withheldFamily: undefined,
			location: undefined,
			recipientUserId: GRACE_ID,
		},
		recipientCounts: { status: "error", error: { status: 500 }, onRetry: fn() },
	},
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("We could not count the feedback for Grace Hopper");
		await canvas.findByText(/pieces? of feedback match/u);
	},
};

/**
 * Every row says where its feedback went, by provider, in a short label beside the provider's mark:
 * on GitHub or GitLab, with Heph in Slack, or in the next conversation.
 */
export const EveryRowNamesItsLocation: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("12 pieces of feedback.");
		const table = within(canvas.getByRole("table", { name: "Feedback" }));
		await expect(table.getAllByText("On GitHub").length).toBeGreaterThan(0);
		await expect(table.getAllByText("On GitLab").length).toBeGreaterThan(0);
		await expect(table.getAllByText("With Heph in Slack").length).toBeGreaterThan(0);
		await expect(table.getAllByText("In conversation").length).toBeGreaterThan(0);
	},
};

/** The channel facet offers each channel split by provider, and filters to exactly that provider. */
export const FilterToOneProvider: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 pieces of feedback.");
		const trigger = canvas.getByRole("combobox", { name: "Channel" });
		await userEvent.click(trigger);
		const listbox = await screen.findByRole("listbox", { name: "Channel options" });
		for (const name of ["On GitHub", "On GitLab", "With Heph in the app", "With Heph in Slack"]) {
			within(listbox).getByRole("option", { name: new RegExp(name, "u") });
		}
		await userEvent.click(within(listbox).getByRole("option", { name: /On GitLab/u }));
		await userEvent.click(trigger);
		await canvas.findByText(/matches? your filters\./u);
		const table = within(canvas.getByRole("table", { name: "Feedback" }));
		await expect(table.queryByText("On GitHub")).not.toBeInTheDocument();
		await expect(table.getAllByText("On GitLab").length).toBeGreaterThan(0);
	},
};

/**
 * The facet offers families rather than every withholding reason, which is what fits it on the
 * toolbar. A row still shows its own precise sentence; only the question is grouped.
 */
export const WhyWithheldFacetOpen: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Why withheld" }));
		const listbox = await screen.findByRole("listbox");
		await within(listbox).findByRole("option", { name: /The work moved on/u });
		within(listbox).getByRole("option", { name: /The developer’s choice/u });
	},
};

/** Filtering by a family returns exactly the rows whose own sentence sits under that heading. */
export const FilterToOneWithholdingFamily: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 pieces of feedback.");
		const trigger = canvas.getByRole("combobox", { name: "Why withheld" });
		await userEvent.click(trigger);
		const listbox = await screen.findByRole("listbox", { name: "Why withheld options" });
		await userEvent.click(await within(listbox).findByRole("option", { name: /Housekeeping/u }));
		// Closed again: these facets are multi-select and stay open after a choice, so the next facet
		// would otherwise put a second listbox on screen.
		await userEvent.click(trigger);
		await canvas.findByText("1 piece of feedback matches your filters.");
		canvas.getByTitle("Nearly the same as other feedback from the same review.");

		// A place nothing under that family went to, so the two filters intersect to nothing.
		await userEvent.click(canvas.getByRole("combobox", { name: "Channel" }));
		const places = await screen.findByRole("listbox", { name: "Channel options" });
		await userEvent.click(await within(places).findByRole("option", { name: /In conversation/u }));
		await canvas.findByText("No feedback matches these filters");
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await canvas.findByText("12 pieces of feedback.");
	},
};

/**
 * Composed order is the server's, like every other: the control only puts the choice in the search
 * the route turns into a request, and `-lists-route.test.tsx` pins that the endpoint spells it `sort`.
 */
export const SortOldestFirst: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ args, canvas, userEvent }) => {
		await canvas.findByText("12 pieces of feedback.");
		await userEvent.click(canvas.getByRole("combobox", { name: /Sort/u }));
		await userEvent.click(await screen.findByRole("option", { name: "Oldest first" }));
		await expect(args.onSearchChange).toHaveBeenLastCalledWith(
			expect.objectContaining({ order: "OLDEST" }),
		);
	},
};

/**
 * More feedback than the first page: the count is the server's, and the next page is asked for when
 * the end of the list scrolls into view.
 */
export const LoadsMoreAtTheEnd: Story = {
	args: { feedback: pool(manyFeedback(60), { onLoadMore: loadMoreFeedback }) },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("60 pieces of feedback.");
		const more = canvas.getByRole("button", { name: "Show more feedback" });
		more.scrollIntoView();
		await waitFor(async () => expect(loadMoreFeedback).toHaveBeenCalled());
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		await canvas.findByText("12 pieces of feedback.");
		await expectNoPageOverflow();
	},
};

/**
 * The error arrives as a prop, so nothing here depends on a request failing at the right moment. A
 * status-less error is the one that reads "check your connection" — see `QueryErrorAlert`.
 */
export const LoadFailed: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	args: { feedback: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async ({ canvas }) => {
		await canvas.findByText("We could not load feedback");
	},
};

export const Loading: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	args: { feedback: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(await canvas.findByRole("table", { name: "Feedback" })).toHaveAttribute(
			"aria-busy",
			"true",
		);
	},
};

/** Nothing has been composed yet, which is not the same as a filter that matched nothing. */
export const NoFeedbackYet: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	args: { feedback: pool([]) },
	play: async ({ canvas }) => {
		await canvas.findByText("No feedback yet");
	},
};
