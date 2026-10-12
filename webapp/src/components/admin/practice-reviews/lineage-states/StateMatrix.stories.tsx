import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect } from "storybook/test";

import {
	LINEAGE_STATE_CASES,
	LINEAGE_STATE_GROUP_TITLES,
	type LineageStateCase,
	type LineageStateGroup,
} from "@/components/admin/practice-reviews/lineage-state-fixtures";
import { ReviewLineageGraph } from "@/components/admin/practice-reviews/ReviewLineageGraph";
import {
	DEFAULT_TRACE_DISPLAY,
	FULL_TRACE_DISPLAY,
	type TraceDisplay,
} from "@/components/admin/practice-reviews/trace-display";

/**
 * Every state the story of a review can meet, one small chain per state: how the review ran, what
 * started it, the merge against it, its observations, where its feedback went and what became of
 * it, an admin's decision, the developer's answer, and many records at once. Each case heads its
 * story with the verdict the chain gives and ends it with where the review's path ended. A story per
 * group, and one with all of them.
 */
const meta = {
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

const GROUPS: readonly LineageStateGroup[] = [
	"review",
	"trigger",
	"merge",
	"observations",
	"location",
	"delivery",
	"withholding",
	"decision",
	"response",
	"volume",
];

/** The groups whose state lives in each piece of feedback read best with every part shown. */
const DISPLAY: Partial<Record<LineageStateGroup, TraceDisplay>> = {
	delivery: FULL_TRACE_DISPLAY,
	withholding: FULL_TRACE_DISPLAY,
	decision: FULL_TRACE_DISPLAY,
	response: FULL_TRACE_DISPLAY,
};

function Case({ entry, display }: { entry: LineageStateCase; display: TraceDisplay }) {
	return (
		<section
			aria-label={entry.title}
			data-state-case={entry.key}
			className="min-w-0 rounded-lg border bg-background p-3"
		>
			<h3 className="mb-2 text-sm font-semibold">{entry.title}</h3>
			<ReviewLineageGraph
				lineage={{ status: "ready", lineage: entry.lineage }}
				defaultDisplay={display}
				reviewOutcome={entry.reviewOutcome}
				job={entry.job}
			/>
		</section>
	);
}

/**
 * The cases a group shows. The overview leaves out the one-reason-each withholding cases, which
 * the Withholding story shows, so one page stays quick to render.
 */
function casesOf(group: LineageStateGroup, overview: boolean): LineageStateCase[] {
	const cases = LINEAGE_STATE_CASES[group];
	return overview ? cases.filter((entry) => !entry.key.startsWith("withheld-")) : cases;
}

function Group({ group, overview = false }: { group: LineageStateGroup; overview?: boolean }) {
	return (
		<section aria-label={LINEAGE_STATE_GROUP_TITLES[group]} className="space-y-3">
			<h2 className="text-base font-semibold">{LINEAGE_STATE_GROUP_TITLES[group]}</h2>
			<div className="grid grid-cols-[repeat(auto-fill,minmax(min(24rem,100%),1fr))] items-start gap-4">
				{casesOf(group, overview).map((entry) => (
					<Case key={entry.key} entry={entry} display={DISPLAY[group] ?? DEFAULT_TRACE_DISPLAY} />
				))}
			</div>
		</section>
	);
}

function groupStory(group: LineageStateGroup): Story {
	return {
		render: () => (
			<div className="p-6">
				<Group group={group} />
			</div>
		),
		play: async ({ canvas, canvasElement }) => {
			const cases = canvas.getAllByRole("group", { name: "How this came about" });
			await expect(cases).toHaveLength(LINEAGE_STATE_CASES[group].length);
			// Every state says in one sentence what became of the review, and ends its path.
			for (const entry of LINEAGE_STATE_CASES[group]) {
				const section = canvasElement.querySelector(`[data-state-case="${entry.key}"]`);
				await expect(section?.querySelector("[data-trace-verdict]")?.textContent).toMatch(
					/^[A-Z].*\.$/u,
				);
				await expect(section?.querySelector('[data-step="end"]')).not.toBeNull();
			}
		},
	};
}

/** Queued, on hold, running, completed, failed, timed out, canceled, nothing assessed, answered earlier. */
export const Review = groupStory("review");

/** Every moment that starts a review, and a review no moment names. */
export const Trigger = groupStory("trigger");

/** Merged before, in the queue, while running, after, never, and the merge as the start. */
export const Merge = groupStory("merge");

/** Every verdict by severity, marked incorrect, an earlier review's answers, none, and 24. */
export const Observations = groupStory("observations");

/** GitHub, GitLab, another provider, their practice pages, Heph in the app and in Slack. */
export const Location = groupStory("location");

/** Every delivery state, a replacement across reviews, and a card shown then withheld. */
export const Delivery = groupStory("delivery");

/** One withholding reason of each kind, then every reason on its own. */
export const Withholding = groupStory("withholding");

/** Awaiting approval, approved, rejected and withdrawn. */
export const Decision = groupStory("decision");

/** Answered (privately), disputed with a reason, resolved by the work, no answer. */
export const Response = groupStory("response");

/** Many pieces of feedback from a few observations. */
export const Volume = groupStory("volume");

/** Every group on one page. */
export const Overview: Story = {
	render: () => (
		<div className="space-y-10 p-6">
			{GROUPS.map((group) => (
				<Group key={group} group={group} overview />
			))}
		</div>
	),
	play: async ({ canvasElement }) => {
		const total = GROUPS.reduce((sum, group) => sum + casesOf(group, true).length, 0);
		// A role query over a page this size takes longer than the test may; the attribute says the same.
		await expect(
			canvasElement.querySelectorAll('[role="group"][aria-label="How this came about"]'),
		).toHaveLength(total);
	},
};
