import type { ReviewRef, ReviewResultCounts } from "@/api/types.gen";
import { count, spell } from "@/components/practice-vocabulary/feedback-text";

/** What a reviews list says a review saw: how far it got, its verdict per practice and its reach. */
export interface ReviewSaw {
	status: ReviewRef["status"];
	/** One outcome per practice the review decided something about. */
	results: ReviewResultCounts;
	/** How many practices the review reached; absent where the list does not know. */
	practicesReached?: number;
	/** The names of the practices whose result is Not met, in the order the review recorded them. */
	notMetPractices: string[];
}

/** How many Not met practices a row names before it counts the rest. */
const NAMED_PRACTICES = 2;

/**
 * What the row leads with: the practices this review found Not met, by name. A review that stopped,
 * is still going or could not determine a practice says that instead of "Nothing to improve", a claim
 * it never made.
 */
export function reviewLead(saw: ReviewSaw): string {
	const names = saw.notMetPractices;
	if (names.length > 0) {
		const rest = names.length - NAMED_PRACTICES;
		const named = names.slice(0, NAMED_PRACTICES).join(", ");
		return rest > 0 ? `${named} +${rest} more` : named;
	}
	switch (saw.status) {
		case "FAILED":
		case "CANCELLED":
		case "PARTIAL": {
			return "Stopped before it finished";
		}
		case "RUNNING": {
			return "Still running";
		}
		case "QUEUED": {
			return "Waiting to start";
		}
		case "COMPLETE": {
			return saw.results.couldNotDetermine > 0
				? "Some practices could not be determined"
				: "Nothing to improve";
		}
	}
}

/**
 * "5 practices met, 9 did not apply · 16 practices reached", every number a count of practices. A
 * nought is dropped, and what was Not met is already named in the lead. The reach is its own
 * clause, never a total of the outcomes: it counts every practice the review reached, the outcomes
 * only those it decided something about.
 */
export function reviewReach(saw: ReviewSaw): string {
	const { met, doesNotApply, couldNotDetermine } = saw.results;
	const reached = saw.practicesReached;
	const outcomes = [
		{ n: met, verb: "met" },
		{ n: doesNotApply, verb: "did not apply" },
		{ n: couldNotDetermine, verb: "could not be determined" },
	].filter((outcome) => outcome.n > 0);
	if (outcomes.length === 0 && reached === undefined) {
		return saw.status === "RUNNING" || saw.status === "QUEUED"
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
