import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import { StatefulPatch } from "@/stories/stateful";
import { precedes } from "@/test/dom";

import { clearedScope } from "./review-scope";
import { clearedReviewFacets, ReviewFilters } from "./ReviewFilters";
import { STORY_GROUPS, STORY_PEOPLE, STORY_PRACTICES } from "./story-scope-sources";

/**
 * The toolbar above the Reviews list: a status, a created-on window, and the count of what they
 * matched. It owns no data — it reports a patch and is handed the total, so the number beside it
 * always describes the answer on screen rather than the request in flight.
 *
 * The status options wear the same badges the rows do. A dropdown of plain words would make choosing
 * a filter an act of memory: matching a word here to a tag there.
 */
const meta = {
	component: ReviewFilters,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: {
		search: { status: undefined },
		onPatch: fn(),
		onReset: fn(),
		onClearScope: fn(),
		groups: STORY_GROUPS,
		practices: STORY_PRACTICES,
		people: STORY_PEOPLE,
		total: 7,
	},
	// Controlled, so a frozen `search` would leave every control inert while nothing looks broken.
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onPatch) => (
				<ReviewFilters
					{...args}
					search={search}
					onPatch={(patch) => {
						args.onPatch(patch);
						onPatch(patch);
					}}
					onReset={() => {
						args.onReset();
						onPatch(clearedReviewFacets());
					}}
					onClearScope={() => {
						args.onClearScope();
						onPatch(clearedScope());
					}}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ReviewFilters>;

export default meta;
type Story = StoryObj<typeof meta>;

/** Nothing chosen: the count says how many reviews exist, and there is nothing to reset. */
export const Unfiltered: Story = {
	play: async ({ canvas }) => {
		canvas.getByText("7 reviews.");
		await expect(canvas.queryByRole("button", { name: "Reset" })).not.toBeInTheDocument();
	},
};

/** Chosen: the count says what survived, and Reset appears to undo all of it at once. */
export const Filtered: Story = {
	args: {
		search: { status: ["COMPLETE"], from: "2026-07-28", to: "2026-07-29" },
		total: 2,
	},
	play: async ({ canvas }) => {
		canvas.getByText("2 reviews match your filters.");
		canvas.getByRole("button", { name: "Created: 28 Jul – 29 Jul 2026" });
		canvas.getByRole("button", { name: /Reset/u });
	},
};

/** One row is still "matches", not "match": the verb agrees with the count, not with the noun. */
export const OneMatch: Story = {
	args: { search: { status: ["FAILED"] }, total: 1 },
	play: async ({ canvas }) => {
		canvas.getByText("1 review matches your filters.");
	},
};

/**
 * No total yet. The count renders nothing rather than a zero, because a zero here would announce an
 * empty list a moment before the full one arrives.
 */
export const CountNotInYet: Story = {
	args: { total: undefined },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("status")).not.toBeInTheDocument();
	},
};

/**
 * Choosing statuses reports the facet the reader changed, and only that: sending them back to page
 * one is the route's job, because it owns the URL. Statuses add up rather than one replacing the
 * other.
 */
export const ChoosingStatuses: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const trigger = canvas.getByRole("combobox", { name: "Status" });
		await userEvent.click(trigger);
		const listbox = await screen.findByRole("listbox", { name: "Status options" });
		await userEvent.click(within(listbox).getByRole("option", { name: /Failed/u }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({ status: ["FAILED"] });
		await userEvent.click(within(listbox).getByRole("option", { name: /Partial/u }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({ status: ["FAILED", "PARTIAL"] });
		await userEvent.click(trigger);
		// Wait for the popup to finish leaving: a listbox caught mid-exit has already been detached
		// from the label that names it, which the accessibility check would audit.
		await waitFor(async () => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
	},
};

/**
 * Reset clears the list's own facets and keeps the shared scope, here the range, for the next tab.
 * "Clear scope" clears the range.
 */
export const ResettingKeepsTheScope: Story = {
	args: {
		search: { status: ["FAILED"], from: "2026-07-28", to: "2026-07-29" },
		total: 2,
	},
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByTitle("Status: Failed");
		await userEvent.click(canvas.getByRole("button", { name: /Reset/u }));
		await expect(args.onReset).toHaveBeenCalledTimes(1);
		await expect(canvas.queryByTitle("Status: Failed")).not.toBeInTheDocument();
		await expect(
			canvas.getByRole("button", { name: "Created: 28 Jul – 29 Jul 2026" }),
		).toBeVisible();
		await expect(canvas.queryByRole("button", { name: /Reset/u })).not.toBeInTheDocument();
		canvas.getByText("2 reviews match your filters.");

		await userEvent.click(canvas.getByRole("button", { name: "Clear scope" }));
		await expect(args.onClearScope).toHaveBeenCalledTimes(1);
		await expect(canvas.getByRole("button", { name: "Created" })).toBeVisible();
		canvas.getByText("2 reviews.");
	},
};

/**
 * A selected review narrows this list too. The selection shows once, above every tab, so the toolbar
 * draws no pill for it; "Clear scope" still clears it with the rest.
 */
export const SelectionApplies: Story = {
	args: {
		search: {
			status: undefined,
			reviewId: "3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84",
			selected: true,
		},
	},
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.queryByText("Not applied on this list")).not.toBeInTheDocument();
		await expect(canvas.queryByText(/3f2b6c1e/u)).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Clear scope" }));
		await expect(args.onClearScope).toHaveBeenCalledOnce();
	},
};

/** A developer and a practice from another tab narrow the reviews to those that recorded a result about them. */
export const ScopeCarriedFromAnotherTab: Story = {
	args: {
		search: {
			status: undefined,
			developerId: STORY_PEOPLE.options[0]?.accountId,
			practiceSlug: [STORY_PRACTICES.options[0]?.value ?? ""],
		},
		total: 2,
	},
	play: async ({ canvas }) => {
		canvas.getByText("2 reviews match your filters.");
		await expect(canvas.getByRole("combobox", { name: /Developer/u })).toHaveTextContent(
			STORY_PEOPLE.options[0]?.label ?? "",
		);
		await expect(canvas.queryByText("Not applied on this list")).not.toBeInTheDocument();
	},
};

/** The bar's controls, in the order the reader tabs through them: every control is a button. */
function controlsOf(group: HTMLElement) {
	return Array.from(
		group.querySelectorAll("button"),
		(control) => control.getAttribute("aria-label") ?? control.textContent.trim(),
	);
}

/**
 * The bar has the order every tab shares. The scope's developer, group, practice and date come
 * first, in their own group, because they apply to every tab; the layout moves that group above the
 * tabs. Then the list's own Status, and the count on the right.
 */
export const SharedOrder: Story = {
	play: async ({ canvas }) => {
		const scope = canvas.getByRole("group", { name: "Applies to every tab" });
		const filters = canvas.getByRole("group", { name: "Filters" });
		await expect(controlsOf(scope)).toStrictEqual(["Developer", "Group", "Practice", "Created"]);
		await expect(controlsOf(filters)).toStrictEqual(["Status"]);
		await expect(precedes(scope, filters)).toBe(true);
	},
};
