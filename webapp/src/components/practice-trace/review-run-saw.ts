import type { ReviewPracticeOutcomes, SlippedPractice } from "@/api/types.gen";
import { count, spell } from "@/components/practice-vocabulary/feedback-text";

/**
 * How far a review got, as both review lists word it. The developer's list and the admin list each
 * read their own status enum into this, so the two lists phrase one review alike.
 */
export type ReviewRunProgress = "queued" | "running" | "stopped" | "finished";

/** What a reviews list says a review saw: its progress, its verdict per practice and its reach. */
export interface ReviewRunSaw {
	progress: ReviewRunProgress;
	practices: ReviewPracticeOutcomes;
	/** How many practices the review measured; absent when it wrote no coverage ledger. */
	practicesEvaluated?: number;
	slippedPractices: SlippedPractice[];
}

/** How many slipped practices a row names before it counts the rest. */
const NAMED_PRACTICES = 2;

/**
 * What the row leads with: the practices this review recorded a problem about, by name. A review
 * that stopped, is still going or left a practice undetermined says that instead of "Nothing to
 * improve", a claim it never made.
 */
export function reviewRunLead(saw: ReviewRunSaw): string {
	const names = saw.slippedPractices.map((practice) => practice.practiceName);
	if (names.length > 0) {
		const rest = names.length - NAMED_PRACTICES;
		const named = names.slice(0, NAMED_PRACTICES).join(", ");
		return rest > 0 ? `${named} +${rest} more` : named;
	}
	if (saw.progress === "stopped") {
		return "Stopped before it finished";
	}
	if (saw.progress === "running") {
		return "Still running";
	}
	if (saw.progress === "queued") {
		return "Waiting to start";
	}
	return saw.practices.undetermined > 0
		? "Some practices could not be determined"
		: "Nothing to improve";
}

/**
 * "5 practices met, 9 did not apply · 16 practices reached", every number a count of practices. A
 * nought is dropped, and what slipped is already named in the lead. The reach is its own clause,
 * never a total of the outcomes: it counts every practice the review measured, the outcomes only
 * those it decided something about.
 */
export function reviewRunReach(saw: ReviewRunSaw): string {
	const { met, notApplicable, undetermined } = saw.practices;
	const reached = saw.practicesEvaluated;
	const outcomes = [
		{ n: met, verb: "met" },
		{ n: notApplicable, verb: "did not apply" },
		{ n: undetermined, verb: "could not be determined" },
	].filter((outcome) => outcome.n > 0);
	if (outcomes.length === 0 && reached === undefined) {
		return saw.progress === "running" || saw.progress === "queued"
			? "Results appear as it finishes"
			: "";
	}
	// One number rule for the line, so "16 practices reached" never sits beside "five held".
	const digits = [reached ?? 0, ...outcomes.map((outcome) => outcome.n)].some((n) => n >= 10);
	const decided = outcomes
		.map(
			(outcome, index) =>
				`${index === 0 ? count(outcome.n, "practice", "practices", digits) : spell(outcome.n, digits)} ${outcome.verb}`,
		)
		.join(", ");
	const reach =
		reached === undefined ? "" : `${count(reached, "practice", "practices", digits)} reached`;
	return [decided, reach].filter((clause) => clause !== "").join(" · ");
}
