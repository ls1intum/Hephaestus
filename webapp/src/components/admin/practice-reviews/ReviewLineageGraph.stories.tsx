import type { Meta, StoryObj } from "@storybook/react-vite";
import { expect, fn, screen, waitFor, within } from "storybook/test";

import type { ReviewLineage } from "@/api/types.gen";

import { expectSettledVisible } from "@/stories/overlay";

import {
	LINEAGE_FEEDBACK,
	LINEAGE_RESULT,
	LINEAGE_REVIEW_ID,
	lineageFeedbackFocus,
	lineageResultFocus,
	wholeReview,
	wholeReviewManyFeedback,
} from "./lineage-fixtures";

import { lineageStateCase, STATE_FEEDBACK_ID } from "./lineage-state-fixtures";
import { connectorBox, LANE_PX } from "./LineageConnectors";
import type { TraceNode } from "./review-trace";
import { ReviewLineageGraph } from "./ReviewLineageGraph";
import { DEFAULT_TRACE_DISPLAY, FULL_TRACE_DISPLAY } from "./trace-display";

const ready = (value: ReviewLineage) => ({ status: "ready" as const, lineage: value });

/**
 * The trace of a record, top to bottom in the order things happened: the work and the event that
 * started the review, the review, its results as numbered verdict dots, each piece of feedback
 * with where it went and how the developer answered, the merge where it fell, and where the path
 * ends. The record the trace was opened for is "You started here", with a ring and, with system
 * info, its facts; its siblings fold into one line. A press on a step opens its record's sheet over
 * the trace, and the mark stays where the reader started.
 */
const meta = {
	component: ReviewLineageGraph,
	tags: ["autodocs"],
	decorators: [
		(Story) => (
			<div className="w-[34rem] max-w-full p-4">
				<Story />
			</div>
		),
	],
	args: {
		lineage: ready(lineageResultFocus),
		here: { kind: "result", id: LINEAGE_RESULT.cache404 },
		onOpen: fn(),
	},
} satisfies Meta<typeof ReviewLineageGraph>;

export default meta;
type Story = StoryObj<typeof meta>;

/** One of the "Show" control's toggles. */
function showToggle(root: HTMLElement, name: string): HTMLElement {
	return within(within(root).getByRole("group", { name: "Show" })).getByRole("button", { name });
}

function stepsOf(root: HTMLElement): HTMLElement {
	return within(root).getByRole("list", { name: "Steps, oldest first" });
}

function hereOf(root: HTMLElement): HTMLElement | null {
	return root.querySelector<HTMLElement>('[data-here="true"]');
}

function verdictOf(root: HTMLElement): HTMLElement | null {
	return root.querySelector<HTMLElement>("[data-trace-verdict]");
}

function endOf(root: HTMLElement): HTMLElement | null {
	return root.querySelector<HTMLElement>('[data-step="end"]');
}

/**
 * A result's story as it opens: a sentence on top says what became of it; the rail reads
 * Started by, Review, Feedback, Effect, and tells only the result's path, from the review through
 * the result ("You started here") to the feedback written from it and how it ended. The review's
 * other records are one line.
 */
export const ResultStory: Story = {
	play: async ({ canvas, canvasElement }) => {
		await expect(verdictOf(canvasElement)).toHaveTextContent(
			"This result led to 1 piece of feedback; it was delivered and answered.",
		);
		await expect(hereOf(canvasElement)).toHaveAttribute(
			"data-lineage-node",
			`result:${LINEAGE_RESULT.cache404}`,
		);
		const stages = [...stepsOf(canvasElement).querySelectorAll("[data-stage-label]")];
		await expect(stages.map((stage) => stage.textContent)).toStrictEqual([
			"Started by",
			"Review",
			"Results",
			"Feedback",
			"Effect",
		]);
		await expect(canvas.getByText("Why it ran: Work opened")).toBeVisible();
		await expect(stepsOf(canvasElement).querySelectorAll('[data-step="feedback"]')).toHaveLength(1);
		await expect(
			canvas.getByRole("button", { name: "4 other results, 2 other pieces of feedback" }),
		).toBeVisible();
		await expect(endOf(canvasElement)).toHaveTextContent(
			"the developer answered it; how stays private to them.",
		);
		// The trace links nowhere: a step opens its record over it.
		await expect(within(stepsOf(canvasElement)).queryAllByRole("link")).toHaveLength(0);
	},
};

/**
 * The line of other records lists them on the rail at their own times, and the verdict and the end
 * still speak of the result. The Siblings toggle stays the reader's choice for every trace.
 */
export const ResultStorySiblingsShown: Story = {
	play: async ({ canvas, canvasElement, userEvent }) => {
		await userEvent.click(
			canvas.getByRole("button", { name: "4 other results, 2 other pieces of feedback" }),
		);
		await expect(showToggle(canvasElement, "Siblings")).toHaveAttribute("aria-pressed", "false");
		await expect(stepsOf(canvasElement).querySelectorAll('[data-step="feedback"]')).toHaveLength(3);
		await expect(canvas.getByText("Withheld: near duplicate")).toBeVisible();
		await expect(endOf(canvasElement)).toHaveTextContent(
			"the developer answered it; how stays private to them.",
		);
	},
};

/**
 * "Legend" beside the Show control opens the legend over the trace, so the rail stays where it is:
 * each mark the trace draws, grouped by what it marks, the icons, and what the trace cannot show.
 */
export const LegendOpen: Story = {
	play: async ({ canvas, canvasElement, userEvent }) => {
		const rail = stepsOf(canvasElement);
		const { top } = rail.getBoundingClientRect();
		await userEvent.click(canvas.getByRole("button", { name: "Legend" }));
		const legend = within(await screen.findByRole("region", { name: "Legend" }));
		// The popover fades in, so its contents are asserted once it has settled.
		await expectSettledVisible(legend.getByRole("heading", { name: "How to read the trace" }));
		await expect(legend.getByRole("heading", { name: "What the trace cannot show" })).toBeVisible();
		await expect(legend.getByRole("button", { name: "coalesced" })).toBeVisible();
		await expect(rail.getBoundingClientRect().top).toBe(top);
	},
};

/**
 * Connections draw their lanes right of the steps, so turning them on moves no step sideways: the
 * words only wrap a little earlier.
 */
export const ConnectionsKeepTheSteps: Story = {
	args: { lineage: ready(wholeReview), here: undefined },
	play: async ({ canvasElement, userEvent }) => {
		const content = () =>
			stepsOf(canvasElement)
				.querySelector<HTMLElement>("[data-step-content]")
				?.getBoundingClientRect().left;
		const before = content();
		await userEvent.click(showToggle(canvasElement, "Connections"));
		await waitFor(async () => {
			await expect(
				canvasElement.querySelectorAll("path[data-connector-line]").length,
			).toBeGreaterThan(0);
		});
		await expect(content()).toBe(before);
	},
};

/** A card's story: it rests on three results, each repeated as its dot under the card. */
export const FeedbackStory: Story = {
	args: {
		lineage: ready(lineageFeedbackFocus),
		here: { kind: "feedback", id: LINEAGE_FEEDBACK.onGitHub },
	},
	play: async ({ canvasElement }) => {
		const here = hereOf(canvasElement);
		await expect(here).toHaveAttribute(
			"data-lineage-node",
			`feedback:${LINEAGE_FEEDBACK.onGitHub}`,
		);
		await expect(here?.querySelectorAll("[data-result-chip]")).toHaveLength(3);
		await expect(here).toHaveAttribute("data-here", "true");
	},
};

/** A piece of work's story: the work is "You started here", and its newest review tells the rest. */
export const WorkStory: Story = {
	args: { lineage: ready(wholeReview), here: { kind: "work" } },
	play: async ({ canvasElement }) => {
		await expect(hereOf(canvasElement)).toHaveAttribute("data-lineage-node", "work");
		// The earlier review's comment came before this review and folds into one line.
		await expect(stepsOf(canvasElement).querySelectorAll('[data-step="feedback"]')).toHaveLength(5);
		await expect(stepsOf(canvasElement).querySelector('[data-step="before"]')).not.toBeNull();
	},
};

/**
 * Every part shown: every result as a row of the review's table, each piece of feedback with every fact and its
 * time, and connectors from each result to the feedback written from it. "Expand all" unfolds
 * what came before the review: the earlier review's comment this review replaced.
 */
export const AllShown: Story = {
	args: { lineage: ready(wholeReview), here: undefined, defaultDisplay: FULL_TRACE_DISPLAY },
	play: async ({ canvas, canvasElement, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Expand all" }));
		await expect(verdictOf(canvasElement)).toHaveTextContent(
			"This review ran 1 h 27 min after a 20 s wait and composed 5 pieces of feedback; 3 reached the developer.",
		);
		// Eight edges: 2 + 3 + 1 + 1 + 1; the earlier review's comment rests on nothing listed here.
		await waitFor(async () => {
			await expect(canvasElement.querySelectorAll("path[data-connector-line]")).toHaveLength(8);
		});
		await expect(canvas.getByRole("list", { name: "Results of this review" })).toBeVisible();
		// Said once, in the facts "Expand all" opens under the piece, not again on its line.
		await expect(canvas.getAllByText("Composed by")[0]).toBeVisible();
		await expect(canvas.queryByText("Composed by an earlier review")).toBeNull();
	},
};

/**
 * The same review as it opens: what came before it folds into one line, and "Show" unfolds it.
 */
export const BeforeThisReview: Story = {
	args: { lineage: ready(wholeReview), here: undefined },
	play: async ({ canvas, canvasElement }) => {
		const before = stepsOf(canvasElement).querySelector<HTMLElement>('[data-step="before"]');
		await expect(before).toHaveTextContent(
			"Before this review: 1 piece of feedback from an earlier review.",
		);
		await expect(canvas.queryByText("Composed by an earlier review")).toBeNull();
	},
};

/** A delivered piece the developer answered, privately, and their work later resolved. */
export const DeliveredHelpful: Story = {
	args: { lineage: ready(wholeReview), here: { kind: "feedback", id: LINEAGE_FEEDBACK.onGitHub } },
	play: async ({ canvasElement }) => {
		await expect(verdictOf(canvasElement)).toHaveTextContent(
			"This feedback reached the developer on their practice pages 18 h 32 min after it was composed, and their work resolved it 8 d 3 h later.",
		);
		await expect(endOf(canvasElement)).toHaveAttribute("data-end", "worked");
	},
};

/**
 * The review's results as a table of their own, opened and folded on its own button: the
 * Siblings toggle is no part of it. Each row names its practice, outcome, severity and the
 * feedback written from it by letter.
 */
export const ResultTable: Story = {
	args: { lineage: ready(wholeReview), here: { kind: "review" } },
	play: async ({ canvas, canvasElement, userEvent }) => {
		const table = () => canvasElement.querySelector('[data-trace-table="results"]');
		await expect(table()).toBeNull();
		await userEvent.click(canvas.getByRole("button", { name: "Show all 8 results in the table" }));
		await expect(showToggle(canvasElement, "Siblings")).toHaveAttribute("aria-pressed", "false");
		const rows = within(canvas.getByRole("list", { name: "Results of this review" }));
		await expect(rows.getAllByRole("listitem")).toHaveLength(8);
		await expect(
			table()?.querySelector(`[data-lineage-node="result:${LINEAGE_RESULT.cache404}"]`),
		).toHaveTextContent(/Not met.*Major.*B, D, F/u);
		await userEvent.click(canvas.getByRole("button", { name: "Fold the table" }));
		await expect(table()).toBeNull();
	},
};

/**
 * The feedback as tables in the rail: pieces written one after another share one table. Each row has
 * its letter, destination, state, the results it was written from by number, and its time.
 */
export const FeedbackTable: Story = {
	args: { lineage: ready(wholeReview), here: { kind: "review" } },
	play: async ({ canvasElement }) => {
		const tables = [...canvasElement.querySelectorAll('[data-trace-table="feedback"]')];
		await expect(tables).toHaveLength(2);
		const merged = stepsOf(canvasElement).querySelector('[data-step="merged"]');
		await expect(
			merged !== null &&
				tables[1] !== undefined &&
				merged.compareDocumentPosition(tables[1]) === Node.DOCUMENT_POSITION_FOLLOWING,
		).toBe(true);
		const card = canvasElement.querySelector(`[data-feedback-row="${LINEAGE_FEEDBACK.onGitHub}"]`);
		await expect(card).toHaveTextContent("On their practice pages");
		await expect(card?.querySelectorAll("[data-result-chip]")).toHaveLength(3);
	},
};

/** On a phone, the tables stack their rows: no column runs off the sheet. */
export const NarrowSheetStacks: Story = {
	args: { lineage: ready(wholeReview), here: { kind: "review" } },
	decorators: [
		(Story) => (
			<div className="w-[22rem] max-w-full">
				<Story />
			</div>
		),
	],
	play: async ({ canvas, canvasElement, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Expand all" }));
		for (const table of canvasElement.querySelectorAll<HTMLElement>("[data-trace-table]")) {
			await expect(table.scrollWidth).toBeLessThanOrEqual(table.clientWidth + 1);
		}
		await expect(canvasElement.scrollWidth).toBeLessThanOrEqual(canvasElement.clientWidth + 1);
	},
};

/** While the chain loads, the rail in the shape it lands in. */
export const Loading: Story = {
	args: { lineage: { status: "loading" } },
	play: async ({ canvas }) => {
		await expect(canvas.queryByRole("group", { name: "How this came about" })).toBeNull();
	},
};

// ------------------------------------------------------------------------------------------------
// Where the reader started
// ------------------------------------------------------------------------------------------------

/** The step marked "You started here": its control is current. */
function startedAt(root: HTMLElement): HTMLElement | null {
	return root.querySelector<HTMLElement>('[data-step-key]:has(> [aria-current="step"])');
}

/**
 * With system info, the trace opens on the record it was opened for, ringed and marked "You started
 * here", with its facts under it. There is no walk through the chain: no previous or next step, and
 * the arrow keys move nothing.
 */
export const StartedHere: Story = {
	args: { defaultDisplay: { ...DEFAULT_TRACE_DISPLAY, system: true } },
	play: async ({ canvasElement, userEvent }) => {
		const start = `result:${LINEAGE_RESULT.cache404}`;
		await expect(startedAt(canvasElement)).toHaveAttribute("data-step-key", start);
		await expect(hereOf(canvasElement)).toHaveAttribute("data-step-key", start);
		await expect(
			within(hereOf(canvasElement) ?? canvasElement).getByText("You started here"),
		).toBeVisible();
		await expect(startedAt(canvasElement)?.querySelector("[data-step-facts]")).toHaveTextContent(
			/Recorded/u,
		);
		await userEvent.keyboard("{ArrowDown}");
		await expect(startedAt(canvasElement)).toHaveAttribute("data-step-key", start);
		await expect(canvasElement.querySelectorAll("[data-step-facts]")).toHaveLength(1);
	},
};

/**
 * A press on a step opens that record's sheet over the trace. The ring and "You started here" stay
 * on the record the trace was opened for.
 */
export const OpenAStep: Story = {
	play: async ({ args, canvasElement, userEvent }) => {
		const feedback = stepsOf(canvasElement).querySelector<HTMLElement>(
			'[data-step="feedback"] [data-step-control]',
		);
		if (feedback === null) {
			throw new Error("The story has a piece of feedback");
		}
		await userEvent.click(feedback);
		await expect(args.onOpen).toHaveBeenCalledWith(expect.objectContaining({ kind: "feedback" }));
		await expect(startedAt(canvasElement)).toHaveAttribute(
			"data-step-key",
			`result:${LINEAGE_RESULT.cache404}`,
		);
		// The work and its events open the work's sheet; the review its own.
		const review = stepsOf(canvasElement).querySelector<HTMLElement>('[data-step="review"]');
		await userEvent.click(within(review ?? canvasElement).getByRole("button"));
		await expect(args.onOpen).toHaveBeenLastCalledWith({ kind: "review", id: LINEAGE_REVIEW_ID });
		await expect(startedAt(canvasElement)).toHaveAttribute(
			"data-step-key",
			`result:${LINEAGE_RESULT.cache404}`,
		);
	},
};

/** Only feedback: the results leave the rail, every piece of feedback stays. */
export const OnlyFeedback: Story = {
	args: { lineage: ready(wholeReview), here: { kind: "review" }, filter: { kind: "feedback" } },
	play: async ({ canvasElement }) => {
		await expect(showToggle(canvasElement, "Siblings")).toBeDisabled();
		await expect(
			stepsOf(canvasElement).querySelectorAll('[data-lineage-node^="result:"]'),
		).toHaveLength(0);
		await expect(
			stepsOf(canvasElement).querySelectorAll('[data-step="feedback"]').length,
		).toBeGreaterThan(0);
	},
};

/** Only what was lost: the feedback that never reached the developer, and nothing that arrived. */
export const OnlyLosses: Story = {
	args: { lineage: ready(wholeReview), here: { kind: "review" }, filter: { kind: "losses" } },
	play: async ({ canvasElement }) => {
		const pieces = [...stepsOf(canvasElement).querySelectorAll('[data-step="feedback"]')];
		for (const piece of pieces) {
			await expect(piece).not.toHaveTextContent(/^Delivered/u);
		}
		// The verdict still speaks of the whole review.
		await expect(verdictOf(canvasElement)).toHaveTextContent(/composed 5 pieces of feedback/u);
	},
};

/** One practice: its results and the feedback written from them. */
export const OnlyOnePractice: Story = {
	args: {
		lineage: ready(wholeReview),
		here: { kind: "review" },
		filter: { kind: "practice", practiceSlug: "errors-carry-context" },
	},
	play: async ({ canvasElement }) => {
		const results = [...stepsOf(canvasElement).querySelectorAll('[data-lineage-node^="result:"]')];
		await expect(results.length).toBeGreaterThan(0);
	},
};

// ------------------------------------------------------------------------------------------------
// Connections
// ------------------------------------------------------------------------------------------------

/** The many-feedback review, with cards of every height: one summary wraps over several lines. */
const mixedHeights: ReviewLineage = {
	...wholeReviewManyFeedback,
	nodes: wholeReviewManyFeedback.nodes.map((node) =>
		node.result?.id === LINEAGE_RESULT.testNames
			? {
					...node,
					result: {
						...node.result,
						summary:
							"Three of the new tests are named after the method they call, not the behaviour they check. A failing run says which method broke, not what a reader expected. The names drift as the methods are renamed.",
					},
				}
			: node,
	),
};

/** A frame whose width a story changes, to see the lines follow the cards. */
function frameOf(root: HTMLElement): HTMLElement {
	const frame = root.querySelector<HTMLElement>("[data-connector-frame]");
	if (frame === null) {
		throw new Error("The story has a frame");
	}
	return frame;
}

/** Where a connector starts and ends, in the drawing's own coordinates. */
function endsOf(path: SVGPathElement) {
	const start = path.getPointAtLength(0);
	const end = path.getPointAtLength(path.getTotalLength());
	return { start, end };
}

/** The card a line must meet: the box around the chip that marks its end. */
function cardOf(root: HTMLElement, selector: string): DOMRect {
	const chip = root.querySelector(selector);
	if (chip === null) {
		throw new Error(`No chip for ${selector}`);
	}
	return connectorBox(chip).getBoundingClientRect();
}

/**
 * Every line starts at the vertical centre of its result's card and ends at the vertical
 * centre of its piece's card, within a pixel, each on the card's right edge; the lanes sit a whole
 * number of lanes apart.
 */
async function expectConnectorsCentred(root: HTMLElement) {
	const paths = [...root.querySelectorAll<SVGPathElement>("path[data-connector-line]")];
	await expect(paths.length).toBeGreaterThan(0);
	const drawing = paths[0]?.ownerSVGElement?.getBoundingClientRect();
	if (drawing === undefined) {
		throw new Error("The lines are drawn in an svg");
	}
	const lanes = new Set<number>();
	for (const path of paths) {
		const result = CSS.escape(path.dataset.fromResult ?? "");
		const feedback = CSS.escape(path.dataset.toFeedback ?? "");
		const source = cardOf(root, `[data-connector="source"][data-result-id="${result}"]`);
		const target = cardOf(root, `[data-connector="use:${feedback}"][data-result-id="${result}"]`);
		const { start, end } = endsOf(path);
		await expect(Math.abs(start.y - (source.top - drawing.top + source.height / 2))).toBeLessThan(
			1,
		);
		await expect(Math.abs(end.y - (target.top - drawing.top + target.height / 2))).toBeLessThan(1);
		await expect(start.x).toBeLessThanOrEqual(source.right - drawing.left + 1);
		await expect(end.x).toBeLessThanOrEqual(target.right - drawing.left + 1);
		lanes.add(Number(path.dataset.laneX));
	}
	const sorted = [...lanes].sort((a, b) => a - b);
	for (const [index, x] of sorted.entries()) {
		const first = sorted[0] ?? x;
		await expect(Math.abs((x - first) / LANE_PX - index)).toBeLessThan(0.01);
	}
}

/** Waits for the lines to be redrawn after a change, then checks every end meets its card's centre. */
async function expectCentredSoon(root: HTMLElement) {
	await waitFor(async () => {
		await expectConnectorsCentred(root);
	});
}

/**
 * Connections over cards of every height: a summary that wraps over several lines, tags, the facts
 * under every step, siblings listed. Each line leaves its result's card at its vertical centre
 * and meets each piece's card at its vertical centre, and still does after each "Show" toggle,
 * "Expand all", and a narrower sheet that wraps the words again.
 */
export const ConnectionsMeetEveryCardInTheMiddle: Story = {
	args: { lineage: ready(mixedHeights), here: undefined, defaultDisplay: FULL_TRACE_DISPLAY },
	decorators: [
		(Story) => (
			<div data-connector-frame className="w-(--frame) max-w-full" style={{ "--frame": "34rem" }}>
				<Story />
			</div>
		),
	],
	play: async ({ canvas, canvasElement, userEvent }) => {
		await expectCentredSoon(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: "Expand all" }));
		await expectCentredSoon(canvasElement);
		await userEvent.click(showToggle(canvasElement, "Times"));
		await expectCentredSoon(canvasElement);
		await userEvent.click(showToggle(canvasElement, "System info"));
		await expectCentredSoon(canvasElement);
		await userEvent.click(canvas.getByRole("button", { name: "Collapse all" }));
		await expectCentredSoon(canvasElement);
		frameOf(canvasElement).style.setProperty("--frame", "24rem");
		await expectCentredSoon(canvasElement);
		await userEvent.click(showToggle(canvasElement, "System info"));
		await userEvent.click(showToggle(canvasElement, "Times"));
		await expectCentredSoon(canvasElement);
	},
};

/**
 * Twelve pieces from eight results on a narrow sheet: one lane each, evenly spaced, the
 * shortest run nearest the cards, and every end at a card's centre.
 */
export const ConnectionsManyLines: Story = {
	args: {
		lineage: ready(wholeReviewManyFeedback),
		here: { kind: "review" },
		defaultDisplay: { ...DEFAULT_TRACE_DISPLAY, siblings: true, connections: true },
	},
	decorators: [
		(Story) => (
			<div data-connector-frame className="w-(--frame) max-w-full" style={{ "--frame": "28rem" }}>
				<Story />
			</div>
		),
	],
	play: async ({ canvasElement }) => {
		await expectCentredSoon(canvasElement);
	},
};

// ------------------------------------------------------------------------------------------------
// Where it was lost, and where the time went
// ------------------------------------------------------------------------------------------------

function bottleneckOf(root: HTMLElement): HTMLElement | null {
	return root.querySelector<HTMLElement>("[data-trace-bottleneck]");
}

const STATE_PIECE: TraceNode = { kind: "feedback", id: STATE_FEEDBACK_ID };

function stateCaseArgs(key: string, here?: TraceNode) {
	const entry = lineageStateCase(key);
	return { lineage: ready(entry.lineage), here: here ?? STATE_PIECE, review: entry.review };
}

/** A failed review: the line on top says the review stopped, and the review's stage is marked. */
export const BottleneckReviewFailed: Story = {
	args: stateCaseArgs("failed", { kind: "review" }),
	play: async ({ canvasElement }) => {
		await expect(bottleneckOf(canvasElement)).toHaveTextContent(/^Review failed/u);
		await expect(canvasElement.querySelector('[data-stage="review"]')).toHaveTextContent(
			"Stopped here",
		);
	},
};

/** At phone width the line and its strip wrap, and nothing scrolls sideways. */
export const BottleneckNarrow: Story = {
	args: stateCaseArgs("failed"),
	parameters: { chromatic: { viewports: [390] } },
	decorators: [
		(Story) => (
			<div className="w-[22rem] max-w-full">
				<Story />
			</div>
		),
	],
	play: async ({ canvas, canvasElement, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Expand all" }));
		await expect(canvasElement.scrollWidth).toBeLessThanOrEqual(canvasElement.clientWidth + 1);
	},
};

/**
 * "Expand all" opens a box only where a step has more to say than its line, and no two boxes say
 * the same: the start, the count of results and the end open none.
 */
export const ExpandAllSaysEachThingOnce: Story = {
	args: { lineage: ready(wholeReview), here: { kind: "review" } },
	play: async ({ canvas, canvasElement, userEvent }) => {
		await userEvent.click(canvas.getByRole("button", { name: "Expand all" }));
		const boxes = [...canvasElement.querySelectorAll<HTMLElement>("[data-step-facts]")];
		await expect(boxes.length).toBeGreaterThan(0);
		const texts = boxes.map((box) => box.textContent);
		await expect(new Set(texts).size).toBe(texts.length);
		for (const step of [
			'[data-step="start"]',
			'[data-step="end"]',
			'[data-step="results"] > [data-step-content] > [data-step-key="results"]',
		]) {
			const node = canvasElement.querySelector(step);
			await expect(node?.querySelector(":scope > [data-step-facts]") ?? null).toBeNull();
		}
	},
};
