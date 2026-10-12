import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { withStandardPage } from "@/stories/decorators";
import { StatefulPatch } from "@/stories/stateful";

import { practiceGroups, workspaceMembers, workspacePractices } from "./fixtures";
import {
	clearedObservationFacets,
	groupFacetOptions,
	ObservationFilters,
	type PracticeFacetOption,
	practiceFacetOptions,
} from "./ObservationFilters";
import { clearedScope } from "./review-scope";
import type { ObservationsSearch } from "./review-search";
import type { ReviewPeople } from "./ReviewPersonFacet";

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
const GROUPS: FacetSource = {
	options: groupFacetOptions(practiceGroups),
	isLoading: false,
	isError: false,
};
const PRACTICES: FacetSource<PracticeFacetOption> = {
	options: practiceFacetOptions(workspacePractices, practiceGroups),
	isLoading: false,
	isError: false,
};

/** The facets a search always names, each unset. */
const UNFILTERED = {
	outcome: undefined,

	severity: undefined,
	origin: undefined,
} satisfies ObservationsSearch;

const meta = {
	component: ObservationFilters,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		search: UNFILTERED,
		onPatch: fn(),
		onReset: fn(),
		onClearScope: fn(),
		groups: GROUPS,
		practices: PRACTICES,
		people: PEOPLE,
		total: 12,
	},
	// Controlled: `selected` on every facet comes back through the same `search` the choice is
	// reported on, so a frozen value would leave the whole toolbar looking dead.
	render: (args) => (
		<StatefulPatch<ObservationsSearch> initial={args.search}>
			{(search, patch) => (
				<ObservationFilters
					{...args}
					search={search}
					onPatch={(next) => {
						patch(next);
						args.onPatch(next);
					}}
					onReset={() => {
						patch(clearedObservationFacets());
						args.onReset();
					}}
					onClearScope={() => {
						patch(clearedScope());
						args.onClearScope();
					}}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ObservationFilters>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing set: no Reset, and the count is the whole list rather than what survived a filter. */
export const Unfiltered: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("12 observations.");
		await expect(canvas.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
	},
};

export const FilteredCountReadsDifferently: Story = {
	args: {
		search: { ...UNFILTERED, severity: ["MAJOR"] },
		total: 2,
	},
	play: async ({ canvas }) => {
		canvas.getByText("2 observations match your filters.");
		canvas.getByRole("button", { name: "Reset" });
	},
};

/** Severity and origin sit in the row with the other facets, with no toggle to open first. */
export const ReportsAChosenSeverity: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.queryByRole("button", { name: /More filters/u })).toBeNull();
		await userEvent.click(canvas.getByRole("combobox", { name: "Severity" }));
		const listbox = await screen.findByRole("listbox", { name: "Severity options" });
		await userEvent.click(await within(listbox).findByRole("option", { name: /Major/u }));
		await expect(args.onPatch).toHaveBeenCalledWith({ severity: ["MAJOR"] });
	},
};

/**
 * Sorting does not narrow anything, which is why it sits with the count rather than among the facets
 * and why Reset leaves it alone. The choice still travels as a patch like any other.
 */
export const SortIsNotAFilter: Story = {
	args: {
		search: { ...UNFILTERED, severity: ["MAJOR"] },
		total: 2,
	},
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: /Sort/u }));
		await userEvent.click(await screen.findByRole("option", { name: "Most actionable first" }));
		await expect(args.onPatch).toHaveBeenCalledWith({ order: "ACTIONABILITY" });

		await userEvent.click(canvas.getByRole("button", { name: "Reset" }));
		await expect(canvas.getByRole("combobox", { name: /Sort/u })).toHaveTextContent(
			"Most actionable first",
		);
	},
};

/** The catalogue is still on its way, so the facet is disabled rather than offering nothing. */
export const WhileTheCatalogueLoads: Story = {
	args: {
		groups: { options: [], isLoading: true, isError: false },
		practices: { options: [], isLoading: true, isError: false },
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("combobox", { name: "Group" })).toBeDisabled();
		await expect(canvas.getByRole("combobox", { name: "Practice" })).toBeDisabled();
	},
};

export const TheCatalogueCouldNotBeLoaded: Story = {
	args: {
		groups: { options: [], isLoading: false, isError: true },
		practices: { options: [], isLoading: false, isError: true },
	},
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Group" }));
		await screen.findByText("We could not load groups");
	},
};

/** Below `sm` the facet chips collapse to a count, so the applied values need their own pill row. */
export const Mobile: Story = {
	args: {
		search: { ...UNFILTERED, severity: ["MAJOR"] },
		total: 2,
	},
	parameters: { chromatic: { viewports: [320] }, viewport: { defaultViewport: "reflow" } },
	play: async ({ canvas }) => {
		await canvas.findByTitle("Severity: Major");
	},
};

export const UnassessedStatuses: Story = {
	args: {
		search: {
			...UNFILTERED,
			outcome: ["NOT_APPLICABLE", "UNDETERMINED"],
		},
	},
	play: async ({ canvas, userEvent }) => {
		await expect(canvas.getByRole("combobox", { name: /Outcome/u })).toHaveTextContent("2");
		await userEvent.click(canvas.getByRole("button", { name: "Reset" }));
		await expect(canvas.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
	},
};

/**
 * Outcome is what every row's badge says, so it is the facet a count on the overview links to.
 */
export const ReportsAChosenOutcome: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Outcome" }));
		const listbox = await screen.findByRole("listbox", { name: "Outcome options" });
		await userEvent.click(await within(listbox).findByRole("option", { name: /Not met/u }));
		await expect(args.onPatch).toHaveBeenCalledWith({ outcome: ["NOT_MET"] });
	},
};

/** Arriving from an outcome count, the applied outcome reads as a pill at every width. */
export const AnOutcomeFromTheOverview: Story = {
	args: {
		search: {
			...UNFILTERED,
			outcome: ["MET"],
		},
		total: 4,
	},
	parameters: { chromatic: { viewports: [320] }, viewport: { defaultViewport: "reflow" } },
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByText("4 observations match your filters.");
		await canvas.findByTitle("Outcome: Met");
		await userEvent.click(canvas.getByLabelText("Clear outcome filter (Met)"));
		await expect(args.onPatch).toHaveBeenCalledWith({ outcome: undefined });
		await expect(canvas.queryByTitle("Outcome: Met")).not.toBeInTheDocument();
	},
};

/**
 * "Marked incorrect" has no facet of its own: it only arrives from the overview's count, so it is a
 * pill the reader can see and drop rather than a control they would never reach for.
 */
export const OnlyObservationsMarkedIncorrect: Story = {
	args: {
		search: {
			invalidated: true,
			...UNFILTERED,
		},
		total: 1,
	},
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByText("1 observation matches your filters.");
		canvas.getByTitle("Marked incorrect: Only");
		canvas.getByRole("button", { name: "Reset" });
		await userEvent.click(
			canvas.getByRole("button", { name: "Clear marked incorrect filter (Only)" }),
		);
		await expect(args.onPatch).toHaveBeenCalledWith({ invalidated: undefined });
		await expect(canvas.queryByTitle("Marked incorrect: Only")).not.toBeInTheDocument();
		await expect(canvas.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
	},
};

/**
 * A requested or backfilled review chose its work rather than meeting it as it arrived, so an
 * operator reading a mix separates them. Live is offered too, though no row badges it.
 */
export const ReportsAChosenOrigin: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Origin" }));
		const listbox = await screen.findByRole("listbox", { name: "Origin options" });
		await within(listbox).findByRole("option", { name: /Live/u });
		await userEvent.click(await within(listbox).findByRole("option", { name: /Backfilled/u }));
		await expect(args.onPatch).toHaveBeenCalledWith({ origin: ["BACKFILL"] });
	},
};

/** An applied origin is a filter like any other: a pill below `sm`, and something Reset clears. */
export const AnOriginOnAPhone: Story = {
	args: { search: { ...UNFILTERED, origin: ["MANUAL"] }, total: 2 },
	parameters: { chromatic: { viewports: [320] }, viewport: { defaultViewport: "reflow" } },
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByText("2 observations match your filters.");
		await canvas.findByTitle("Origin: Requested");
		await userEvent.click(canvas.getByRole("button", { name: "Reset" }));
		await expect(args.onReset).toHaveBeenCalledTimes(1);
		await expect(canvas.queryByTitle("Origin: Requested")).not.toBeInTheDocument();
	},
};

/** A facet the address already sets shows its value in its own control, in the row. */
export const SetFacetShowsItsValue: Story = {
	args: { search: { ...UNFILTERED, severity: ["MAJOR"] }, total: 2 },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("combobox", { name: /Severity/u })).toHaveTextContent("Major");
	},
};

/**
 * The scope came along from another tab: a developer, a practice and a range. Reset clears this
 * list's own facets and leaves the scope. "Clear scope" clears the scope.
 */
export const ScopeCarriedFromAnotherTab: Story = {
	args: {
		search: {
			...UNFILTERED,
			outcome: ["NOT_MET"],
			subjectUserId: PEOPLE.options[0]?.userId,
			practiceSlug: [workspacePractices[0]?.slug ?? ""],
			from: "2026-09-01",
			to: "2026-09-10",
		},
		total: 3,
	},
	play: async ({ args, canvas, userEvent }) => {
		const scope = canvas.getByRole("group", { name: "Applies to every tab" });
		await expect(
			within(scope).getByRole("combobox", { name: /^Developer(?::|$)/u }),
		).toHaveTextContent(PEOPLE.options[0]?.label ?? "");
		await expect(within(scope).getByRole("button", { name: /Observed/u })).toHaveTextContent(
			"1 Sep – 10 Sep 2026",
		);

		await userEvent.click(canvas.getByRole("button", { name: "Reset" }));
		await expect(args.onReset).toHaveBeenCalledTimes(1);
		await expect(
			within(scope).getByRole("combobox", { name: /^Developer(?::|$)/u }),
		).toHaveTextContent(PEOPLE.options[0]?.label ?? "");

		await userEvent.click(within(scope).getByRole("button", { name: "Clear scope" }));
		await expect(args.onClearScope).toHaveBeenCalledTimes(1);
		await expect(
			within(scope).queryByRole("button", { name: "Clear scope" }),
		).not.toBeInTheDocument();
	},
};

/** A chosen group narrows the practices on offer to the practices in it. */
export const AGroupNarrowsThePractices: Story = {
	args: { search: { ...UNFILTERED, groupSlug: [practiceGroups[0]?.slug ?? ""] } },
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Practice" }));
		const listbox = await screen.findByRole("listbox", { name: "Practice options" });
		const offered = await within(listbox).findAllByRole("option");
		const inGroup = workspacePractices.filter(
			(practice) => practice.groupSlug === practiceGroups[0]?.slug,
		);
		await expect(offered).toHaveLength(inGroup.length);
	},
};

/**
 * "Thin controllers" is turned off today, so the Practice facet leaves it out until the reader turns
 * on "Show turned-off practices" inside it. The same switch sits above the overview's Practices table.
 */
export const TurnedOffPracticesHidden: Story = {
	args: {
		practices: {
			...PRACTICES,
			options: PRACTICES.options.map((option) =>
				option.value === "thin-controllers" ? { ...option, turnedOff: true } : option,
			),
		},
	},
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Practice" }));
		const options = await screen.findByRole("listbox", { name: "Practice options" });
		await expect(within(options).queryByRole("option", { name: /Thin controllers/u })).toBeNull();
		const show = screen.getByRole("switch", { name: "Show turned-off practices (1 hidden)" });
		await userEvent.click(show);
		await within(options).findByRole("option", { name: /Thin controllers/u });
	},
};

/** A turned-off practice already chosen stays offered, so the choice can be seen and cleared. */
export const ChosenTurnedOffPracticeStays: Story = {
	args: {
		search: { ...UNFILTERED, practiceSlug: ["thin-controllers"] },
		practices: {
			...PRACTICES,
			options: PRACTICES.options.map((option) =>
				option.value === "thin-controllers" ? { ...option, turnedOff: true } : option,
			),
		},
	},
	play: async ({ canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: /^Practice/u }));
		const options = await screen.findByRole("listbox", { name: "Practice options" });
		await within(options).findByRole("option", { name: /Thin controllers/u });
	},
};
