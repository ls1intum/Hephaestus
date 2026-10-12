import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import type { AgentJob } from "@/api/types.gen";

import { mockJobCompleted } from "./fixtures";
import { isSlowGap, type NumberedObservation } from "./lineage-chain";
import {
	LINEAGE_FB,
	LINEAGE_REVIEW_ID,
	WHOLE_OBS,
	wholeReview,
	wholeReviewStartedByMerge,
} from "./lineage-fixtures";
import { storyObservations, storyRail } from "./lineage-story";
import type { TraceNode } from "./review-trace";
import { ReviewLineageGraph } from "./ReviewLineageGraph";
import { DEFAULT_TRACE_DISPLAY } from "./trace-display";
import { filteredLineage, type TraceFilter } from "./trace-filter";
import { traceVerdict } from "./trace-verdict";

/**
 * The rail tells only what is true of the review, whatever the reader filters or stands on: its
 * times run forward, a filter hides but never renumbers or denies, and the mark of where the reader
 * started never moves.
 */

afterEach(cleanup);

function renderTrace(
	options: {
		here?: TraceNode;
		filter?: TraceFilter;
		job?: AgentJob;
		lineage?: typeof wholeReview;
	} = {},
) {
	return render(
		<ReviewLineageGraph
			lineage={{ status: "ready", lineage: options.lineage ?? wholeReview }}
			here={options.here ?? { kind: "review" }}
			defaultDisplay={DEFAULT_TRACE_DISPLAY}
			filter={options.filter}
			job={options.job}
			onOpen={() => undefined}
		/>,
	);
}

function railText(container: HTMLElement): string {
	return within(container).getByRole("list", { name: "Steps, oldest first" }).textContent;
}

/** Every time the rail draws a step at, top to bottom. */
function railTimes(): number[] {
	return storyRail(wholeReview, wholeReview.feedback)
		.flatMap((stage) => stage.items)
		.flatMap((item) => ("at" in item && item.at instanceof Date ? [item.at.getTime()] : []));
}

/** The kinds of moment the feedback stage holds. */
function momentsAmongFeedback(): string[] {
	return storyRail(wholeReview, wholeReview.feedback)
		.filter((stage) => stage.stage === "feedback")
		.flatMap((stage) => stage.items)
		.flatMap((item) => (item.kind === "moment" ? [item.moment.kind] : []));
}

function numberOf(list: readonly NumberedObservation[]): number | undefined {
	return list.find((entry) => entry.observation.id === WHOLE_OBS.migration)?.number;
}

describe("the rail's order", () => {
	it("runs forward in time across its stages", () => {
		const times = railTimes();
		expect(times).toStrictEqual([...times].sort((a, b) => a - b));
	});

	it("puts a moment folded into the review after its first feedback among the feedback", () => {
		expect(momentsAmongFeedback()).toContain("PUSHED");
	});
});

/** Each observation's number in the story of a chain, with its id. */
function numbersOf(lineage: typeof wholeReview) {
	return storyObservations(lineage, undefined).numbered.map(
		({ observation, number }) => `${number}:${observation.id}`,
	);
}

/** Whether the dot of the step marked `step` is drawn hollow. */
function hollowDot(container: HTMLElement, step: string): boolean {
	const dot = container.querySelector(`[data-step="${step}"] > .rounded-full`);
	if (dot === null) {
		throw new Error(`No dot on the ${step} step`);
	}
	return dot.classList.contains("bg-background");
}

describe("the rail's dots", () => {
	it("draw the work and a plain merge hollow, so neither reads as open", () => {
		const { container } = renderTrace({ lineage: wholeReviewStartedByMerge });
		expect(hollowDot(container, "work")).toBe(true);
		expect(hollowDot(container, "merged")).toBe(true);
	});

	it("fill a merge that has something to judge, as one before the review was done", () => {
		const { container } = renderTrace();
		expect(hollowDot(container, "merged")).toBe(false);
	});
});

describe("the observations' numbers", () => {
	it("stay the same in every trace of one review, whichever record it was opened from", () => {
		const whole = numbersOf(wholeReview);
		// A trace opened from another record marks other observations as its focus, and the server
		// may list them in another order.
		const fromAnother = {
			...wholeReview,
			observations: [...wholeReview.observations]
				.reverse()
				.map((entry, index) => ({ ...entry, focus: index === 0 })),
		};
		expect(numbersOf(fromAnother)).toStrictEqual(whole);
	});
});

/** A moment that started the review or joined it. */
const startsOrJoins = (moment: { startedReview?: boolean; joinedReview?: boolean }) =>
	moment.startedReview === true || moment.joinedReview === true;

describe("a filter", () => {
	it("keeps each observation's number", () => {
		const whole = storyObservations(wholeReview, undefined).numbered;
		const narrowed = filteredLineage(wholeReview, {
			kind: "practice",
			practiceSlug: "workspace-scoped-queries",
		});
		const kept = storyObservations(narrowed, undefined, wholeReview).numbered;
		expect(kept.length).toBeLessThan(whole.length);
		expect(numberOf(kept)).toBeDefined();
		expect(numberOf(kept)).toBe(numberOf(whole));
	});

	it("keeps only the moments that started or joined the review, as every filter does", () => {
		const narrowed = filteredLineage(wholeReview, {
			kind: "practice",
			practiceSlug: "workspace-scoped-queries",
		});
		expect(narrowed.workMoments.length).toBeLessThan(wholeReview.workMoments.length);
		expect(narrowed.workMoments).toStrictEqual(wholeReview.workMoments.filter(startsOrJoins));
	});

	it("counts as lost only feedback whose path ends lost", () => {
		const lost = filteredLineage(wholeReview, { kind: "losses" }).feedback.map((entry) => entry.id);
		expect(lost).toContain(LINEAGE_FB.afterMerge);
		expect(lost).toContain(LINEAGE_FB.earlier);
		expect(lost).not.toContain(LINEAGE_FB.card);
	});

	it("does not say the review recorded no observation under Only feedback", () => {
		const { container } = renderTrace({ filter: { kind: "feedback" } });
		const text = railText(container);
		expect(text).not.toMatch(/recorded no observation/u);
		expect(text).toMatch(/It recorded/u);
		expect(text).toMatch(/Hidden by the filter/u);
	});

	it("says nothing was lost on the path rather than that the review wrote no feedback", () => {
		const { container } = renderTrace({
			filter: { kind: "practice", practiceSlug: "workspace-scoped-queries" },
		});
		expect(railText(container)).not.toMatch(/No feedback from this review/u);
	});
});

describe("an observation with no feedback of its own", () => {
	it("does not deny the review's other feedback", () => {
		const { container } = renderTrace({ here: { kind: "observation", id: WHOLE_OBS.migration } });
		const text = railText(container);
		expect(text).not.toMatch(/No feedback from this review/u);
		expect(text).toMatch(/other pieces of feedback/u);
	});
});

describe("where the reader started", () => {
	it("stays put when the toolbar's arrow keys move between the Show toggles", () => {
		const { container } = renderTrace();
		const before = container.querySelector('[aria-current="step"]');
		const times = within(screen.getByRole("group", { name: "Show" })).getByRole("button", {
			name: "Times",
		});
		fireEvent.keyDown(times, { key: "ArrowRight" });
		expect(container.querySelector('[aria-current="step"]')).toBe(before);
	});

	it("stays put when an arrow key is pressed on the rail: the trace is no walk", () => {
		const { container } = renderTrace();
		const before = within(container).getByRole("button", { current: "step" });
		fireEvent.keyDown(before, { key: "ArrowDown" });
		expect(container.querySelector('[aria-current="step"]')).toBe(before);
	});
});

describe("the review itself", () => {
	const job: AgentJob = {
		...mockJobCompleted,
		id: LINEAGE_REVIEW_ID,
		reviewOutcome: "COALESCED",
		firstFeedbackReadAt: new Date("2026-07-29T08:20:00Z"),
	};

	it("gives the trace its outcome, so a coalesced review is not told as one that found nothing", () => {
		const { container } = renderTrace({ job });
		expect(container.querySelector("[data-trace-verdict]")?.textContent).toMatch(/coalesced/u);
	});

	it("shows when a developer first read its feedback", () => {
		const { container } = renderTrace({ job });
		expect(container.querySelector('[data-step="first-read"]')).not.toBeNull();
	});
});

describe("a long wait", () => {
	it("is coloured as soon as it reads as 30 min", () => {
		expect(isSlowGap(29 * 60 + 40)).toBe(true);
		expect(isSlowGap(29 * 60)).toBe(false);
	});
});

describe("the review's sentence", () => {
	it("names how many of the listed pieces reached the developer when some are not listed", () => {
		const { sentence } = traceVerdict({ ...wholeReview, moreFeedback: 3 }, { kind: "review" });
		expect(sentence).toMatch(/5 pieces of feedback from it are listed/u);
		expect(sentence).toMatch(/3 additional pieces of feedback in the chain are not listed/u);
	});
});

function tableRows(container: HTMLElement) {
	return container.querySelectorAll('[data-trace-table="observations"] [data-lineage-node]');
}

function siblingsToggle() {
	return within(screen.getByRole("group", { name: "Show" })).getByRole("button", {
		name: "Siblings",
	});
}

/** The letter of a piece and the ids of the observations its row names under From. */
function feedbackRow(container: HTMLElement, id: string): { letter: string; sources: string[] } {
	const row = container.querySelector<HTMLElement>(`[data-feedback-row="${id}"]`);
	if (row === null) {
		throw new Error(`No row for ${id}`);
	}
	const letter = row.querySelector("[data-step-control] > span")?.textContent ?? "";
	const sources = [...row.querySelectorAll<HTMLElement>("[data-observation-id]")].map(
		(chip) => chip.dataset.observationId ?? "",
	);
	return { letter: letter.replace("Feedback ", ""), sources };
}

describe("the observations' table", () => {
	it("opens and folds on its own button, with Siblings left off", () => {
		const { container } = renderTrace();
		expect(tableRows(container)).toHaveLength(0);
		fireEvent.click(
			screen.getByRole("button", { name: /^Show all \d+ observations in the table$/u }),
		);
		expect(tableRows(container)).toHaveLength(wholeReview.observations.length);
		expect(siblingsToggle().getAttribute("aria-pressed")).toBe("false");
		fireEvent.click(screen.getByRole("button", { name: "Fold the table" }));
		expect(tableRows(container)).toHaveLength(0);
	});

	it("opens with Expand all and folds with Collapse all", () => {
		const { container } = renderTrace();
		fireEvent.click(screen.getByRole("button", { name: "Expand all" }));
		expect(tableRows(container)).toHaveLength(wholeReview.observations.length);
		fireEvent.click(screen.getByRole("button", { name: "Collapse all" }));
		expect(tableRows(container)).toHaveLength(0);
	});

	it("folds by hand even while Siblings is on", () => {
		const { container } = renderTrace();
		fireEvent.click(siblingsToggle());
		expect(tableRows(container)).toHaveLength(wholeReview.observations.length);
		fireEvent.click(screen.getByRole("button", { name: "Fold the table" }));
		expect(tableRows(container)).toHaveLength(0);
	});

	it("names the feedback each observation fed, as the feedback names its observations", () => {
		const { container } = renderTrace();
		fireEvent.click(
			screen.getByRole("button", { name: /^Show all \d+ observations in the table$/u }),
		);
		const { letter, sources } = feedbackRow(container, LINEAGE_FB.card);
		expect(letter).toMatch(/^[A-Z]$/u);
		expect(sources.length).toBeGreaterThan(0);
		const fed = sources.map(
			(id) =>
				container.querySelector(
					`[data-trace-table="observations"] [data-lineage-node="observation:${id}"]`,
				)?.textContent,
		);
		for (const text of fed) {
			expect(text).toContain(letter);
		}
	});
});
