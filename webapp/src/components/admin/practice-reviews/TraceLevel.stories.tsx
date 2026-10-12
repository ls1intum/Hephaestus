import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { tracedArtifact } from "@/components/practice-trace/fixtures";
import { withPageBehind } from "@/stories/decorators";
import { settledDrawerPanel } from "@/stories/overlay";

import { FeedbackLevel } from "./FeedbackLevel";
import { observationDetail, reviewFeedbackDetail, reviewJob } from "./fixtures";
import { LINEAGE_OBS, LINEAGE_REVIEW_ID, wholeReview } from "./lineage-fixtures";
import { lineageStateCase } from "./lineage-state-fixtures";
import { ObservationLevel } from "./ObservationLevel";
import { levelRecord, type RecordRef, recordLevel, traceLevel } from "./review-layers";
import type { PracticeReviewLevel } from "./review-levels";
import { DEFAULT_TRACE_DISPLAY, FULL_TRACE_DISPLAY, type TraceDisplay } from "./trace-display";
import { ALL_STEPS, type TraceFilter } from "./trace-filter";
import { TraceLevel, type TraceLevelProps } from "./TraceLevel";

const TRACE_ENTRY = traceLevel({ kind: "review", id: "11111111-1111-1111-1111-111111111111" });
const PATH = { behind: [{ label: "Practice reviews", depth: 0 }], onClose: fn() };

/**
 * The trace and what opens over it, as the route stacks them: the wide trace, and one record's
 * sheet over it when a step is opened. The sheet is the same as from the record's row, and links
 * nowhere, so nothing opens deeper. Escape closes the sheet back to the trace, then the trace.
 */
function TraceStack(props: Omit<TraceLevelProps, "path" | "onOpen">) {
	const [stack, setStack] = useState<PracticeReviewLevel[]>([TRACE_ENTRY]);
	const [display, setDisplay] = useState<TraceDisplay>(props.display);
	const [filter, setFilter] = useState<TraceFilter>(props.filter);
	const open = (record: RecordRef) => setStack([TRACE_ENTRY, recordLevel(record)]);
	return (
		<DetailDrawerStack stack={stack} onClose={(depth) => setStack(stack.slice(0, depth))}>
			{(entry, level) => {
				const path = {
					behind: level.depth === 0 ? PATH.behind : [...PATH.behind, { label: "Trace", depth: 1 }],
					onClose: (depth: number) => setStack(stack.slice(0, depth)),
				};
				if (entry.kind === "trace") {
					return (
						<TraceLevel
							{...props}
							nested={level.nested}
							path={path}
							display={display}
							onDisplayChange={setDisplay}
							filter={filter}
							onFilterChange={setFilter}
							onOpen={open}
						/>
					);
				}
				const record = levelRecord(entry);
				if (record?.kind === "feedback") {
					return (
						<FeedbackLevel
							nested={level.nested}
							path={path}
							feedback={{ status: "ready", feedback: reviewFeedbackDetail }}
							isDeciding={false}
							onApprove={fn()}
							onReject={fn()}
							onChangeWithdrawal={fn(async () => undefined)}
							isChangingWithdrawal={false}
						/>
					);
				}
				return (
					<ObservationLevel
						nested={level.nested}
						path={path}
						observation={{ status: "ready", observation: observationDetail(LINEAGE_OBS.cache404) }}
						onChangeValidity={fn(async () => undefined)}
						isChangingValidity={false}
					/>
				);
			}}
		</DetailDrawerStack>
	);
}

const meta = {
	component: TraceLevel,
	parameters: { layout: "fullscreen" },
	decorators: [withPageBehind],
	tags: ["autodocs"],
	args: {
		path: PATH,
		title: "Review of #1423",
		chain: { status: "ready", lineage: wholeReview },
		here: { kind: "review" },
		onOpen: fn(),
		display: DEFAULT_TRACE_DISPLAY,
		onDisplayChange: fn(),
		filter: ALL_STEPS,
		onFilterChange: fn(),
		// The review as its sheet reads it, so the trace and the sheet tell the same times and model.
		job: reviewJob(LINEAGE_REVIEW_ID),
	},
	argTypes: { path: { control: false }, chain: { control: false }, job: { control: false } },
	render: (args) => <TraceStack {...args} />,
} satisfies Meta<typeof TraceLevel>;

export default meta;
type Story = StoryObj<typeof meta>;

function showToggle(panel: HTMLElement, name: string): HTMLElement {
	return within(within(panel).getByRole("group", { name: "Show" })).getByRole("button", { name });
}

/**
 * As a reader first opens it: the verdict on top, the four stages, the review "You started here" with
 * its times. Times are on; system info, siblings and connections are off, and nothing is unfolded.
 */
export const Default: Story = {
	play: async () => {
		const element = await settledDrawerPanel();
		const panel = within(element);
		panel.getByText("You started here");
		await expect(showToggle(element, "Times")).toHaveAttribute("aria-pressed", "true");
		for (const name of ["System info", "Siblings", "Connections"]) {
			await expect(showToggle(element, name)).toHaveAttribute("aria-pressed", "false");
		}
		const story = panel.getByRole("group", { name: "How this came about" });
		await expect(story.querySelector("[data-trace-verdict]")).not.toBeNull();
		await expect(story.querySelector("[data-step-facts]")).toBeNull();
		await expect(story.querySelectorAll("time").length).toBeGreaterThan(0);
		// The review's facts come from the review itself, as on its sheet.
		await expect(story.querySelector("[data-trace-verdict]")).toHaveTextContent(/ran 5 min/u);
		await expect(story.querySelector('[data-step="first-read"]')).not.toBeNull();
	},
};

/**
 * Every part on: the times, the system info with the facts under where the reader started, every
 * observation by practice, and connectors from each observation to the feedback written from it.
 */
export const AllOn: Story = {
	args: { display: FULL_TRACE_DISPLAY },
	play: async () => {
		const panel = await settledDrawerPanel();
		for (const name of ["Times", "System info", "Siblings", "Connections"]) {
			await expect(showToggle(panel, name)).toHaveAttribute("aria-pressed", "true");
		}
		await expect(panel.querySelector("[data-step-facts]")).toHaveTextContent(/Asked for/u);
		await expect(panel.querySelector("[data-step-facts]")).toHaveTextContent(/gpt-5\.4-mini/u);
		await waitFor(async () => {
			await expect(panel.querySelectorAll("path[data-connector-line]").length).toBeGreaterThan(0);
		});
	},
};

/** Every part off: the path of the record alone, with no times, facts or connectors. */
export const AllOff: Story = {
	args: {
		display: { times: false, system: false, siblings: false, connections: false },
	},
	play: async () => {
		const panel = await settledDrawerPanel();
		const steps = within(panel).getByRole("list", { name: "Steps, oldest first" });
		await expect(steps.querySelectorAll("time")).toHaveLength(0);
		await expect(panel.querySelector("[data-step-facts]")).toBeNull();
		await expect(panel.querySelectorAll("path[data-connector-line]")).toHaveLength(0);
	},
};

/**
 * "Expand all" opens every folded part at once: what came before the review, every step's facts and
 * the legend. "Collapse all" folds them again.
 */
export const ExpandAll: Story = {
	play: async ({ userEvent }) => {
		const element = await settledDrawerPanel();
		const panel = within(element);
		await userEvent.click(panel.getByRole("button", { name: "Expand all" }));
		const steps = panel.getByRole("list", { name: "Steps, oldest first" });
		await expect(steps.querySelector('[data-step="before"]')).toBeNull();
		await expect(element.querySelectorAll("[data-step-facts]").length).toBeGreaterThan(3);
		await userEvent.click(panel.getByRole("button", { name: "Collapse all" }));
		await expect(element.querySelector("[data-step-facts]")).toBeNull();
		await expect(steps.querySelector('[data-step="before"]')).not.toBeNull();
	},
};

/** Started from a piece of feedback, with system info: its delivery, its times and the developer's answer under it. */
export const StartedOnFeedback: Story = {
	args: {
		here: { kind: "feedback", id: "44444444-4444-4444-4444-444444444444" },
		display: { ...DEFAULT_TRACE_DISPLAY, system: true },
	},
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(panel.querySelector("[data-step-facts]")).toHaveTextContent(/Composed/u);
	},
};

/** Only what was lost on the way: the filter lists every step it keeps, so Siblings is held. */
export const OnlyLosses: Story = {
	args: { filter: { kind: "losses" } },
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(showToggle(panel, "Siblings")).toBeDisabled();
	},
};

/**
 * An observation opened from the trace: its sheet over the trace, the same as from its row, with no
 * story and no way on. Escape closes it back to the trace.
 */
export const RecordOverTheTrace: Story = {
	play: async ({ userEvent }) => {
		const panel = within(await settledDrawerPanel());
		await userEvent.click(
			panel.getByRole("button", {
				name: /A cache miss and a permission failure come back as the same 404/u,
			}),
		);
		const over = await screen.findByRole("dialog", {
			name: "A cache miss and a permission failure come back as the same 404",
		});
		await expect(within(over).queryByRole("group", { name: "How this came about" })).toBeNull();
		await expect(over.querySelector("[data-trace-verdict]")).toBeNull();
		await userEvent.keyboard("{Escape}");
		await waitFor(async () => {
			await expect(screen.getAllByRole("dialog")).toHaveLength(1);
		});
	},
};

/** The merge came while the review ran: a state from the matrix, told as a trace. */
export const MergedWhileRunning: Story = {
	// The state's own times: the merge falls while it ran only on them.
	args: {
		chain: { status: "ready", lineage: lineageStateCase("merged-during").lineage },
		job: undefined,
	},
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(panel.querySelector('[data-step="merged"]')).not.toBeNull();
	},
};

/** A piece of feedback the developer answered: another state from the matrix. */
export const DeveloperAnswered: Story = {
	args: {
		chain: { status: "ready", lineage: lineageStateCase("responses").lineage },
		display: FULL_TRACE_DISPLAY,
		job: undefined,
	},
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(panel.querySelector('[data-step="end"]')).not.toBeNull();
	},
};

/** Work none of whose moments started a review: its latest moment, and why no review started. */
export const NoReviewStarted: Story = {
	args: {
		title: "Define the practice-binding contract",
		chain: { status: "unreviewed", work: tracedArtifact(1430) },
		here: { kind: "work" },
		workRecord: { kind: "work", id: "issue:1430" },
	},
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(panel.querySelector("[data-trace-verdict]")).toHaveTextContent(
			/^No review started on this work/u,
		);
	},
};

/** While the chain loads, the sheet holds its shape. */
export const Loading: Story = {
	args: { chain: { status: "loading" } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.queryByRole("group", { name: "How this came about" })).toBeNull();
	},
};
