import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import type { PracticeTraceEntry, PracticeScriptRun } from "@/api/types.gen";
import { reviewLevel } from "@/components/admin/practice-reviews/review-levels";
import { InlineLink } from "@/components/common/InlineLink";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { practiceScriptGuideUrl } from "@/components/practice-vocabulary/practice-script-run-status-defs";
import { groups } from "@/stories/practice-profile-story-mock-data";
import { levelsOpenedBy } from "@/test/detail-stack";

import { artifactTrace, practiceScriptRuns } from "./fixtures";
import { ReviewRunPracticeTable } from "./ReviewRunPracticeTable";

const [reviewed] = artifactTrace.practices;
if (reviewed === undefined) {
	throw new Error("The trace fixture lists no practice.");
}

/** The fix and *Details* name the practice for a screen reader, which lists every row's links together. */
const forPractice = (label: string) => `${label} for ${reviewed.practiceName}`;
const DETAILS = `Details of the practice script for ${reviewed.practiceName}`;

/** One reviewed practice whose practice script ended as `run`. */
const withPracticeScript = (run: PracticeScriptRun): PracticeTraceEntry[] => [
	{ ...reviewed, practiceScript: run },
];

/**
 * Every practice's answer on one piece of work. An admin also reads the operating facts under each
 * answer: what was sent, the autonomy, what the practice script did, and what the answer rests on.
 *
 * The practice script line is muted text and never a badge. It is about the script that ran before the
 * review, not about the developer's work, so it must not read as the practice's outcome.
 */
const meta = {
	component: ReviewRunPracticeTable,
	parameters: { layout: "padded" },
	tags: ["autodocs"],
	args: {
		workspaceSlug: "demo",
		entries: artifactTrace.practices,
		signals: artifactTrace.signals,
		groups,
		filters: {},
		onFiltersChange: fn(),
		onOpenPractice: fn(),
		onShowOccurrence: fn(),
		canAdminister: true,
		emptyMessage: "No practice in this workspace reviews pull requests.",
	},
	argTypes: {
		entries: { control: false },
		signals: { control: false },
		groups: { control: false },
	},
} satisfies Meta<typeof ReviewRunPracticeTable>;

export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {};

/** Every line ends in *Details*, the review's *Practice scripts* section, where the reasons are. */
async function expectDetails(link: HTMLElement) {
	await expect(link.getAttribute("href")).toMatch(/^\/w\/demo\/admin\/practices\/reviews\?/u);
	await expect(levelsOpenedBy(link)).toStrictEqual(["review:11111111-1111-1111-1111-111111111111"]);
}

/** A script that ran and gave the review places has nothing to fix, so *Details* is the only link. */
export const PracticeScriptFound: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.found) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/^Practice script found 4 places to check\./u)).toBeVisible();
		await expect(canvas.queryByRole("link", { name: /model$|script$/u })).not.toBeInTheDocument();
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/** A skipped script found nothing because it did not run, so the line names the missing model instead of a count. */
export const PracticeScriptSkipped: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.skipped) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				/^Practice script did not run: no decision model was set for this review\./u,
			),
		).toBeVisible();
		await expect(canvas.queryByText(/0 places/u)).not.toBeInTheDocument();
		// The optional embedding model was assigned, so only the required one is offered.
		await expect(canvas.getAllByRole("link", { name: /^Assign /u })).toHaveLength(1);
		await expect(
			canvas.getByRole("link", { name: forPractice("Assign a decision model") }),
		).toHaveAttribute("href", "/w/demo/admin/models?purpose=PRACTICE_DECISION");
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/**
 * A count of unrated items is model calls, never places: one call can carry many places. Of the
 * reasons, only the unusable answer points at the model, so the line offers that model alone.
 */
export const PracticeScriptCallsNotRated: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.partial) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/^Practice script found 3 places to check\. 8 calls were not rated\./u),
		).toBeVisible();
		await expect(
			canvas.getByRole("link", { name: forPractice("Check the decision model") }),
		).toHaveAttribute("href", "/w/demo/admin/models?purpose=PRACTICE_DECISION");
		await expect(
			canvas.queryByRole("link", { name: /^Check the reranking model/u }),
		).not.toBeInTheDocument();
		// The reasons are the review's to show, not the line's.
		await expect(canvas.queryByText(/too slow|took too long/u)).not.toBeInTheDocument();
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/**
 * The script handed the review nothing while some calls went unrated. The line never says that the
 * work held no places. No page fixes a slow model host, so the line links the guide that says who
 * can.
 */
export const PracticeScriptRatedNone: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.ratedNone) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/^Practice script handed the review no places\. 6 calls were not rated\./u),
		).toBeVisible();
		await expect(canvas.queryByText(/found no places/u)).not.toBeInTheDocument();
		await expect(canvas.queryByRole("link", { name: /model for/u })).not.toBeInTheDocument();
		await expect(
			canvas.getByRole("link", {
				name: new RegExp(`^${forPractice("Why calls go unrated")}`, "u"),
			}),
		).toHaveAttribute("href", practiceScriptGuideUrl("UNRATED_CALLS"));
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/**
 * Inside a drawer stack, the caller opens the review over the stack it already has, so *Details*
 * keeps the reader's place.
 */
export const PracticeScriptDetailsInAStack: Story = {
	args: {
		entries: withPracticeScript(practiceScriptRuns.found),
		reviewLink: (reviewId, label) => (
			<InlineLink
				className="inline-flex items-center gap-1"
				render={<DetailStackLink entry={reviewLevel(reviewId)} />}
			>
				{label}
			</InlineLink>
		),
	},
	play: async ({ canvas }) => {
		const details = canvas.getByRole("link", { name: DETAILS });
		await expect(details.getAttribute("href")).not.toMatch(/\/admin\/practices\/reviews/u);
		await expect(levelsOpenedBy(details)).toStrictEqual([
			"review:11111111-1111-1111-1111-111111111111",
		]);
	},
};

/**
 * The stage stopped before the script reported. No page here fixes that, so the line links the
 * guide that says what to raise or speed up.
 */
export const PracticeScriptNotFinished: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.notFinished) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/^Practice script did not finish\./u)).toBeVisible();
		await expect(canvas.queryByRole("link", { name: /model$|script$/u })).not.toBeInTheDocument();
		await expect(
			canvas.getByRole("link", {
				name: new RegExp(`^${forPractice("Why scripts stop early")}`, "u"),
			}),
		).toHaveAttribute("href", practiceScriptGuideUrl("STOPPED_EARLY"));
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/**
 * A script that ran out of time still handed the review what it found by then. A model whose
 * answers went unrated waits: the line links the guide first, because the review lost what the
 * script did not reach.
 */
export const PracticeScriptTimedOut: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.timedOut) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				/^Practice script ran out of time but found 2 places to check\. 1 call was not rated\./u,
			),
		).toBeVisible();
		await expect(canvas.queryByRole("link", { name: /model for/u })).not.toBeInTheDocument();
		await expect(
			canvas.getByRole("link", {
				name: new RegExp(`^${forPractice("Why scripts stop early")}`, "u"),
			}),
		).toHaveAttribute("href", practiceScriptGuideUrl("STOPPED_EARLY"));
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/** A script that failed is the author's to fix, so the line opens its practice in the editor. */
export const PracticeScriptFailed: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.failed) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/^Practice script failed\./u)).toBeVisible();
		const edit = canvas.getByRole("link", { name: forPractice("Edit the script") });
		await expect(edit.getAttribute("href")).toMatch(/^\/w\/demo\/admin\/practices\?/u);
		await expect(levelsOpenedBy(edit)).toStrictEqual([`practice-edit:${reviewed.practiceSlug}`]);
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/** The script is an operating fact, like the delivery sentence: a member reads the answer alone. */
export const PracticeScriptHiddenFromMembers: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.skipped), canAdminister: false },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(reviewed.explanation)).toBeVisible();
		await expect(canvas.queryByText(/Practice script/u)).not.toBeInTheDocument();
		await expect(
			canvas.queryByRole("link", { name: /^Assign |^Details/u }),
		).not.toBeInTheDocument();
	},
};

export const NarrowViewport: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.partial) },
	parameters: { viewport: { defaultViewport: "reflow" }, chromatic: { viewports: [320] } },
};
