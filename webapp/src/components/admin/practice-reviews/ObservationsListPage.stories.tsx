import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { expect, fn, screen, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { StatefulPatch } from "@/stories/stateful";
import { expectNoPageOverflow } from "@/test/reflow";
import { ObservationsListPage } from "./ObservationsListPage";
import { manyMembers, manyObservations } from "./story-mock-data";
import { reviewHandlers } from "./story-mock-server";

const meta = {
	title: "Workspace admin/Practice reviews/Observations",
	component: ObservationsListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
		msw: { handlers: reviewHandlers() },
	},
	decorators: [withWidePage, withStandardPage],
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		search: { presence: undefined, assessment: undefined, severity: undefined },
		onSearchChange: fn(),
	},
	/**
	 * The screen is a controlled component, so its filters only work if something holds the search it
	 * reports. Every story here goes through the harness — with a `fn()` in `onSearchChange` and a
	 * frozen `search`, choosing a severity left the facet unselected and clicking two days in the
	 * calendar highlighted neither, because `selected` comes back through the same dead prop.
	 *
	 * The mock endpoint behind it filters too, so a chosen facet visibly removes rows. A mock that
	 * returned the same array for every query made a working filter look broken all over again.
	 */
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onSearchChange) => (
				<ObservationsListPage {...args} search={search} onSearchChange={onSearchChange} />
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ObservationsListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/**
 * Open a facet, choose an option, and close it again.
 *
 * Closing matters: the popup stays open after a choice, because these facets are multi-select and
 * shutting it after every tick would make picking two severities take four clicks. A story that then
 * opens a second facet has two listboxes on screen, and a bare `findByRole("listbox")` throws. The
 * trigger element is captured before the click because choosing an option renames it.
 */
async function pickFacet(
	canvas: ReturnType<typeof within>,
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

/** Every facet is on the toolbar. "More filters" is gone; a filter you must find is one you do not use. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText("12 observations.");
		for (const name of ["Area", "Practice", "Result", "Severity", "Practice status"]) {
			canvas.getByRole("combobox", { name });
		}
		canvas.getByRole("combobox", { name: "Developer" });
		canvas.getByRole("button", { name: "Observed" });
		// Twelve observations over four kinds of work, not one shape repeated: a pull request, a merge
		// request, a chat thread and a document all reach this list and have to read as one language.
		canvas.getByText("The controller delegates before it does anything else");
		canvas.getByText("The thread ends without naming what was chosen");
		canvas.getByText("The runbook opens with the one step that cannot be undone");
	},
};

/**
 * Choosing a severity removes the rows that do not carry it.
 *
 * Both halves of the loop have to work for this to be true: the screen has to be able to record the
 * choice, and the endpoint has to answer the narrowed query. Each was broken on its own.
 */
export const FilterToOneSeverity: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 observations.");
		await pickFacet(canvas, userEvent, "Severity", /Major/);
		await canvas.findByText("2 observations match your filters.");
		await expect(canvas.queryByText(/leaks the ledger's table name/)).not.toBeInTheDocument();
	},
};

/**
 * The person filter, which the screen has always understood and never offered.
 *
 * `subjectUserId` was in the search schema, the query sent it, and an applied one rendered as a pill
 * you could clear — but the only way to set one was a link from another page. Anything that can show
 * up as an applied-filter pill has to be settable where the pill appears.
 */
export const FilterToOnePerson: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 observations.");
		await userEvent.click(canvas.getByRole("combobox", { name: "Developer" }));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(await within(listbox).findByRole("option", { name: /Grace Hopper/ }));
		// The choice sticks, and the list narrows to her two observations.
		await canvas.findByRole("combobox", { name: "Developer: Grace Hopper" });
		await canvas.findByText("2 observations match your filters.");
	},
};

/** Over-filtering reaches the empty state by a route an operator can retrace and undo. */
export const FilteredToNothing: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 observations.");
		await pickFacet(canvas, userEvent, "Severity", /Critical/);
		await pickFacet(canvas, userEvent, "Area", /Testing/);
		await canvas.findByText("No observations match these filters");
		canvas.getByRole("button", { name: "Reset" });
		// The empty state carries its own way out, where it used to advise removing a filter and give
		// the reader nothing to press.
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await canvas.findByText("12 observations.");
	},
};

/**
 * "Show me the worst thing first", answered.
 *
 * <p>The endpoint has ordered by actionability since it shipped and no screen ever asked for it, so
 * the twelve observations always arrived newest first and the critical one sat wherever its
 * timestamp put it — fifth. Choosing the ordering sends `sort=ACTIONABILITY`, and the mock orders
 * the way the server documents: shortfalls worst-first, then strengths, then the observations that
 * judged nothing.
 */
export const SortByActionability: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 observations.");
		const firstRowBefore = within(
			await canvas.findByRole("list", { name: "Observations" }),
		).getAllByRole("listitem")[0];
		await expect(firstRowBefore).toHaveTextContent("A dropped delivery is logged at debug");

		await userEvent.click(canvas.getByRole("combobox", { name: /Sort/ }));
		await userEvent.click(await screen.findByRole("option", { name: "Most actionable first" }));

		// The one critical shortfall in the fixture is now the row you land on.
		const rows = await within(
			await canvas.findByRole("list", { name: "Observations" }),
		).findAllByRole("listitem");
		await expect(rows[0]).toHaveTextContent("Invoice numbering leaks the ledger's table name");
		await expect(rows[0]).toHaveTextContent("Critical");
		// …and every strength has moved below every shortfall.
		const titles = rows.map((row) => row.textContent ?? "");
		const problems = titles.flatMap((text, index) =>
			text.includes("Needs improvement") ? [index] : [],
		);
		const strengths = titles.flatMap((text, index) => (text.includes("Strength") ? [index] : []));
		expect(Math.min(...strengths)).toBeGreaterThan(Math.max(...problems));
	},
};

/**
 * On a phone, what is applied is on screen and removable.
 *
 * `FacetMultiSelect` hides its value chips below `sm` and says "1 selected", so the toolbar knew how
 * many filters were on and never which. Baymard asks for the applied values as removable chips at
 * this width; these are the same pill the scope filters already use.
 */
export const MobileAppliedFilters: Story = {
	parameters: {
		chromatic: { viewports: [320] },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement, userEvent }) => {
		const canvas = within(canvasElement);
		await canvas.findByText("12 observations.");
		await pickFacet(canvas, userEvent, "Severity", /Major/);
		await canvas.findByText("2 observations match your filters.");
		// The value, not just the count — and its own X, which removes only it. Queried by label
		// rather than by role because whether the `sm:` breakpoint is live depends on how the runner
		// applies the viewport, while what the pill says and does must hold either way; the 320px
		// snapshot is what proves it is on screen.
		await canvas.findByTitle("Severity: Major");
		await userEvent.click(canvas.getByLabelText("Clear severity filter (Major)"));
		await canvas.findByText("12 observations.");
		await expectNoPageOverflow();
	},
};

/**
 * A date range can be picked, and the picked range shows.
 *
 * The product owner reported he could not select one here. The control was present and correctly
 * wired at both list screens; the story it lived in could not accept an answer.
 */
export const PickADateRange: Story = {
	parameters: { chromatic: { disableSnapshot: true } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 observations.");
		await userEvent.click(canvas.getByRole("button", { name: "Observed" }));
		const dialog = await screen.findByRole("dialog");
		// The month is the only `role="grid"` on screen and the day buttons are the only buttons
		// inside it. Testing Library maps a `<td>` to `cell`, not `gridcell`, whatever role its
		// ancestor table carries, so the grid is the anchor rather than the cells.
		const grid = await within(dialog).findByRole("grid");
		const days = within(grid).getAllByRole("button");
		await userEvent.click(days[4]);
		await userEvent.click(days[10]);
		await canvas.findByText(/–/);
	},
};

/**
 * The severity facet open, showing that an option carries the icon and tone of the badge it filters
 * for — the dropdown and the rows are recognisably about one thing.
 */
export const SeverityFacetOpen: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Severity" }));
		const listbox = await screen.findByRole("listbox");
		await within(listbox).findByRole("option", { name: /Critical/ });
		within(listbox).getByRole("option", { name: /Informational/ });
	},
};

/**
 * Sixty-four rows, opened at the middle page — the size at which this screen stops being a list you
 * can check by eye and starts being one you have to filter.
 *
 * <p>The story sets the page rather than clicking to it. Pagination here is real links, so that the
 * third page can be bookmarked and opened in a new tab, and the page number therefore travels
 * through the router rather than through `onSearchChange`. Storybook mounts these screens under a
 * single bare route, so there is nothing to navigate to and a click would go nowhere. This is a
 * property of the harness, not a dead control: the same links work in the app.
 */
export const MoreThanOnePage: Story = {
	args: { search: { page: 1, presence: undefined, assessment: undefined, severity: undefined } },
	parameters: {
		chromatic: { viewports: [1440] },
		msw: { handlers: reviewHandlers({ observations: manyObservations(64) }) },
	},
	play: async ({ canvas }) => {
		await canvas.findByText("64 observations.");
		const current = await canvas.findByRole("link", { name: "Go to page 2" });
		await expect(current).toHaveAttribute("aria-current", "page");
		canvas.getByRole("link", { name: "Go to previous page" });
		canvas.getByRole("link", { name: "Go to next page" });
	},
};

/**
 * A workspace with more members than the facet can list, saying so.
 *
 * <p>The members endpoint takes `page` and `size` and no name, so this control asks for the first
 * hundred and filters them in the browser. In a workspace of 140 the other forty were unselectable
 * and the search box answered "No matches" for them — the one wrong thing a filter can say, because
 * it reads as "that person is not in this workspace". The limit is now stated where the search that
 * hits it lives; lifting it needs a query parameter the server does not yet offer.
 */
export const MorePeopleThanTheFacetCanList: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json(manyMembers(100))),
				...reviewHandlers(),
			],
		},
	},
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 observations.");
		await userEvent.click(canvas.getByRole("combobox", { name: "Developer" }));
		await screen.findByRole("listbox");
		await screen.findByText(/Showing the first 100 members/);
	},
};

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		await within(canvasElement).findByText("12 observations.");
		await expectNoPageOverflow();
	},
};

export const LoadFailed: Story = {
	parameters: {
		chromatic: { viewports: [1440] },
		msw: {
			handlers: [
				http.get(
					"*/workspaces/:workspaceSlug/practices/reviews/observations",
					() => new HttpResponse(null, { status: 500 }),
				),
				...reviewHandlers(),
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load observations");
	},
};
