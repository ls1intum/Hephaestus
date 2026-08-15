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
	// The screen is controlled: with a frozen `search` prop every facet reads as dead, because
	// `selected` comes back through the same prop the choice was reported on.
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
 * These facets are multi-select, so the popup stays open after a choice; leaving it open would put a
 * second listbox on screen for the next facet and make a bare `findByRole("listbox")` throw. The
 * trigger is captured before the click because choosing an option renames it.
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

export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText("12 observations.");
		for (const name of ["Area", "Practice", "Result", "Severity", "Practice status"]) {
			canvas.getByRole("combobox", { name });
		}
		canvas.getByRole("combobox", { name: "Developer" });
		canvas.getByRole("button", { name: "Observed" });
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
		await canvas.findByText("12 observations.");
		await pickFacet(canvas, userEvent, "Severity", /Major/);
		await canvas.findByText("2 observations match your filters.");
		await expect(canvas.queryByText(/leaks the ledger's table name/)).not.toBeInTheDocument();
	},
};

export const FilterToOnePerson: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 observations.");
		await userEvent.click(canvas.getByRole("combobox", { name: "Developer" }));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(await within(listbox).findByRole("option", { name: /Grace Hopper/ }));
		await canvas.findByRole("combobox", { name: "Developer: Grace Hopper" });
		await canvas.findByText("2 observations match your filters.");
	},
};

export const FilteredToNothing: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await canvas.findByText("12 observations.");
		await pickFacet(canvas, userEvent, "Severity", /Critical/);
		await pickFacet(canvas, userEvent, "Area", /Testing/);
		await canvas.findByText("No observations match these filters");
		canvas.getByRole("button", { name: "Reset" });
		await userEvent.click(canvas.getByRole("button", { name: "Clear all filters" }));
		await canvas.findByText("12 observations.");
	},
};

/**
 * `sort=ACTIONABILITY` is the server's ordering, not one the browser applies: shortfalls worst-first,
 * then strengths, then the observations that judged nothing. The mock has to answer it the same way
 * or this story proves nothing.
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

		const rows = await within(
			await canvas.findByRole("list", { name: "Observations" }),
		).findAllByRole("listitem");
		await expect(rows[0]).toHaveTextContent("Invoice numbering leaks the ledger's table name");
		await expect(rows[0]).toHaveTextContent("Critical");
		const titles = rows.map((row) => row.textContent ?? "");
		const problems = titles.flatMap((text, index) =>
			text.includes("Needs improvement") ? [index] : [],
		);
		const strengths = titles.flatMap((text, index) => (text.includes("Strength") ? [index] : []));
		expect(Math.min(...strengths)).toBeGreaterThan(Math.max(...problems));
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
	play: async ({ canvasElement, userEvent }) => {
		const canvas = within(canvasElement);
		await canvas.findByText("12 observations.");
		await pickFacet(canvas, userEvent, "Severity", /Major/);
		await canvas.findByText("2 observations match your filters.");
		// Queried by label rather than by role because whether the `sm:` breakpoint is live depends on
		// how the runner applies the viewport, while what the pill says and does must hold either way.
		await canvas.findByTitle("Severity: Major");
		await userEvent.click(canvas.getByLabelText("Clear severity filter (Major)"));
		await canvas.findByText("12 observations.");
		await expectNoPageOverflow();
	},
};

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

export const SeverityFacetOpen: Story = {
	parameters: { chromatic: { viewports: [1440] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Severity" }));
		const listbox = await screen.findByRole("listbox");
		await within(listbox).findByRole("option", { name: /Critical/ });
		within(listbox).getByRole("option", { name: /Informational/ });
	},
};

export const MoreThanOnePage: Story = {
	// Pagination is real links so a page can be bookmarked, which means the page travels through the
	// router rather than `onSearchChange`. Storybook mounts this screen under a single bare route, so
	// a click would go nowhere and the page has to be set in `args`.
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
 * The members endpoint takes `page` and `size` and no name filter, so this control fetches one page
 * and matches within it. Past that page the search box would otherwise answer "No matches", which
 * reads as "that person is not in this workspace"; lifting the cap needs a server-side name query.
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
