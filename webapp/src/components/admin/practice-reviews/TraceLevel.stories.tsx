import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import { DetailDrawerStack } from "@/components/layout/detail-drawer/DetailDrawerStack";
import { eventLabel, untouchedItemTrace } from "@/components/practice-trace/fixtures";
import { withPageBehind } from "@/stories/decorators";
import { settledDrawerPanel } from "@/stories/overlay";

import { FeedbackLevel } from "./FeedbackLevel";
import { resultDetail, reviewDetail, reviewFeedbackDetail } from "./fixtures";
import {
	LINEAGE_FEEDBACK,
	LINEAGE_RESULT,
	LINEAGE_REVIEW_ID,
	wholeReview,
} from "./lineage-fixtures";
import { lineageStateCase } from "./lineage-state-fixtures";
import { ResultLevel } from "./ResultLevel";
import { levelRecord, type RecordRef, recordLevel, traceLevel } from "./review-layers";
import type { PracticeReviewLevel } from "./review-levels";
import { DEFAULT_TRACE_DISPLAY, FULL_TRACE_DISPLAY, type TraceDisplay } from "./trace-display";
import { ALL_STEPS, type TraceFilter } from "./trace-filter";
import { TraceLevel, type TraceLevelProps } from "./TraceLevel";

const TRACE_ENTRY = traceLevel({ kind: "review", id: LINEAGE_REVIEW_ID });

const [untouchedEvent] = untouchedItemTrace.events;
if (untouchedEvent === undefined) {
	throw new Error("The untouched item carries its event");
}
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
					<ResultLevel
						nested={level.nested}
						path={path}
						result={{ status: "ready", result: resultDetail(LINEAGE_RESULT.cache404) }}
						onMark={fn(async () => undefined)}
						onRemoveMark={fn(async () => undefined)}
						isMarking={false}
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
		review: reviewDetail(LINEAGE_REVIEW_ID),
	},
	argTypes: { path: { control: false }, chain: { control: false }, review: { control: false } },
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
	},
};

export const AllOn: Story = {
	args: { display: FULL_TRACE_DISPLAY },
	play: async () => {
		const panel = await settledDrawerPanel();
		for (const name of ["Times", "System info", "Siblings", "Connections"]) {
			await expect(showToggle(panel, name)).toHaveAttribute("aria-pressed", "true");
		}
		await expect(panel.querySelector("[data-step-facts]")).toHaveTextContent(/Asked for/u);
		await expect(panel.querySelector("[data-step-facts]")).toHaveTextContent(
			/openai\/gpt-oss-120b/u,
		);
		await waitFor(async () => {
			await expect(panel.querySelectorAll("path[data-connector-line]").length).toBeGreaterThan(0);
		});
	},
};

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
	},
};

export const StartedOnFeedback: Story = {
	args: {
		here: { kind: "feedback", id: LINEAGE_FEEDBACK.praise },
		display: { ...DEFAULT_TRACE_DISPLAY, system: true },
	},
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(panel.querySelector("[data-step-facts]")).toHaveTextContent(/Destination/u);
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

export const ScanWithManyJobs: Story = {
	args: {
		chain: { status: "ready", lineage: lineageStateCase("scan-many-jobs").lineage },
		review: lineageStateCase("scan-many-jobs").review,
	},
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(panel.querySelector('[data-step="end"]')).not.toBeNull();
	},
};

export const EveryDeliveryState: Story = {
	args: {
		chain: { status: "ready", lineage: lineageStateCase("delivery-states").lineage },
		display: FULL_TRACE_DISPLAY,
		review: undefined,
	},
	play: async () => {
		const panel = await settledDrawerPanel();
		await expect(panel.querySelector('[data-step="end"]')).not.toBeNull();
	},
};

export const NoReviewStarted: Story = {
	args: {
		title: "Define the practice-binding contract",
		chain: {
			status: "unreviewed",
			item: untouchedItemTrace.item,
			event: untouchedEvent,
			eventLabel: eventLabel(untouchedEvent.name),
		},
		here: { kind: "work" },
		workRecord: { kind: "item", id: untouchedItemTrace.item.id },
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

export const Empty: Story = {
	args: { chain: { status: "none" } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		panel.getByText("Nothing was recorded about this record, so there is no trace to show.");
	},
};

export const LoadError: Story = {
	args: { chain: { status: "error", error: { status: 500 }, onRetry: fn() } },
	play: async () => {
		const panel = within(await settledDrawerPanel());
		await expect(panel.getByRole("button", { name: "Retry" })).toBeVisible();
	},
};
