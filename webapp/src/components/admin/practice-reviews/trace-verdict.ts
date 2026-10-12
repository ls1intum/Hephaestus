import type {
	AgentJob,
	ReviewLineage,
	ReviewLineageFeedback,
	ReviewLineageObservation,
	TracedSignal,
} from "@/api/types.gen";
import type { BadgeVariant } from "@/components/common/status-def";
import { SIGNAL_STATE_LABELS } from "@/components/practice-trace/trace-format";
import { FEEDBACK_LOCATION_DEFS } from "@/components/practice-vocabulary/feedback-location-defs";
import { withholdingReasonSentence } from "@/components/practice-vocabulary/withholding-defs";
import { durationBetween } from "@/lib/dates";
import { hasText } from "@/lib/text";

import { holdReasonCopy } from "./job-utils";
import {
	countOf,
	feedbackLocation,
	lowerFirst,
	reachedDeveloper,
	shortReason,
	silentNotMet,
} from "./lineage-chain";
import type { TraceNode } from "./review-trace";
import { mergeTiming } from "./trace-bottleneck";

/**
 * How the path of the record in front ends, as the story's last step says it:
 *
 * - `worked`: the developer's work resolved it.
 * - `lost`: it did not arrive, or no longer stands: withheld, rejected, replaced, failed.
 * - `open`: it is still on its way, or delivered with no answer yet; a review still runs.
 * - `answered`: the developer answered it, which stays private to them, or disputed it.
 * - `none`: nothing was sent, and nothing had to be: a met observation, a review with nothing to say.
 */
export type TraceEndKind = "worked" | "lost" | "open" | "answered" | "none";

export interface TraceEnd {
	kind: TraceEndKind;
	/** "Worked", "Did not arrive", "Open", "Answered", "No feedback". */
	label: string;
	/** What happened, after the label: "their work resolved it". */
	reason: string;
	/** When the path ended, where the record says. */
	at?: Date;
	/** Open: since when, where the record says. */
	since?: Date;
}

export interface TraceVerdict {
	/** One sentence on top of the story that says what became of the record in front. */
	sentence: string;
	/** The end step of the record's path. */
	end: TraceEnd;
}

/** The end step's label for each way a path ends. */
export const TRACE_END_LABELS: Record<TraceEndKind, string> = {
	worked: "Worked",
	lost: "Did not arrive",
	open: "Open",
	answered: "Answered",
	none: "No feedback",
};

/** The end step's dot for each way a path ends; `none` is hollow. */
export const TRACE_END_TONES: Record<TraceEndKind, BadgeVariant> = {
	worked: "success",
	lost: "warning",
	open: "secondary",
	// Its own colour: amber already says lost or late, and an answer is neither.
	answered: "default",
	none: "outline",
};

/** Which end of several pieces names the path: the best thing that happened to any of them. */
const END_RANK: Record<TraceEndKind, number> = {
	worked: 0,
	open: 1,
	answered: 2,
	lost: 3,
	none: 4,
};

function end(
	kind: TraceEndKind,
	reason: string,
	times: Pick<TraceEnd, "at" | "since"> & { label?: string } = {},
): TraceEnd {
	const { label = TRACE_END_LABELS[kind], ...at } = times;
	return { kind, label, reason, ...at };
}

// ------------------------------------------------------------------------------------------------
// One piece of feedback
// ------------------------------------------------------------------------------------------------

/**
 * What became of one piece of feedback, in three forms: its end step, a clause for a sentence
 * about several pieces ("was delivered and resolved by the work", singular and plural), and a
 * short phrase for a count ("resolved by the work").
 */
interface FeedbackFate {
	end: TraceEnd;
	clause: readonly [singular: string, plural: string];
	phrase: string;
}

/** "the work was closed before the feedback could be posted": a reason sentence inside a sentence. */
function reasonClause(reason: NonNullable<ReviewLineageFeedback["suppressionReason"]>): string {
	return lowerFirst(withholdingReasonSentence(reason)).replace(/\.$/u, "");
}

/** "withheld (near duplicate)": the reason in a few words, for a count. */
function shortWithheld(feedback: ReviewLineageFeedback): string {
	return feedback.suppressionReason === undefined
		? "withheld"
		: `withheld (${shortReason(feedback.suppressionReason)})`;
}

/**
 * Where a prepared piece is about to reach the developer, by its channel: posted on the work, shown
 * on their practice pages, or raised in their next conversation with Heph.
 */
const PREPARED_FOR: Record<
	ReviewLineageFeedback["channel"],
	{ end: string; sentence: string; phrase: string }
> = {
	IN_CONTEXT: {
		end: "it is prepared to be posted on the work",
		sentence: "it is prepared to be posted on the work",
		phrase: "prepared to be posted on the work",
	},
	IN_APP: {
		end: "it is prepared for the developer’s practice pages",
		sentence: "it is prepared for their practice pages",
		phrase: "prepared for their practice pages",
	},
	IN_CHAT: {
		end: "it is prepared for the next conversation with Heph",
		sentence: "it is prepared for their next conversation with Heph",
		phrase: "prepared for a conversation",
	},
};

function feedbackFate(feedback: ReviewLineageFeedback): FeedbackFate {
	const { response } = feedback;
	if (feedback.withdrawnAt !== undefined) {
		return {
			end: end("lost", "an admin withdrew it after it was delivered", { at: feedback.withdrawnAt }),
			clause: ["was delivered, then withdrawn", "were delivered, then withdrawn"],
			phrase: "withdrawn by an admin",
		};
	}
	switch (feedback.deliveryState) {
		case "DISCARDED": {
			return {
				end: end("lost", "an admin rejected it", { at: feedback.decidedAt }),
				clause: ["was rejected by an admin", "were rejected by an admin"],
				phrase: "rejected by an admin",
			};
		}
		case "AWAITING_APPROVAL": {
			return {
				end: end("open", "it awaits an admin’s approval", { since: feedback.createdAt }),
				clause: ["awaits approval", "await approval"],
				phrase: "awaiting approval",
			};
		}
		case "PREPARED": {
			const prepared = PREPARED_FOR[feedback.channel];
			return {
				end: end("open", prepared.end, { since: feedback.createdAt }),
				clause: [`is ${prepared.phrase}`, `are ${prepared.phrase}`],
				phrase: prepared.phrase,
			};
		}
		case "SUPERSEDED": {
			return {
				end: end("lost", "newer feedback replaced it", { at: feedback.replacedAt }),
				clause: ["was replaced by newer feedback", "were replaced by newer feedback"],
				phrase: "replaced by newer feedback",
			};
		}
		case "FAILED": {
			return {
				end: end("lost", "it failed to deliver"),
				clause: ["failed to deliver", "failed to deliver"],
				phrase: "failed to deliver",
			};
		}
		case "PARTIALLY_FAILED": {
			return {
				end: end("lost", "only part of it was delivered, and the rest failed", {
					at: feedback.deliveredAt,
				}),
				clause: ["was only partly delivered", "were only partly delivered"],
				phrase: "only partly delivered",
			};
		}
		case "UNCONFIRMED": {
			return {
				end: end("none", "no record says that the developer saw it", { label: "Not known" }),
				clause: ["is unconfirmed", "are unconfirmed"],
				phrase: "unconfirmed",
			};
		}
		case "SUPPRESSED": {
			const shownFirst = feedback.deliveredAt !== undefined;
			const why =
				feedback.suppressionReason === undefined
					? ""
					: ` (${shortReason(feedback.suppressionReason)})`;
			return {
				end: end("lost", `${shownFirst ? "delivered, then withheld" : "withheld"}${why}`, {
					at: feedback.deliveredAt,
				}),
				clause: shownFirst
					? [`was delivered, then withheld${why}`, `were delivered, then withheld${why}`]
					: [`was withheld${why}`, `were withheld${why}`],
				phrase: shortWithheld(feedback),
			};
		}
		case "PARTIALLY_DELIVERED": {
			// A reason makes the partial delivery final: the rest was withheld and is not on its way.
			if (feedback.suppressionReason !== undefined) {
				const why = shortReason(feedback.suppressionReason);
				return {
					end: end("lost", `part of it was delivered, the rest was withheld (${why})`, {
						at: feedback.deliveredAt,
					}),
					clause: [`was only partly delivered (${why})`, `were only partly delivered (${why})`],
					phrase: `partly withheld (${why})`,
				};
			}
			return {
				end: end("open", "part of it is delivered, and the rest is on its way", {
					since: feedback.deliveredAt,
				}),
				clause: ["is partly delivered", "are partly delivered"],
				phrase: "partly delivered",
			};
		}
		case "DELIVERED": {
			break;
		}
	}
	const worked = [
		response?.resolvedByWorkAt === undefined
			? undefined
			: { said: "resolved by the work", reason: "their work resolved it" },
	].filter((entry) => entry !== undefined);
	if (worked.length > 0) {
		const said = worked.map((entry) => entry.said).join(", then ");
		return {
			end: end("worked", worked.map((entry) => entry.reason).join(", then "), {
				at: response?.resolvedByWorkAt ?? response?.respondedAt,
			}),
			clause: [`was delivered and ${said}`, `were delivered and ${said}`],
			phrase: said,
		};
	}
	const declined = declinedAnswer(response);
	if (declined !== undefined) {
		return {
			end: end(
				"answered",
				declined === "disputed"
					? "the developer disputed it"
					: "the developer answered it; how stays private to them",
				{ at: response?.respondedAt },
			),
			clause: [`was delivered and ${declined}`, `were delivered and ${declined}`],
			phrase: declined,
		};
	}
	return {
		end: end("open", "delivered, no answer yet", { since: feedback.deliveredAt }),
		clause: ["was delivered and has no answer yet", "were delivered and have no answer yet"],
		phrase: "delivered with no answer yet",
	};
}

/**
 * How the developer answered where their work did not resolve it, as far as an admin may know: a
 * dispute, or that they answered at all. Their rating and other decisions stay private to them.
 */
function declinedAnswer(response: ReviewLineageFeedback["response"]): string | undefined {
	if (response?.disputed === true) {
		return "disputed";
	}
	return response?.respondedAt === undefined ? undefined : "answered";
}

/** "30 s after it was composed", or nothing where there was no wait to tell. */
function afterWritten(feedback: ReviewLineageFeedback, at: Date | undefined): string {
	if (at === undefined || at.getTime() - feedback.createdAt.getTime() < 1000) {
		return "";
	}
	return ` ${durationBetween(feedback.createdAt, at)} after it was composed`;
}

/** Whether a piece of feedback ended lost on its way, as its end step says: the filter's losses. */
export function feedbackLost(feedback: ReviewLineageFeedback): boolean {
	return feedbackFate(feedback).end.kind === "lost";
}

function feedbackSentence(lineage: ReviewLineage, feedback: ReviewLineageFeedback): string {
	const place = lowerFirst(FEEDBACK_LOCATION_DEFS[feedbackLocation(feedback, lineage.work)].label);
	const reached = `This feedback reached the developer ${place}`;
	const { response } = feedback;
	if (feedback.withdrawnAt !== undefined) {
		return `${reached}, then an admin withdrew it.`;
	}
	switch (feedback.deliveryState) {
		case "DISCARDED": {
			return "This feedback did not reach the developer: an admin rejected it.";
		}
		case "AWAITING_APPROVAL": {
			return "This feedback has not reached the developer yet: it awaits an admin’s approval.";
		}
		case "PREPARED": {
			return `This feedback has not reached the developer yet: ${PREPARED_FOR[feedback.channel].sentence}.`;
		}
		case "SUPERSEDED": {
			return feedback.deliveredAt === undefined
				? "This feedback did not reach the developer: newer feedback replaced it."
				: `${reached}, then newer feedback replaced it.`;
		}
		case "FAILED": {
			return "This feedback did not reach the developer: it failed to deliver.";
		}
		case "PARTIALLY_FAILED": {
			return `${reached} only in part: the rest failed to deliver.`;
		}
		case "PARTIALLY_DELIVERED": {
			return feedback.suppressionReason === undefined
				? `${reached} in part, and the rest is still on its way.`
				: `${reached} in part; the rest was withheld: ${reasonClause(feedback.suppressionReason)}.`;
		}
		case "UNCONFIRMED": {
			return "This feedback was linked in a conversation, but no record says that the developer saw it.";
		}
		case "SUPPRESSED": {
			return withheldSentence(lineage, feedback, reached);
		}
		case "DELIVERED": {
			break;
		}
	}
	const arrived = `${reached}${afterWritten(feedback, feedback.deliveredAt)}`;
	const resolvedFrom = feedback.deliveredAt ?? feedback.createdAt;
	let resolved: string | undefined;
	if (response?.resolvedByWorkAt !== undefined) {
		// Delivery through an approval or a conversation can come after the work already resolved it.
		resolved =
			response.resolvedByWorkAt.getTime() < resolvedFrom.getTime()
				? `their work had resolved it ${durationBetween(response.resolvedByWorkAt, resolvedFrom)} before it was delivered`
				: `their work resolved it ${durationBetween(resolvedFrom, response.resolvedByWorkAt)} later`;
	}
	if (resolved !== undefined) {
		return `${arrived}, and ${resolved}.`;
	}
	if (response?.disputed === true) {
		return `${arrived}, but they disputed it.`;
	}
	if (response?.respondedAt !== undefined) {
		return `${arrived}, and they answered it; their answer stays private to them.`;
	}
	return `${arrived} and has no answer yet.`;
}

function withheldSentence(
	lineage: ReviewLineage,
	feedback: ReviewLineageFeedback,
	reached: string,
): string {
	const reason = feedback.suppressionReason;
	if (feedback.deliveredAt !== undefined) {
		return reason === undefined
			? `${reached}, then was withheld.`
			: `${reached}, then was withheld: ${reasonClause(reason)}.`;
	}
	const timing = reason === "ARTIFACT_MERGED" ? mergeTiming(feedback, lineage) : undefined;
	if (timing?.where === "startedReview") {
		return "This feedback did not reach the developer, by design: the merge started this review, so the work was already merged.";
	}
	if (timing?.merged !== undefined) {
		const { merged } = timing;
		return Math.abs(feedback.createdAt.getTime() - merged.getTime()) < 1000
			? "This feedback did not reach the developer: the work was merged moments before it was composed."
			: `This feedback did not reach the developer: the work was merged ${durationBetween(merged, feedback.createdAt)} before it was composed.`;
	}
	return reason === undefined
		? "This feedback did not reach the developer: it was withheld."
		: `This feedback did not reach the developer: ${reasonClause(reason)}.`;
}

// ------------------------------------------------------------------------------------------------
// Several pieces: an observation's, a review's
// ------------------------------------------------------------------------------------------------

const NUMBER_WORDS = ["no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"];

/** "it", "both", "all 5": a count that is every piece there is. */
function wholeCount(count: number): string {
	if (count === 1) {
		return "it";
	}
	return count === 2 ? "both" : `all ${count}`;
}

/** "one", "two" … "nine", then the number: a count that opens a clause. */
function countWord(count: number): string {
	return NUMBER_WORDS[count] ?? String(count);
}

/** "one was delivered and resolved by the work, and one was withheld", in the order the pieces came. */
function fatesClause(fates: readonly FeedbackFate[]): string {
	const groups = new Map<string, { fate: FeedbackFate; count: number }>();
	for (const fate of fates) {
		const key = fate.clause[0];
		const group = groups.get(key);
		if (group === undefined) {
			groups.set(key, { fate, count: 1 });
		} else {
			group.count += 1;
		}
	}
	if (groups.size > 3) {
		return kindsClause(fates);
	}
	const parts = [...groups.values()].map(({ fate, count }) => {
		const clause = fate.clause[count === 1 ? 0 : 1];
		return count === fates.length
			? `${wholeCount(count)} ${clause}`
			: `${countWord(count)} ${clause}`;
	});
	return joinAnd(parts);
}

const KIND_CLAUSE: Record<TraceEndKind, readonly [singular: string, plural: string]> = {
	worked: ["worked", "worked"],
	open: ["is still open", "are still open"],
	answered: ["was answered but not taken up", "were answered but not taken up"],
	lost: ["did not arrive", "did not arrive"],
	none: ["has no known end", "have no known end"],
};

const KIND_PHRASE: Record<TraceEndKind, string> = {
	worked: "worked",
	open: "open",
	answered: "not taken up",
	lost: "did not arrive",
	none: "with no known end",
};

const KINDS: readonly TraceEndKind[] = ["worked", "open", "answered", "lost", "none"];

/** "2 worked, 3 are still open, and 1 did not arrive": many different ends, counted by kind. */
function kindsClause(fates: readonly FeedbackFate[]): string {
	return joinAnd(
		KINDS.map((kind) => ({ kind, count: fates.filter((fate) => fate.end.kind === kind).length }))
			.filter(({ count }) => count > 0)
			.map(({ kind, count }) => `${countWord(count)} ${KIND_CLAUSE[kind][count === 1 ? 0 : 1]}`),
	);
}

function joinAnd(parts: readonly string[]): string {
	if (parts.length <= 1) {
		return parts.join("");
	}
	return `${parts.slice(0, -1).join(", ")}, and ${parts.at(-1) ?? ""}`;
}

/** The end of several pieces' path: the best end any of them reached, with every end counted. */
function endOfMany(feedback: readonly ReviewLineageFeedback[]): TraceEnd {
	if (feedback.length === 0) {
		return end("none", "no feedback is listed");
	}
	const fates = feedback.map(feedbackFate);
	if (fates.length === 1 && fates[0] !== undefined) {
		return fates[0].end;
	}
	let best: FeedbackFate | undefined;
	for (const fate of fates) {
		if (best === undefined || END_RANK[fate.end.kind] < END_RANK[best.end.kind]) {
			best = fate;
		}
	}
	const counts = new Map<string, number>();
	for (const fate of fates) {
		counts.set(fate.phrase, (counts.get(fate.phrase) ?? 0) + 1);
	}
	const reason =
		counts.size > 3
			? KINDS.map((kind) => ({
					kind,
					count: fates.filter((fate) => fate.end.kind === kind).length,
				}))
					.filter(({ count }) => count > 0)
					.map(({ kind, count }) => `${count} ${KIND_PHRASE[kind]}`)
					.join(", ")
			: [...counts.entries()].map(([phrase, count]) => `${count} ${phrase}`).join(", ");
	const kind = best?.end.kind ?? "none";
	const since =
		kind === "open"
			? fates
					.filter((fate) => fate.end.kind === "open")
					.map((fate) => fate.end.since)
					.find((at) => at !== undefined)
			: undefined;
	return end(kind, `${countOf(fates.length, "piece", "pieces")} of feedback: ${reason}`, {
		since,
	});
}

// ------------------------------------------------------------------------------------------------
// An observation
// ------------------------------------------------------------------------------------------------

function observationVerdict(
	lineage: ReviewLineage,
	observation: ReviewLineageObservation,
): TraceVerdict {
	const written = lineage.feedback
		.filter((entry) => entry.observationIds.includes(observation.id))
		.sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());
	const marked = observation.invalidated ? "This observation was marked incorrect" : undefined;
	if (written.length > 0) {
		const fates = written.map(feedbackFate);
		const led = `led to ${countOf(written.length, "piece", "pieces")} of feedback`;
		return {
			sentence: `${marked === undefined ? "This observation" : `${marked} and`} ${led}; ${fatesClause(fates)}.`,
			end: endOfMany(written),
		};
	}
	const phase = lineage.review?.phase;
	const running = phase === "RUNNING" || phase === "QUEUED";
	const stopped = phase === "FAILED" || phase === "TIMED_OUT" || phase === "CANCELLED";
	if (marked !== undefined) {
		return {
			sentence: `${marked}, so no feedback from it reached the developer.`,
			end: end("lost", "an admin marked it incorrect", { at: observation.markedIncorrectAt }),
		};
	}
	switch (observation.outcome) {
		case "NOT_MET": {
			if (running) {
				return {
					sentence: "This observation has led to no feedback yet, because the review still runs.",
					end: end("open", "the review still runs", { since: lineage.review?.createdAt }),
				};
			}
			if (stopped) {
				return {
					sentence:
						"This observation led to no feedback, because the review stopped before it composed any.",
					end: end("lost", "the review stopped before it composed feedback", {
						at: lineage.review?.finishedAt,
					}),
				};
			}
			if (observation.feedbackCount > observation.ownFeedbackCount) {
				return {
					sentence: "This observation led to no feedback in this review; a later review wrote it.",
					end: end("none", "a later review wrote the feedback", { label: "Elsewhere" }),
				};
			}
			return {
				sentence:
					"This observation was not met but led to no feedback, so the developer was not told.",
				end: end("lost", "no feedback was written from it"),
			};
		}
		case "MET": {
			return {
				sentence: "This observation was met and led to no feedback.",
				end: end("none", "it was met, so nothing had to be sent"),
			};
		}
		case "NOT_APPLICABLE": {
			return {
				sentence: "This observation did not apply to the work, so it led to no feedback.",
				end: end("none", "the practice did not apply"),
			};
		}
		case "UNDETERMINED": {
			return {
				sentence: "This observation stayed undetermined, so it led to no feedback.",
				end: end("none", "the review could not tell"),
			};
		}
	}
}

// ------------------------------------------------------------------------------------------------
// A review
// ------------------------------------------------------------------------------------------------

function reviewVerdict(
	lineage: ReviewLineage,
	reviewOutcome: AgentJob["reviewOutcome"] | undefined,
): TraceVerdict {
	const { review } = lineage;
	if (review === undefined) {
		return {
			sentence: "The record of this review is gone, so this view cannot tell what it did.",
			end: end("none", "the record of the review is gone", { label: "Not known" }),
		};
	}
	const own = lineage.feedback
		.filter((entry) => entry.agentJobId === review.id)
		.sort((a, b) => a.createdAt.getTime() - b.createdAt.getTime());
	const observed = lineage.observations.filter((entry) => entry.agentJobId === review.id).length;
	const recorded = countOf(observed + lineage.moreObservations, "observation", "observations");
	const ranFrom = review.startedAt ?? review.createdAt;
	const ran =
		review.finishedAt === undefined ? undefined : durationBetween(ranFrom, review.finishedAt);
	const waited =
		review.startedAt !== undefined &&
		review.startedAt.getTime() - review.createdAt.getTime() >= 1000
			? durationBetween(review.createdAt, review.startedAt)
			: undefined;

	switch (review.phase) {
		case "QUEUED": {
			const hold = review.holdReason;
			if (hold !== undefined && hold.length > 0) {
				const label = lowerFirst(holdReasonCopy(hold).label);
				return {
					sentence: `This review is on hold (${label}) and has observed nothing yet.`,
					end: end("open", `the review is on hold: ${label}`, { since: review.createdAt }),
				};
			}
			return {
				sentence: "This review waits in the queue and has observed nothing yet.",
				end: end("open", "the review waits in the queue", { since: review.createdAt }),
			};
		}
		case "RUNNING": {
			return {
				sentence:
					observed + lineage.moreObservations === 0
						? "This review still runs and has recorded no observation yet."
						: `This review still runs and has recorded ${recorded} so far.`,
				end: end("open", "the review still runs", { since: ranFrom }),
			};
		}
		case "FAILED":
		case "TIMED_OUT":
		case "CANCELLED": {
			const how = { FAILED: "failed", TIMED_OUT: "timed out", CANCELLED: "was canceled" }[
				review.phase
			];
			const after = ran === undefined ? "" : ` after ${ran}`;
			let what = `and composed ${countOf(own.length, "piece", "pieces")} of feedback`;
			if (observed + lineage.moreObservations === 0) {
				what = "with nothing recorded";
			} else if (own.length === 0) {
				what = `and recorded ${recorded}, but composed no feedback`;
			}
			return {
				sentence: `This review ${how}${after} ${what}.`,
				end:
					own.length === 0
						? end("lost", `the review ${how} before it composed feedback`, {
								at: review.finishedAt,
							})
						: endOfMany(own),
			};
		}
		case "COMPLETED": {
			break;
		}
	}
	if (reviewOutcome === "INSUFFICIENT_EVIDENCE") {
		return {
			sentence: "This review assessed nothing: the evidence it needed was missing or unreadable.",
			end: end("none", "nothing was assessed"),
		};
	}
	if (reviewOutcome === "COALESCED") {
		return {
			sentence:
				"This review assessed nothing anew: it was coalesced with an earlier review that had answered every practice on the same code.",
			end: end("none", "an earlier review holds the answers"),
		};
	}
	if (reviewOutcome === "SUPERSEDED") {
		return {
			sentence:
				"This review assessed nothing: a newer review of the same work replaced it while it waited, and that review carries its practices.",
			end: end("none", "a newer review carries its practices"),
		};
	}
	const how = [
		ran === undefined ? "completed" : `ran ${ran}`,
		ran !== undefined && waited !== undefined ? `after a ${waited} wait` : undefined,
	]
		.filter((entry) => entry !== undefined)
		.join(" ");
	if (observed + lineage.moreObservations === 0) {
		return {
			sentence: `This review ${how} and recorded no observation.`,
			end: end("none", "the review recorded no observation"),
		};
	}
	const composed = own.length + lineage.moreFeedback;
	const silent = silentNotMet(lineage).length;
	if (composed === 0) {
		return {
			sentence: `This review ${how} and recorded ${recorded}, but composed no feedback.`,
			end:
				silent > 0
					? end(
							"lost",
							`${countOf(silent, "not-met observation", "not-met observations")} led to no feedback`,
						)
					: end("none", "no feedback was composed"),
		};
	}
	const reached = own.filter(reachedDeveloper).length;
	let reachedText: string;
	if (lineage.moreFeedback > 0) {
		// The pieces not listed are not known to have reached anyone, nor known not to have.
		reachedText = `${reached} of the ${own.length} listed reached the developer`;
	} else if (reached === composed) {
		reachedText =
			composed === 1 ? "it reached the developer" : `all ${composed} reached the developer`;
	} else if (reached === 0) {
		reachedText = "none reached the developer";
	} else {
		reachedText = `${reached} reached the developer`;
	}
	return {
		sentence: `This review ${how} and composed ${countOf(composed, "piece", "pieces")} of feedback; ${reachedText}.`,
		end: endOfMany(own),
	};
}

// ------------------------------------------------------------------------------------------------
// The record in front
// ------------------------------------------------------------------------------------------------

/**
 * The verdict on the record in front and the end of its path, read from the chain alone: no model
 * writes it, so the same chain always says the same thing. A review, its observations step and the
 * work tell the review; an observation and a piece of feedback tell themselves. A record the chain
 * does not list falls back to the review.
 */
export function traceVerdict(
	lineage: ReviewLineage,
	here: TraceNode | undefined,
	reviewOutcome?: AgentJob["reviewOutcome"],
): TraceVerdict {
	if (here?.kind === "feedback") {
		const feedback = lineage.feedback.find((entry) => entry.id === here.id);
		if (feedback !== undefined) {
			return { sentence: feedbackSentence(lineage, feedback), end: feedbackFate(feedback).end };
		}
	}
	if (here?.kind === "observation") {
		const observation = lineage.observations.find((entry) => entry.id === here.id);
		if (observation !== undefined) {
			return observationVerdict(lineage, observation);
		}
	}
	return reviewVerdict(lineage, reviewOutcome);
}

// ------------------------------------------------------------------------------------------------
// Work no review started on
// ------------------------------------------------------------------------------------------------

/** What the trace of work with no review says: its sentence on top and its end step. */
export interface UnreviewedVerdict {
	sentence: string;
	end: TraceEnd;
}

/**
 * What the trace of work none of whose moments started a review says, from the state of its latest
 * moment. A moment still queued or waiting has not been refused, so the trace says it waits rather
 * than that no review started. A reason is said only where it still holds: "Practice reviews are
 * turned off" is said while they are off, and once they are on again a waiting moment just waits and
 * a refused one says they were off when it happened.
 */
export function unreviewedVerdict(
	moment: TracedSignal,
	practicesEnabled: boolean | undefined,
): UnreviewedVerdict {
	const staleOff = moment.stateReason === "PRACTICES_DISABLED" && practicesEnabled === true;
	const reason =
		!staleOff && hasText(moment.stateReasonDescription)
			? moment.stateReasonDescription.trim()
			: undefined;
	switch (moment.state) {
		case "PENDING": {
			return {
				sentence: "No review has started on this work yet: it is queued for review.",
				end: end("open", reason ?? "it is queued for review", {
					label: "Queued for review",
					since: moment.occurredAt,
				}),
			};
		}
		case "DEFERRED": {
			return {
				sentence:
					"No review has started on this work yet: Hephaestus waits to see if it keeps changing.",
				end: end("open", reason ?? "Hephaestus waits to see if it keeps changing", {
					label: "Waiting",
					since: moment.occurredAt,
				}),
			};
		}
		case "TRIGGERED": {
			return {
				sentence: "A review started on this work.",
				end: end("open", "a review started", { label: "Review started", at: moment.occurredAt }),
			};
		}
		case "RECORDED":
		case "SUPPRESSED":
		case "LAPSED": {
			break;
		}
	}
	const why =
		reason ??
		(staleOff
			? "Practice reviews were turned off for this workspace when it happened. They are on now."
			: SIGNAL_STATE_LABELS[moment.state]);
	return {
		sentence: `No review started on this work: ${lowerFirst(firstSentence(why))}.`,
		end: end("lost", why, { label: "No review started", at: moment.occurredAt }),
	};
}

/** The first sentence of a reason, without its full stop, for a sentence of the trace's own. */
function firstSentence(text: string): string {
	return (text.split(/(?<=\.)\s/u)[0] ?? text).replace(/\.$/u, "");
}
