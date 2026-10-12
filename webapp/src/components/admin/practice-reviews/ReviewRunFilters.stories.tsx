import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import { StatefulPatch } from "@/stories/stateful";
import { precedes } from "@/test/dom";

import { clearedScope } from "./review-scope";
import { clearedRunFacets, ReviewRunFilters } from "./ReviewRunFilters";
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
	component: ReviewRunFilters,
	parameters: { layout: "padded", chromatic: { viewports: [320, 1440] } },
	tags: ["autodocs"],
	args: {
		search: { status: undefined, resultProcessing: undefined },
		onPatch: fn(),
		onReset: fn(),
		onClearScope: fn(),
		groups: STORY_GROUPS,
		practices: STORY_PRACTICES,
		people: STORY_PEOPLE,
		total: 7,
	},
	// Controlled, so a frozen `search` would leave every control inert while nothing looks broken.
	// Nothing here recomputes "is anything filtered" — the toolbar derives that from the `search` it
	// is already given, so a story cannot hand it a search and a contradicting answer about it.
	render: (args) => (
		<StatefulPatch initial={args.search}>
			{(search, onPatch) => (
				<ReviewRunFilters
					{...args}
					search={search}
					onPatch={(patch) => {
						args.onPatch(patch);
						onPatch(patch);
					}}
					onReset={() => {
						args.onReset();
						onPatch(clearedRunFacets());
					}}
					onClearScope={() => {
						args.onClearScope();
						onPatch(clearedScope());
					}}
				/>
			)}
		</StatefulPatch>
	),
} satisfies Meta<typeof ReviewRunFilters>;

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
		search: {
			status: ["COMPLETED"],
			resultProcessing: undefined,
			from: "2026-07-28",
			to: "2026-07-29",
		},
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
	args: { search: { status: ["FAILED"], resultProcessing: undefined }, total: 1 },
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
 * one is the route's job, because it owns the URL. Statuses add up — failed and timed out are both
 * reviews that did not finish — rather than one replacing the other.
 */
export const ChoosingStatuses: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const trigger = canvas.getByRole("combobox", { name: "Status" });
		await userEvent.click(trigger);
		const listbox = await screen.findByRole("listbox", { name: "Status options" });
		await userEvent.click(within(listbox).getByRole("option", { name: /Failed/u }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({ status: ["FAILED"] });
		await userEvent.click(within(listbox).getByRole("option", { name: /Timed out/u }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({ status: ["FAILED", "TIMED_OUT"] });
		await userEvent.click(trigger);
		// Wait for the popup to finish leaving. The accessibility check runs when the play function
		// returns, and a listbox caught mid-exit has already been detached from the label that names
		// it — which under a loaded test pool is long enough to be audited.
		await waitFor(async () => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
	},
};

/**
 * Reset clears the list's own facets and keeps the shared scope, here the range, for the next tab.
 * "Clear scope" clears the range.
 */
export const ResettingKeepsTheScope: Story = {
	args: {
		search: {
			status: ["FAILED"],
			resultProcessing: ["FAILED"],
			from: "2026-07-28",
			to: "2026-07-29",
		},
		total: 2,
	},
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByTitle("Status: Failed");
		canvas.getByTitle("Result processing: Result processing failed");
		await userEvent.click(canvas.getByRole("button", { name: /Reset/u }));
		await expect(args.onReset).toHaveBeenCalledTimes(1);
		await expect(canvas.queryByTitle("Status: Failed")).not.toBeInTheDocument();
		await expect(
			canvas.queryByTitle("Result processing: Result processing failed"),
		).not.toBeInTheDocument();
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
 * A review can complete and still fail to process what it produced, which its status alone reports
 * as a success — so what happened to its results is a facet of its own, beside Status.
 */
export const ChoosingAResultProcessingState: Story = {
	play: async ({ args, canvas, userEvent }) => {
		const trigger = canvas.getByRole("combobox", { name: "Result processing" });
		await userEvent.click(trigger);
		const listbox = await screen.findByRole("listbox", { name: "Result processing options" });
		await userEvent.click(
			within(listbox).getByRole("option", { name: /Result processing failed/u }),
		);
		await expect(args.onPatch).toHaveBeenLastCalledWith({ resultProcessing: ["FAILED"] });
		await userEvent.click(trigger);
		// See `ChoosingStatuses`: the audit must not catch the listbox mid-exit.
		await waitFor(async () => expect(screen.queryByRole("listbox")).not.toBeInTheDocument());
	},
};

/** Below `sm` the facet collapses to a count, so the applied state is a pill that clears itself. */
export const AResultProcessingStateOnAPhone: Story = {
	args: { search: { status: undefined, resultProcessing: ["FAILED"] }, total: 1 },
	parameters: { chromatic: { viewports: [320] }, viewport: { defaultViewport: "reflow" } },
	play: async ({ args, canvas, userEvent }) => {
		canvas.getByText("1 review matches your filters.");
		await canvas.findByTitle("Result processing: Result processing failed");
		await userEvent.click(
			canvas.getByLabelText("Clear result processing filter (Result processing failed)"),
		);
		await expect(args.onPatch).toHaveBeenCalledWith({ resultProcessing: undefined });
		await expect(
			canvas.queryByTitle("Result processing: Result processing failed"),
		).not.toBeInTheDocument();
	},
};

/**
 * A selected review or piece of work narrows this list too. The selection shows once, above every
 * tab, so the toolbar draws no pill for it; "Clear scope" still clears it with the rest.
 */
export const SelectionApplies: Story = {
	args: {
		search: {
			status: undefined,
			resultProcessing: undefined,
			agentJobId: "3f2b6c1e-8a4d-4f0b-9c7e-2d5a1b0c9e84",
			artifactKind: "scm.pull_request",
			artifactId: 42,
		},
	},
	play: async ({ args, canvas, userEvent }) => {
		await expect(canvas.queryByText("Not applied on this list")).not.toBeInTheDocument();
		await expect(canvas.queryByText(/3f2b6c1e/u)).not.toBeInTheDocument();
		await userEvent.click(canvas.getByRole("button", { name: "Clear scope" }));
		await expect(args.onClearScope).toHaveBeenCalledOnce();
	},
};

/** A developer and a practice from another tab narrow the reviews to those that observed them. */
export const ScopeCarriedFromAnotherTab: Story = {
	args: {
		search: {
			status: undefined,
			resultProcessing: undefined,
			subjectUserId: STORY_PEOPLE.options[0]?.userId,
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

/**
 * Reached from the overview's count of reviews that finished after their work was merged. No
 * control offers it, so its pill is the one way to see it and to clear it.
 */
export const FinishedAfterMerge: Story = {
	args: { search: { status: undefined, resultProcessing: undefined, finishedAfterMerge: true } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: "Clear finished after merge filter (Only)" }),
		);
		await expect(args.onPatch).toHaveBeenCalledWith({ finishedAfterMerge: undefined });
		await expect(
			canvas.queryByRole("button", { name: /finished after merge/u }),
		).not.toBeInTheDocument();
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
 * tabs. Then the list's own Status and Result processing, and the count on the right.
 */
export const SharedOrder: Story = {
	play: async ({ canvas }) => {
		const scope = canvas.getByRole("group", { name: "Applies to every tab" });
		const filters = canvas.getByRole("group", { name: "Filters" });
		await expect(controlsOf(scope)).toStrictEqual(["Developer", "Group", "Practice", "Created"]);
		await expect(controlsOf(filters)).toStrictEqual(["Status", "Result processing"]);
		await expect(precedes(scope, filters)).toBe(true);
	},
};

/**
 * "No review started" is a value of Status, not a second toggle: it lists work, so it never combines
 * with a status a review has, the date filters when the moments were recorded, and the filters only
 * reviews have go. A developer in the scope stays, marked as not applied.
 */
export const NoReviewStarted: Story = {
	args: { search: { status: undefined, resultProcessing: ["FAILED"], subjectUserId: 7 } },
	play: async ({ args, canvas, userEvent }) => {
		await userEvent.click(canvas.getByRole("combobox", { name: "Status" }));
		await userEvent.click(await screen.findByRole("option", { name: "No review started" }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({
			status: ["NOT_STARTED"],
			resultProcessing: undefined,
			finishedAfterMerge: undefined,
		});
		await canvas.findByText("7 pieces of work match your filters.");
		canvas.getByRole("button", { name: "Recorded" });
		await expect(canvas.queryByRole("combobox", { name: "Result processing" })).toBeNull();
		await expect(canvas.queryByRole("combobox", { name: /^Developer/u })).toBeNull();
		canvas.getByText("Not applied on this list");

		await userEvent.click(await screen.findByRole("option", { name: "Running" }));
		await expect(args.onPatch).toHaveBeenLastCalledWith({
			status: ["RUNNING"],
			withoutReview: undefined,
		});
		canvas.getByText("7 reviews match your filters.");
	},
};
