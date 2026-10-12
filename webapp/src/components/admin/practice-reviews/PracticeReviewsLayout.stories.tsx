import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, within } from "storybook/test";

import { withStandardPage } from "@/stories/decorators";
import { expectNoPageOverflow } from "@/stories/reflow";
import { precedes } from "@/test/dom";

import { PracticeReviewsLayout } from "./PracticeReviewsLayout";
import type { RunsSearch } from "./review-search";
import { ReviewRunFilters } from "./ReviewRunFilters";
import { ReviewScopeFilters } from "./ReviewScopeFilters";
import { ReviewSelectionBar } from "./ReviewSelectionBar";
import { STORY_GROUPS, STORY_PEOPLE, STORY_PRACTICES } from "./story-scope-sources";

/**
 * The page's header and its four sections, drawn as line tabs but made of links, since each changes
 * the URL. The scope and the tracked record are in one row above the tabs, because they apply to
 * every tab; a tab's own filters stay below the tabs. Which scope a section link carries is read
 * from the URL, so it is proved by the route's tests.
 */
const meta = {
	component: PracticeReviewsLayout,
	parameters: {
		layout: "fullscreen",
		chromatic: { viewports: [320, 1440] },
	},
	args: {
		workspaceSlug: "demo",
		children: <p className="text-sm text-muted-foreground">The section renders here.</p>,
	},
	argTypes: { children: { control: false } },
	decorators: [withStandardPage],
	tags: ["autodocs"],
} satisfies Meta<typeof PracticeReviewsLayout>;

export default meta;
type Story = StoryObj<typeof meta>;

const UNFILTERED: RunsSearch = { status: undefined, resultProcessing: undefined };

const SCOPED: RunsSearch = {
	...UNFILTERED,
	subjectUserId: STORY_PEOPLE.options[0]?.userId,
	practiceSlug: [STORY_PRACTICES.options[0]?.value ?? ""],
	from: "2026-09-01",
	to: "2026-09-10",
};

/** The Reviews tab as the route draws it: its filters, then its list. */
function ReviewsTab({ search }: { search: RunsSearch }) {
	return (
		<section aria-label="Practice reviews" className="space-y-4">
			<ReviewRunFilters
				search={search}
				onPatch={fn()}
				onReset={fn()}
				onClearScope={fn()}
				groups={STORY_GROUPS}
				practices={STORY_PRACTICES}
				people={STORY_PEOPLE}
				total={7}
			/>
			<p className="text-sm text-muted-foreground">The reviews render here.</p>
		</section>
	);
}

const TRACKING = (
	<ReviewSelectionBar
		kind="review"
		name={{ status: "ready", name: "Review of #1423 · 5 October, 2:02 pm" }}
		trace={{ kind: "review", id: "11111111-1111-1111-1111-111111111111" }}
		onClear={fn()}
	/>
);

/** The row above the tabs, the tabs, and the tab's own filters below them, in document order. */
function placement(canvas: Parameters<NonNullable<Story["play"]>>[0]["canvas"]) {
	const scope = canvas.getByRole("group", { name: "Applies to every tab" });
	const sections = canvas.getByRole("navigation", { name: "Practice review sections" });
	const filters = canvas.getByRole("group", { name: "Filters" });
	return { scope, sections, filters };
}

export const Default: Story = {
	play: async ({ canvas }) => {
		await expect(canvas.getByRole("heading", { level: 1 })).toHaveTextContent("Practice reviews");
		const sections = within(canvas.getByRole("navigation", { name: "Practice review sections" }));
		await expect(sections.getAllByRole("link").map((link) => link.textContent)).toEqual([
			"Overview",
			"Reviews",
			"Results",
			"Feedback",
		]);
		await expect(sections.getByRole("link", { name: "Reviews" })).toHaveAttribute(
			"href",
			"/w/demo/admin/practices/reviews/runs",
		);
		canvas.getByText("The section renders here.");
	},
};

/**
 * On a list, the scope's facets are above the tabs, labelled as applying to every tab. The tab's own
 * Status, Result processing and the count stay below the tabs.
 */
export const ScopeAboveTheTabs: Story = {
	args: { children: <ReviewsTab search={UNFILTERED} /> },
	parameters: { router: { initialUrl: "/w/demo/admin/practices/reviews/runs" } },
	play: async ({ canvas }) => {
		const { scope, sections, filters } = placement(canvas);
		await expect(precedes(scope, sections)).toBe(true);
		await expect(precedes(sections, filters)).toBe(true);
		canvas.getByText("Applies to every tab");
		for (const name of ["Developer", "Group", "Practice"]) {
			within(scope).getByRole("combobox", { name });
		}
		within(scope).getByRole("button", { name: "Created" });
		within(filters).getByRole("combobox", { name: "Status" });
		within(filters).getByRole("combobox", { name: "Result processing" });
		within(filters).getByText("7 reviews.");
		// No scope is set, so there is nothing to clear.
		await expect(
			within(scope).queryByRole("button", { name: "Clear scope" }),
		).not.toBeInTheDocument();
	},
};

/**
 * A scope and a tracked record: "Clear scope", the tracked review, "Stop tracking" and "Show trace"
 * are all in the row above the tabs. Only the tab's own filters are below them.
 */
export const ScopeAndTracking: Story = {
	args: { children: <ReviewsTab search={SCOPED} />, selection: TRACKING },
	parameters: { router: { initialUrl: "/w/demo/admin/practices/reviews/runs" } },
	play: async ({ canvas }) => {
		const { scope, sections, filters } = placement(canvas);
		await expect(precedes(scope, sections)).toBe(true);
		within(scope).getByRole("button", { name: "Clear scope" });
		await expect(within(scope).getByRole("combobox", { name: /^Developer/u })).toHaveTextContent(
			STORY_PEOPLE.options[0]?.label ?? "",
		);
		const tracking = within(scope).getByRole("group", { name: "Selection" });
		within(tracking).getByText("Review of #1423 · 5 October, 2:02 pm");
		within(tracking).getByRole("button", { name: /^Stop tracking/u });
		within(tracking).getByRole("link", { name: "Show trace" });
		await expect(
			within(filters).queryByRole("button", { name: "Clear scope" }),
		).not.toBeInTheDocument();
	},
};

/**
 * The overview applies none of the scope. The scope it keeps for the next tab shows in the same row
 * above the tabs, as muted pills, and so does the tracked record.
 */
export const OverviewKeepsTheScope: Story = {
	args: {
		children: (
			<ReviewScopeFilters
				tab="overview"
				scope={{ personId: STORY_PEOPLE.options[0]?.userId, from: "2026-09-01", to: "2026-09-10" }}
				onChange={fn()}
				onClear={fn()}
				dateTitle="Dates"
				groups={STORY_GROUPS}
				practices={STORY_PRACTICES}
				people={STORY_PEOPLE}
				notAppliedNote="Not applied on the overview"
			/>
		),
		selection: TRACKING,
	},
	play: async ({ canvas }) => {
		const scope = canvas.getByRole("group", { name: "Applies to every tab" });
		await expect(
			precedes(scope, canvas.getByRole("navigation", { name: "Practice review sections" })),
		).toBe(true);
		await expect(within(scope).getAllByText("Not applied on the overview")).toHaveLength(2);
		within(scope).getByRole("button", { name: "Clear scope" });
		within(scope).getByRole("group", { name: "Selection" });
		// The overview has no control for a part it does not apply.
		await expect(within(scope).queryByRole("combobox")).not.toBeInTheDocument();
	},
};

/** At 320px the row above the tabs wraps, and four sections share the track, with no sideways scroll. */
export const Reflow: Story = {
	args: { children: <ReviewsTab search={SCOPED} />, selection: TRACKING },
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
		router: { initialUrl: "/w/demo/admin/practices/reviews/runs" },
	},
	play: async ({ canvas }) => {
		const { scope, sections } = placement(canvas);
		await expect(precedes(scope, sections)).toBe(true);
		await expectNoPageOverflow();
	},
};

/**
 * The router marks the section at the current address, and the tab's bar and weight follow that
 * mark; a section is a link, never a `tab`.
 */
export const CurrentSection: Story = {
	parameters: { router: { initialUrl: "/w/demo/admin/practices/reviews/feedback" } },
	play: async ({ canvas }) => {
		const sections = within(canvas.getByRole("navigation", { name: "Practice review sections" }));
		await expect(sections.getByRole("link", { name: "Feedback" })).toHaveAttribute(
			"aria-current",
			"page",
		);
		// Overview's address is a prefix of every other section's, so it is the one a fuzzy match would
		// also mark.
		for (const name of ["Overview", "Reviews", "Results"]) {
			await expect(sections.getByRole("link", { name })).not.toHaveAttribute("aria-current");
		}
		await expect(sections.queryByRole("tab")).not.toBeInTheDocument();
	},
};

/**
 * Each list tab carries how many it holds under the shared scope, with thousands separated and
 * figures of one width, so the counts line up as they change. A screen reader hears "Reviews, 1,688".
 * The overview is not a list and has no count.
 */
export const TabCounts: Story = {
	args: {
		children: <ReviewsTab search={SCOPED} />,
		counts: { runs: 1688, observations: 13_308, feedback: 1951 },
	},
	parameters: { router: { initialUrl: "/w/demo/admin/practices/reviews/runs" } },
	play: async ({ canvas }) => {
		const sections = within(canvas.getByRole("navigation", { name: "Practice review sections" }));
		await expect(sections.getAllByRole("link").map((link) => link.textContent)).toEqual([
			"Overview",
			"Reviews1,688",
			"Observations13,308",
			"Feedback1,951",
		]);
		await expect(sections.getByRole("link", { name: "Reviews, 1,688" })).toHaveAttribute(
			"aria-current",
			"page",
		);
		sections.getByRole("link", { name: "Observations, 13,308" });
		sections.getByRole("link", { name: "Feedback, 1,951" });
		await expect(sections.getByText("13,308")).toHaveClass("tabular-nums");
	},
};

/**
 * Until a count is known its tab shows the name alone, so the tab bar never waits for a count. A
 * count known to be nothing says 0, which an unknown one never does.
 */
export const TabCountsLoading: Story = {
	args: { counts: { observations: 0 } },
	parameters: { router: { initialUrl: "/w/demo/admin/practices/reviews/runs" } },
	play: async ({ canvas }) => {
		const sections = within(canvas.getByRole("navigation", { name: "Practice review sections" }));
		sections.getByRole("link", { name: "Reviews" });
		sections.getByRole("link", { name: "Observations, 0" });
		sections.getByRole("link", { name: "Feedback" });
	},
};

/** At 320px the counted tabs wrap onto a second line, with no sideways scroll. */
export const TabCountsReflow: Story = {
	args: {
		children: <ReviewsTab search={SCOPED} />,
		counts: { runs: 1688, observations: 13_308, feedback: 1951 },
	},
	parameters: {
		viewport: { defaultViewport: "reflow" },
		chromatic: { viewports: [320] },
		router: { initialUrl: "/w/demo/admin/practices/reviews/runs" },
	},
	play: async () => {
		await expectNoPageOverflow();
	},
};
