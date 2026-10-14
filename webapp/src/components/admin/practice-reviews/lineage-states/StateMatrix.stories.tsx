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

const meta = {
	parameters: { layout: "fullscreen" },
	tags: ["autodocs"],
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

const GROUPS: readonly LineageStateGroup[] = [
	"review",
	"origin",
	"item",
	"results",
	"destination",
	"delivery",
	"volume",
];

const DISPLAY: Partial<Record<LineageStateGroup, TraceDisplay>> = {
	destination: FULL_TRACE_DISPLAY,
	delivery: FULL_TRACE_DISPLAY,
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
				review={entry.review}
			/>
		</section>
	);
}

function Group({ group }: { group: LineageStateGroup }) {
	return (
		<section aria-label={LINEAGE_STATE_GROUP_TITLES[group]} className="space-y-3">
			<h2 className="text-base font-semibold">{LINEAGE_STATE_GROUP_TITLES[group]}</h2>
			<div className="grid grid-cols-[repeat(auto-fill,minmax(min(24rem,100%),1fr))] items-start gap-4">
				{LINEAGE_STATE_CASES[group].map((entry) => (
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

export const Review = groupStory("review");

export const Origin = groupStory("origin");

export const Item = groupStory("item");

export const Results = groupStory("results");

export const Destination = groupStory("destination");

export const Delivery = groupStory("delivery");

export const Volume = groupStory("volume");

/** Every group on one page. */
export const Overview: Story = {
	render: () => (
		<div className="space-y-10 p-6">
			{GROUPS.map((group) => (
				<Group key={group} group={group} />
			))}
		</div>
	),
	play: async ({ canvasElement }) => {
		const total = GROUPS.reduce((sum, group) => sum + LINEAGE_STATE_CASES[group].length, 0);
		// A role query over a page this size takes longer than the test may; the attribute says the same.
		await expect(
			canvasElement.querySelectorAll('[role="group"][aria-label="How this came about"]'),
		).toHaveLength(total);
	},
};
