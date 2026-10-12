import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn } from "storybook/test";

import type { PracticeTraceEntry, PracticeScriptRun } from "@/api/types.gen";
import { reviewLevel } from "@/components/admin/practice-reviews/review-levels";
import { InlineLink } from "@/components/common/InlineLink";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { practiceScriptGuideUrl } from "@/components/practice-vocabulary/practice-script-run-status-defs";
import { detailResult } from "@/stories/practice-detail-story-mock-data";
import { groups } from "@/stories/practice-profile-story-mock-data";
import { levelsOpenedBy } from "@/test/detail-stack";

import { artifactTrace, practiceScriptRuns } from "./fixtures";
import { ReviewRunPracticeTable, ReviewRunPracticeTableSkeleton } from "./ReviewRunPracticeTable";

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
 * answer: what was sent, the autonomy, what the practice's program did, and what the answer rests on.
 *
 * The program line is muted text and never a badge. It is about the program that reviewed the
 * practice, not about the developer's work, so it must not read as the practice's outcome.
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

/** A script that returned a result from its code alone has nothing to fix, so *Details* is the only link. */
export const PracticeScriptReturned: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.returned) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/^Practice script returned a result from its code alone\./u),
		).toBeVisible();
		await expect(canvas.queryByRole("link", { name: /model$|script$/u })).not.toBeInTheDocument();
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/** A practice without a script of its own gets the built-in review, which the line names as such. */
export const BuiltInReview: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.builtin) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/^Built-in review returned a result through the review\./u),
		).toBeVisible();
		await expect(canvas.queryByRole("link", { name: /^Edit the script/u })).not.toBeInTheDocument();
	},
};

/** A skipped script did not run, so the line names the missing model it needed. */
export const PracticeScriptSkipped: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.skipped) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				/^Practice script did not run: no decision model was set for this review\./u,
			),
		).toBeVisible();
		// The optional embedding model was assigned, so only the required one is offered.
		await expect(canvas.getAllByRole("link", { name: /^Assign /u })).toHaveLength(1);
		await expect(
			canvas.getByRole("link", { name: forPractice("Assign a decision model") }),
		).toHaveAttribute("href", "/w/demo/admin/models?purpose=PRACTICE_DECISION");
		await expectDetails(canvas.getByRole("link", { name: DETAILS }));
	},
};

/**
 * A count of unrated items is model calls: one call can carry many places. Of the reasons, only
 * the unusable answer points at the model, so the line offers that model alone.
 */
export const PracticeScriptCallsNotRated: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.partial) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				/^Practice script returned a result with its models\. 8 calls were not rated\./u,
			),
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
 * Every call went unrated for a reason no model change fixes. No page fixes a slow model host, so
 * the line links the guide that says who can.
 */
export const PracticeScriptRatedNone: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.ratedNone) },
	play: async ({ canvas }) => {
		await expect(canvas.getByText(/6 calls were not rated\.$/u)).toBeVisible();
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
		entries: withPracticeScript(practiceScriptRuns.returned),
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
 * The review ended before the script started, or the stage stopped before it reported. No page
 * here fixes either, so the line links the guide that says what to raise or speed up.
 */
export const PracticeScriptNotReached: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.notReached) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/^The review ended before the practice script started\./u),
		).toBeVisible();
		await expect(
			canvas.getByRole("link", {
				name: new RegExp(`^${forPractice("Why scripts stop early")}`, "u"),
			}),
		).toHaveAttribute("href", practiceScriptGuideUrl("STOPPED_EARLY"));
	},
};

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
 * A script that ran out of time returned no result. A model whose answers went unrated waits: the
 * line links the guide first, because the review lost what the script did not reach.
 */
export const PracticeScriptOutOfTime: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.outOfTime) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(
				/^Practice script stopped without a result: it ran out of time\. 1 call was not rated\./u,
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

/** A script that raised an error is the author's to fix, so the line opens its practice in the editor. */
export const PracticeScriptFailed: Story = {
	args: { entries: withPracticeScript(practiceScriptRuns.failed) },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText(/^Practice script stopped without a result: it raised an error\./u),
		).toBeVisible();
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

/**
 * The reader's own results, one line per result: its words, then its verdict, which counts its
 * problems and never ranks them. A practice with no result reads its recorded reason.
 */
export const ResultsAboutTheReader: Story = {
	args: {
		canAdminister: false,
		resultsByPractice: {
			[reviewed.practiceSlug]: [detailResult],
		},
	},
	play: async ({ canvas }) => {
		await expect(canvas.getByText(detailResult.summary)).toBeVisible();
		await expect(canvas.getByText("Not met · 3 problems")).toBeVisible();
		await expect(canvas.queryByText("Major")).not.toBeInTheDocument();
		await expect(canvas.queryByText(reviewed.explanation)).not.toBeInTheDocument();
	},
};

/** No practice reviews this kind of work, which only the caller can say. */
export const Empty: Story = {
	args: { entries: [] },
	play: async ({ canvas }) => {
		await expect(
			canvas.getByText("No practice in this workspace reviews pull requests."),
		).toBeVisible();
	},
};

/** The table's own shape while its rows load, so nothing jumps when they land. */
export const Loading: Story = {
	render: () => <ReviewRunPracticeTableSkeleton rows={3} />,
	play: async ({ canvas }) => {
		await expect(
			canvas.getByRole("table", { name: "Every practice on this work" }),
		).toHaveAttribute("aria-busy", "true");
	},
};
