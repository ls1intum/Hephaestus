import type { Meta, StoryObj } from "@storybook/react-vite";
import { HttpResponse, http } from "msw";
import { fn, screen, within } from "storybook/test";
import { withStandardPage, withWidePage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/test/reflow";
import { ObservationsListPage } from "./ObservationsListPage";
import {
	practiceAreas,
	reviewObservations,
	workspaceMembers,
	workspacePractices,
} from "./story-mock-data";
import { StatefulSearch } from "./story-search-harness";

const meta = {
	title: "Workspace admin/Practice reviews/Observations",
	component: ObservationsListPage,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 768, 1440] },
		msw: {
			handlers: [
				http.get("*/workspaces/:workspaceSlug/practices/reviews/observations", () =>
					HttpResponse.json({
						content: reviewObservations,
						page: {
							number: 0,
							size: 25,
							totalElements: reviewObservations.length,
							totalPages: 1,
						},
					}),
				),
				http.get("*/workspaces/:workspaceSlug/practice-areas", () =>
					HttpResponse.json(practiceAreas),
				),
				http.get("*/workspaces/:workspaceSlug/practices", () =>
					HttpResponse.json(workspacePractices),
				),
				http.get("*/workspaces/:workspaceSlug/members", () => HttpResponse.json(workspaceMembers)),
			],
		},
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
	 */
	render: (args) => (
		<StatefulSearch initial={args.search}>
			{(search, onSearchChange) => (
				<ObservationsListPage {...args} search={search} onSearchChange={onSearchChange} />
			)}
		</StatefulSearch>
	),
} satisfies Meta<typeof ObservationsListPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Every facet is on the toolbar. "More filters" is gone; a filter you must find is one you do not use. */
export const Default: Story = {
	play: async ({ canvas }) => {
		await canvas.findByText(`${reviewObservations.length} observations.`);
		for (const name of ["Area", "Practice", "Result", "Severity", "Practice status"]) {
			canvas.getByRole("combobox", { name });
		}
		canvas.getByRole("combobox", { name: "Developer" });
		canvas.getByRole("button", { name: "Date" });
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
		await canvas.findByText(`${reviewObservations.length} observations.`);
		await userEvent.click(canvas.getByRole("combobox", { name: "Developer" }));
		const listbox = await screen.findByRole("listbox");
		await userEvent.click(await within(listbox).findByRole("option", { name: /Grace Hopper/ }));
		// The choice sticks, which is the whole point of the harness.
		await canvas.findByRole("combobox", { name: "Developer: Grace Hopper" });
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
		await canvas.findByText(`${reviewObservations.length} observations.`);
		await userEvent.click(canvas.getByRole("button", { name: "Date" }));
		const dialog = await screen.findByRole("dialog");
		const days = await within(dialog).findAllByRole("gridcell", { selected: false });
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

export const Mobile: Story = {
	parameters: {
		chromatic: { disableSnapshot: true },
		viewport: { defaultViewport: "reflow" },
	},
	play: async ({ canvasElement }) => {
		await within(canvasElement).findByText(`${reviewObservations.length} observations.`);
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
			],
		},
	},
	play: async ({ canvas }) => {
		await canvas.findByText("Couldn't load observations");
	},
};
