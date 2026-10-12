import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, within } from "storybook/test";

import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { withStandardPage } from "@/stories/decorators";
import { StatefulPatch } from "@/stories/stateful";

import { clearedFeedbackFacets, FeedbackFilters } from "./FeedbackFilters";
import { practiceGroups, workspaceMembers, workspacePractices } from "./fixtures";
import { practiceFacetOptions } from "./ObservationFilters";
import { clearedScope } from "./review-scope";
import type { FeedbackSearch } from "./review-search";
import type { ReviewPeople } from "./ReviewPersonFacet";
import { STORY_GROUPS } from "./story-scope-sources";

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

/**
 * The Feedback list's toolbar, on its own. It reports a patch and renders what it is given; which
 * rows come back is the route's business, so every state here is a `search` value rather than a
 * response.
 */
const meta = {
	component: FeedbackFilters,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	decorators: [withStandardPage],
	tags: ["autodocs"],
	args: {
		search: { deliveryState: undefined, withheldFamily: undefined, location: undefined },
		onPatch: fn(),
		onReset: fn(),
		onClearScope: fn(),
		groups: STORY_GROUPS,
		practices: PRACTICES,
		people: PEOPLE,
		total: 11,
	},
	// Controlled: `selected` on every facet comes back through the same `search` the choice is
	// reported on, so a frozen value would leave the whole toolbar looking dead.
	render: (args) => (
		<StatefulPatch<FeedbackSearch> initial={args.search}>
			{(search, patch) => (
				<FeedbackFilters
					{...args}
					search={search}
					onPatch={(next) => {
						patch(next);
						args.onPatch(next);
					}}
					onReset={() => {
						patch(clearedFeedbackFacets());
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
} satisfies Meta<typeof FeedbackFilters>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing set: no Reset, and the count is the whole list rather than what survived a filter. */
export const Unfiltered: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("11 pieces of feedback.");
		await expect(canvas.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
	},
};

/** Reset appears as soon as anything is set, and the count changes its wording with it. */
export const FilteredCountReadsDifferently: Story = {
	args: {
		search: { deliveryState: ["DELIVERED"], withheldFamily: undefined, location: undefined },
		total: 3,
	},
	play: async ({ canvas }) => {
		canvas.getByText("3 pieces of feedback match your filters.");
		canvas.getByRole("button", { name: "Reset" });
	},
};

/** One row of feedback is still a sentence that agrees with itself. */
export const ExactlyOneMatch: Story = {
	args: {
		search: { deliveryState: ["DELIVERED"], withheldFamily: undefined, location: undefined },
		total: 1,
	},
	play: async ({ canvas }) => {
		canvas.getByText("1 piece of feedback matches your filters.");
	},
};

export const ReportsAChosenOutcome: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Outcome" }));
		const listbox = await screen.findByRole("listbox", { name: "Outcome options" });
		await userEvent.click(await within(listbox).findByRole("option", { name: /Delivered/u }));
		await expect(args.onPatch).toHaveBeenCalledWith({ deliveryState: ["DELIVERED"] });
	},
};

/**
 * The person the list is filtered to may not be on the fetched member page; the facet names them from
 * the row that is on screen rather than showing a bare id.
 */
export const FilteredToSomebodyOffThePage: Story = {
	args: {
		search: {
			deliveryState: undefined,
			withheldFamily: undefined,
			location: undefined,
			recipientUserId: 404,
		},
		recipientName: "Barbara Liskov",
		total: 2,
	},
	play: async ({ canvas }) => {
		await canvas.findByRole("combobox", { name: "Developer: Barbara Liskov" });
	},
};

/** Below `sm` the facet chips collapse to a count, so the applied values need their own pill row. */
export const Mobile: Story = {
	args: {
		search: {
			deliveryState: ["DELIVERED", "SUPPRESSED"],
			withheldFamily: undefined,
			location: undefined,
		},
		total: 7,
	},
	parameters: { chromatic: { viewports: [320] }, viewport: { defaultViewport: "reflow" } },
	play: async ({ canvas }) => {
		await canvas.findByTitle("Outcome: Delivered");
	},
};

/**
 * A piece of feedback belongs to every practice one of its observations was about, so filtering by a
 * practice finds the feedback a practice's count on the overview was counting.
 */
export const ReportsAChosenPractice: Story = {
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Practice" }));
		const listbox = await screen.findByRole("listbox", { name: "Practice options" });
		await userEvent.click(
			await within(listbox).findByRole("option", { name: /Thin controllers/u }),
		);
		await expect(args.onPatch).toHaveBeenCalledWith({ practiceSlug: ["thin-controllers"] });
	},
};

/** Arriving from a practice's count, the practice reads as a pill at every width and clears there. */
export const APracticeOnAPhone: Story = {
	args: {
		search: {
			deliveryState: undefined,
			withheldFamily: undefined,
			location: undefined,
			practiceSlug: ["thin-controllers"],
		},
		total: 3,
	},
	parameters: { chromatic: { viewports: [320] }, viewport: { defaultViewport: "reflow" } },
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByText("3 pieces of feedback match your filters.");
		await canvas.findByTitle("Practice: Thin controllers");
		await userEvent.click(canvas.getByLabelText("Clear practice filter (Thin controllers)"));
		await expect(args.onPatch).toHaveBeenCalledWith({ practiceSlug: undefined });
		await expect(canvas.queryByTitle("Practice: Thin controllers")).not.toBeInTheDocument();
	},
};

/** The practices are still on their way, so the facet is disabled rather than offering nothing. */
export const WhileThePracticesLoad: Story = {
	args: { practices: { options: [], isLoading: true, isError: false } },
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("combobox", { name: "Practice" })).toBeDisabled();
	},
};

/**
 * Sorting does not narrow anything, which is why Reset leaves it alone — as on the observations
 * list. It sits in the row with the facets. Picking the default again clears it from the search
 * rather than writing the server's own default into the URL.
 */
export const SortIsNotAFilter: Story = {
	args: {
		search: {
			deliveryState: ["AWAITING_APPROVAL"],
			withheldFamily: undefined,
			location: undefined,
		},
		total: 3,
	},
	play: async ({ args, canvas, userEvent }) => {
		const sort = canvas.getByRole("combobox", { name: /Sort/u });
		await expect(sort).toHaveTextContent("Newest first");
		await userEvent.click(sort);
		await userEvent.click(await screen.findByRole("option", { name: "Oldest first" }));
		await expect(args.onPatch).toHaveBeenCalledWith({ order: "OLDEST" });

		await userEvent.click(canvas.getByRole("button", { name: "Reset" }));
		await expect(sort).toHaveTextContent("Oldest first");

		await userEvent.click(sort);
		await userEvent.click(await screen.findByRole("option", { name: "Newest first" }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({ order: undefined });
	},
};

/**
 * A group is part of the shared scope: it finds the feedback written from observations of any
 * practice in it, and it narrows the practices on offer.
 */
export const ReportsAChosenGroup: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const group = STORY_GROUPS.options[0];
		await userEvent.click(canvas.getByRole("combobox", { name: "Group" }));
		const listbox = await screen.findByRole("listbox", { name: "Group options" });
		await userEvent.click(
			await within(listbox).findByRole("option", { name: new RegExp(group?.label ?? "", "u") }),
		);
		await expect(args.onPatch).toHaveBeenCalledWith({ groupSlug: [group?.value] });
	},
};

/** Every facet sits in one row: Outcome, Channel, Why withheld and the sort, with no toggle. */
export const EveryFacetInOneRow: Story = {
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByRole("combobox", { name: "Outcome" });
		canvas.getByRole("combobox", { name: "Channel" });
		canvas.getByRole("combobox", { name: /Sort/u });
		await expect(canvas.queryByRole("button", { name: /More filters/u })).toBeNull();
		await userEvent.click(canvas.getByRole("combobox", { name: "Why withheld" }));
		const listbox = await screen.findByRole("listbox", { name: "Why withheld options" });
		await userEvent.click(await within(listbox).findByRole("option", { name: /Housekeeping/u }));
		await expect(args.onPatch).toHaveBeenCalledWith({ withheldFamily: ["HOUSEKEEPING"] });
	},
};
