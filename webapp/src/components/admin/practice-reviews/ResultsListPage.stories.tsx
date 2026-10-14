import type { Meta, StoryContext, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewResult } from "@/api/types.gen";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { loadedList, narrowedList } from "@/stories/paged-list";
import { expectNoPageOverflow } from "@/stories/reflow";
import { StatefulPatch } from "@/stories/stateful";

import {
	manyResults,
	practiceGroups,
	reviewResults,
	selects,
	workspaceMembers,
	workspacePractices,
} from "./fixtures";
import { groupFacetOptions, practiceFacetOptions } from "./ResultFilters";
import { ResultsListPage } from "./ResultsListPage";
import { type ResultsSearch, resultsQuery, REVIEW_PAGE_SIZE } from "./review-search";
import type { ReviewPeople } from "./ReviewPersonFacet";

const PEOPLE: ReviewPeople = {
	options: workspaceMembers.map(({ account }) => ({
		accountId: account.id,
		label: account.displayName ?? `#${account.id}`,
		secondary: account.identities[0]?.login ?? undefined,
	})),
	capped: false,
	isLoading: false,
	isError: false,
};
const GROUPS: FacetSource = {
	options: groupFacetOptions(practiceGroups),
	isLoading: false,
	isError: false,
};
const PRACTICES: FacetSource = {
	options: practiceFacetOptions(workspacePractices, practiceGroups),
	isLoading: false,
	isError: false,
};

const loadMoreResults = fn();

/**
 * Every row a story has to choose from. It travels in the `results` arg because that is the
 * prop the screen reads, and {@link resultPage} narrows it to its first page before the screen
 * sees it — so an arg set in Controls is a pool to filter, not a page already cut.
 */
const pool = loadedList<ReviewResult>;

/**
 * The route fetches; this screen only draws what it is handed. To keep the facets live in a story
 * without a network, the filtering the server does is applied here — over the query object the route
 * would actually send, so a facet that stopped reaching the request would stop working here too.
 * What the *endpoint* names those parameters is a separate contract, pinned by the route test. A
 * row does not say which group its practice is in, so the Group facet narrows nothing here.
 */
const SEVERITY_RANK: Record<string, number> = { CRITICAL: 0, MAJOR: 1, MINOR: 2, TRIVIAL: 3 };

/** The server's "Most actionable first" (`PracticeResultRepository.OPERATOR_ORDER`), as a sort key. */
function actionabilityKey(result: ReviewResult): number {
	if (result.outcome === "NOT_MET") {
		return SEVERITY_RANK[result.severity ?? ""] ?? 4;
	}
	return result.outcome === "MET" ? 10 : 20;
}

/** The rows in the order the server would send them for `search`. */
function inServerOrder(
	list: PagedListState<ReviewResult>,
	search: ResultsSearch,
): PagedListState<ReviewResult> {
	if (list.status !== "ready" || search.order !== "ACTIONABILITY") {
		return list;
	}
	const rows = [...list.rows].sort(
		(a, b) =>
			actionabilityKey(a) - actionabilityKey(b) || b.recordedAt.getTime() - a.recordedAt.getTime(),
	);
	return { ...list, rows };
}

function resultPage(
	list: PagedListState<ReviewResult>,
	search: ResultsSearch,
): PagedListState<ReviewResult> {
	const query = resultsQuery(search, REVIEW_PAGE_SIZE);
	return narrowedList(
		inServerOrder(list, search),
		(row) =>
			(!query.from || row.recordedAt >= new Date(query.from)) &&
			(!query.to || row.recordedAt < new Date(query.to)) &&
			selects(search.practiceSlug, row.practiceSlug) &&
			selects(query.severity, row.severity) &&
			selects(query.outcome, row.outcome) &&
			(query.reviewId === undefined || row.reviewId === query.reviewId) &&
			(search.developerId === undefined || row.developer?.accountId === search.developerId),
		REVIEW_PAGE_SIZE,
	);
}

const meta = {
	component: ResultsListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		search: {
			outcome: undefined,
			severity: undefined,
		},
		onSearchChange: fn(),
		results: pool(reviewResults),
		groups: GROUPS,
		practices: PRACTICES,
		// The facet needs a label per slug; the hover card on a row's practice name needs the record.
		people: PEOPLE,
	},
	// The screen is controlled: with a frozen `search` prop every facet reads as dead, because
	// `selected` comes back through the same prop the choice was reported on. The rows are recomputed
	// from that search the way the route's query would be re-run.
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onSearchChange) => (
				<ResultsListPage
					{...args}
					search={search}
					onSearchChange={(patch) => {
						args.onSearchChange(patch);
						onSearchChange(patch);
					}}
					results={resultPage(args.results, search)}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ResultsListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * These facets are multi-select, so the popup stays open after a choice; leaving it open would put a
 * second listbox on screen for the next facet and make a bare `findByRole("listbox")` throw. The
 * trigger is captured before the click because choosing an option renames it.
 */
async function pickFacet(
	canvas: StoryContext["canvas"],
	userEvent: { click: (element: Element) => Promise<void> },
	facet: string,
	option: RegExp,
) {
	const trigger = canvas.getByRole("combobox", { name: facet });
	await userEvent.click(trigger);
	const listbox = await screen.findByRole("listbox", { name: `${facet} options` });
	await userEvent.click(await within(listbox).findByRole("option", { name: option }));
	await userEvent.click(trigger);
}

export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText("13 results.");
		// Every facet sits in the row, with no toggle to open first.
		for (const name of [
			"Group",
			"Practice",
			"Outcome",
			"Severity",
			"Developer response",
			"Origin",
		]) {
			canvas.getByRole("combobox", { name });
		}
		await expect(canvas.queryByRole("button", { name: /More filters/u })).toBeNull();
		canvas.getByRole("combobox", { name: "Developer" });
		canvas.getByRole("button", { name: "Recorded" });
		// A pull request, a chat thread and a document all reach this list and have to read as one
		// language, so one row of each work kind is asserted rather than any one row.
		canvas.getByText("The controller delegates before it does anything else");
		canvas.getByText("The thread ends without naming what was chosen");
		canvas.getByText("The runbook opens with the one step that cannot be undone");
	},
};

export const FilterToOneSeverity: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("13 results.");
		await pickFacet(canvas, userEvent, "Severity", /Major/u);
		await canvas.findByText("2 results match your filters.");
		await expect(canvas.queryByText(/leaks the ledger’s table name/u)).not.toBeInTheDocument();
	},
};

export const FilterToOnePerson: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("13 results.");
		await userEvent.click(canvas.getByRole("combobox", { name: "Developer" }));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(await within(listbox).findByRole("option", { name: /Grace Hopper/u }));
		await canvas.findByRole("combobox", { name: "Developer: Grace Hopper" });
		await canvas.findByText("2 results match your filters.");
	},
};

/**
 * A result selected elsewhere narrows this list to the review that recorded it, the
 * result among its siblings. The selection is set where a chain starts — the Reviews and Work
 * lists, or a level — so no row here offers a "Select".
 */
export const NarrowedToTheSelectedResult: Story = {
	args: {
		search: {
			outcome: undefined,
			severity: undefined,
			reviewId: "11111111-1111-1111-1111-111111111111",
			resultId: "66666666-6666-6666-6666-666666666666",
		},
	},
	parameters: {
		router: {
			initialUrl:
				"/?reviewId=11111111-1111-1111-1111-111111111111&resultId=66666666-6666-6666-6666-666666666666",
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("button", { name: /^Select/u })).not.toBeInTheDocument();
		await expect(
			within(canvas.getByRole("table", { name: "Results" }))
				.getAllByRole("row")
				.filter((row) => within(row).queryAllByRole("cell").length > 0),
		).toHaveLength(4);
	},
};

export const FilteredToNothing: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("13 results.");
		await pickFacet(canvas, userEvent, "Severity", /Critical/u);
		await pickFacet(canvas, userEvent, "Group", /Testing/u);
		await canvas.findByText("No results match these filters");
		canvas.getByRole("button", { name: "Reset" });
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await canvas.findByText("13 results.");
	},
};

/**
 * `ACTIONABILITY` is the server's ordering, not one the browser applies: not met results
 * worst-first, then met results, then the results that judged nothing. The control's only
 * job is to put the choice in the search the route turns into a request — that the endpoint spells it
 * `sort` is pinned by `-lists-route.test.tsx`, which is the only place the wire name can be checked.
 */
export const SortByActionability: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ args, canvas, userEvent }) => {
		await canvas.findByText("13 results.");
		await userEvent.click(canvas.getByRole("combobox", { name: /Sort/u }));
		await userEvent.click(await screen.findByRole("option", { name: "Most actionable first" }));
		await expect(args.onSearchChange).toHaveBeenLastCalledWith(
			expect.objectContaining({ order: "ACTIONABILITY" }),
		);
		// Out of time order a day would head the rows again each time it came back, so the table keeps
		// its headed bodies, one per rank of the order, and each row names its own day.
		const table = canvas.getByRole("table", { name: "Results" });
		await waitFor(async () =>
			expect(
				[...table.querySelectorAll("th[scope=rowgroup]")].map((heading) => heading.textContent),
			).toContain("Met"),
		);
		const headings = [...table.querySelectorAll("th[scope=rowgroup]")].map(
			(heading) => heading.textContent,
		);
		// In the server's order: not met worst first, then met, then the rest.
		await expect(headings.slice(0, 5)).toStrictEqual([
			"Not met · Critical",
			"Not met · Major",
			"Not met · Minor",
			"Not met · Trivial",
			"Met",
		]);
		await expect(new Set(headings).size).toBe(headings.length);
		await expect(table.querySelectorAll("tbody")).toHaveLength(headings.length);
	},
};

/**
 * `FacetMultiSelect` collapses its value chips below `sm` into a bare count, so at this width the
 * applied values only reach the reader through a separate pill row.
 */
export const MobileAppliedFilters: Story = {
	parameters: {
		chromatic: { viewports: [320] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("13 results.");
		await pickFacet(canvas, userEvent, "Severity", /Major/u);
		await canvas.findByText("2 results match your filters.");
		// Queried by label rather than by role because whether the `sm:` breakpoint is live depends on
		// how the runner applies the viewport, while what the pill says and does must hold either way.
		await canvas.findByTitle("Severity: Major");
		await userEvent.click(canvas.getByLabelText("Clear severity filter (Major)"));
		await canvas.findByText("13 results.");
		await expectNoPageOverflow();
	},
};

export const PickADateRange: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("13 results.");
		await userEvent.click(canvas.getByRole("button", { name: "Recorded" }));
		const dialog = await screen.findByRole("dialog");
		// The month is the only `role="grid"` on screen and the day buttons are the only buttons
		// inside it. Testing Library maps a `<td>` to `cell`, not `gridcell`, whatever role its
		// ancestor table carries, so the grid is the anchor rather than the cells.
		const grid = await within(dialog).findByRole("grid");
		const days = within(grid).getAllByRole("button");
		const [rangeStart, rangeEnd] = [days[4], days[10]];
		if (!rangeStart || !rangeEnd) {
			throw new Error("The month grid rendered too few days");
		}
		await userEvent.click(rangeStart);
		await userEvent.click(rangeEnd);
		await canvas.findByText(/–/u);
	},
};

export const SeverityFacetOpen: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Severity" }));
		const listbox = await screen.findByRole("listbox");
		await within(listbox).findByRole("option", { name: /Critical/u });
		within(listbox).getByRole("option", { name: /Trivial/u });
	},
};

/**
 * More results than the first page: the count is the server's, and the next page is asked for
 * when the end of the list scrolls into view.
 */
export const LoadsMoreAtTheEnd: Story = {
	args: { results: pool(manyResults(64), { onLoadMore: loadMoreResults }) },
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas }) => {
		await canvas.findByText("64 results.");
		const more = canvas.getByRole("button", { name: "Show more results" });
		more.scrollIntoView();
		await waitFor(async () => expect(loadMoreResults).toHaveBeenCalled());
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvas }) => {
		await canvas.findByText("13 results.");
		await expectNoPageOverflow();
	},
};

/**
 * The error arrives as a prop, so nothing here depends on a request failing at the right moment. A
 * status-less error is the one that reads "check your connection" — see `QueryErrorAlert`.
 */
export const LoadFailed: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	args: {
		results: { status: "error", error: { status: 500 }, onRetry: fn() },
	},
	play: async ({ canvas }) => {
		await canvas.findByText("We could not load results");
	},
};

export const Loading: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	args: { results: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(await canvas.findByRole("table", { name: "Results" })).toHaveAttribute(
			"aria-busy",
			"true",
		);
	},
};

/** Nothing has been reviewed yet, which is not the same as a filter that matched nothing. */
export const NoResultsYet: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	args: { results: pool([]) },
	play: async ({ canvas }) => {
		await canvas.findByText("No results yet");
		await expect(
			canvas.getByText("Results appear after a practice review completes."),
		).toBeVisible();
	},
};
