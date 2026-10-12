import type { PracticeScriptModelUse, PracticeScriptRun } from "@/api/types.gen";

import { AGENT_PURPOSE_DEFS, type AgentPurpose } from "./agent-purpose-defs";
import {
	practiceScriptModelsToCheck,
	practiceScriptNotRated,
} from "./practice-script-not-rated-defs";

export type PracticeScriptRunStatus = PracticeScriptRun["status"];
export type PracticeScriptStopReason = NonNullable<PracticeScriptRun["stopReason"]>;
export type PracticeProgramPath = NonNullable<PracticeScriptRun["path"]>;

const orList = new Intl.ListFormat("en-GB", { type: "disjunction" });

/** What ran: the practice's own script, or the built-in review a practice without one gets. */
const program = (run: PracticeScriptRun) =>
	run.programKind === "BUILTIN" ? "Built-in review" : "Practice script";

/**
 * How the program reached its result, from the calls it made. Absent when the runner did not report
 * its calls, and then the words say nothing about how.
 */
export const PRACTICE_PROGRAM_PATH_DEFS = {
	CODE: { sentence: "from its code alone", result: "Result from code" },
	MODELS: { sentence: "with its models", result: "Result from its models" },
	REVIEW: { sentence: "through the review", result: "Result from the review" },
	MIXED: { sentence: "with its models and the review", result: "Result from models and review" },
} satisfies Record<PracticeProgramPath, { sentence: string; result: string }>;

/** Why a program stopped without a result, as the end of "… stopped without a result: …". */
export const PRACTICE_SCRIPT_STOP_REASON_DEFS = {
	UNAVAILABLE: { sentence: "a model it needed was unavailable", result: "Model unavailable" },
	BUDGET: { sentence: "it used up its budget", result: "Out of budget" },
	DEADLINE: { sentence: "it ran out of time", result: "Ran out of time" },
	TOO_LARGE: { sentence: "a call was too large", result: "Call too large" },
	OFF_FORMAT: { sentence: "a model answered in the wrong format", result: "Wrong format" },
	REFUSED: { sentence: "a model refused to answer", result: "Model refused" },
	ERROR: { sentence: "it raised an error", result: "Error" },
	CANCELLED: { sentence: "the review was cancelled", result: "Cancelled" },
	INVALID_PROGRAM: {
		sentence: "it is not a program the review can run",
		result: "Not a runnable script",
	},
	INVALID_CANDIDATE: {
		sentence: "what it returned is not a result",
		result: "Returned no result",
	},
} satisfies Record<PracticeScriptStopReason, { sentence: string; result: string }>;

/** The required models the review had none for: why a skipped script did not run. */
function missingRequiredModels(run: PracticeScriptRun): PracticeScriptModelUse["purpose"][] {
	return run.models
		.filter((model) => model.need === "REQUIRED" && !model.bound)
		.map((model) => model.purpose);
}

interface PracticeScriptRunStatusDef {
	/** The first sentence of the trace line under "What it saw". */
	sentence: (run: PracticeScriptRun) => string;
	/** The *Result* cell of the review's *Practice scripts* section. */
	result: (run: PracticeScriptRun) => string;
}

/**
 * How one practice's program ended. Never a badge: it is an operating fact about the program, and a
 * badge would read as the practice's own outcome. A result the program returned can still be
 * withheld when it is admitted, so the words say it returned one, never that it was recorded.
 */
export const PRACTICE_SCRIPT_RUN_STATUS_DEFS = {
	OK: {
		sentence: (run) =>
			run.path === undefined
				? `${program(run)} returned a result.`
				: `${program(run)} returned a result ${PRACTICE_PROGRAM_PATH_DEFS[run.path].sentence}.`,
		result: (run) =>
			run.path === undefined ? "Returned a result" : PRACTICE_PROGRAM_PATH_DEFS[run.path].result,
	},
	SKIPPED: {
		sentence: (run) => {
			const missing = missingRequiredModels(run).map((purpose) => AGENT_PURPOSE_DEFS[purpose].noun);
			return missing.length === 0
				? `${program(run)} did not run.`
				: `${program(run)} did not run: no ${orList.format(missing)} was set for this review.`;
		},
		result: () => "Did not run",
	},
	FAILED: {
		sentence: (run) =>
			run.stopReason === undefined
				? `${program(run)} stopped without a result.`
				: `${program(run)} stopped without a result: ${PRACTICE_SCRIPT_STOP_REASON_DEFS[run.stopReason].sentence}.`,
		result: (run) =>
			run.stopReason === undefined
				? "Failed"
				: `Failed: ${PRACTICE_SCRIPT_STOP_REASON_DEFS[run.stopReason].result.toLowerCase()}`,
	},
	NOT_REACHED: {
		sentence: (run) => `The review ended before the ${program(run).toLowerCase()} started.`,
		result: () => "Not reached",
	},
	NOT_FINISHED: {
		sentence: (run) => `${program(run)} did not finish.`,
		result: () => "Did not finish",
	},
} satisfies Record<PracticeScriptRunStatus, PracticeScriptRunStatusDef>;

/**
 * The trace line's words: how the run ended, then how many calls its models could not rate. The
 * reasons are on the review's *Practice scripts* section.
 */
export function practiceScriptRunSentence(run: PracticeScriptRun): string {
	const ended = PRACTICE_SCRIPT_RUN_STATUS_DEFS[run.status].sentence(run);
	const { count } = run.status === "SKIPPED" ? { count: 0 } : practiceScriptNotRated(run.models);
	return count === 0
		? ended
		: `${ended} ${count} ${count === 1 ? "call was" : "calls were"} not rated.`;
}

/** The operations guide, whose sections say who can act where nothing in the workspace can. */
const OPERATIONS_GUIDE_URL = "https://docs.hephaestus.build/admin/practice-review-operations";

interface PracticeScriptGuideDef {
	/** The link's words: what the section answers. */
	label: string;
	/** The section of the operations guide. */
	anchor: string;
}

/** The sections of the operations guide that a practice script fix can open. */
export const PRACTICE_SCRIPT_GUIDE_DEFS = {
	/** Why a program ran out of time or budget, or did not finish, and what to raise or speed up. */
	STOPPED_EARLY: { label: "Why scripts stop early", anchor: "the-trace-line" },
	/** Each reason a call was not rated, and who fixes it. */
	UNRATED_CALLS: { label: "Why calls go unrated", anchor: "calls-that-were-not-rated" },
} satisfies Record<string, PracticeScriptGuideDef>;

export type PracticeScriptGuide = keyof typeof PRACTICE_SCRIPT_GUIDE_DEFS;

export function practiceScriptGuideUrl(guide: PracticeScriptGuide): string {
	return `${OPERATIONS_GUIDE_URL}#${PRACTICE_SCRIPT_GUIDE_DEFS[guide].anchor}`;
}

/**
 * The one change that would most help a program next time: assign the model a skipped script
 * required, edit a script that failed, or check a model whose answers went unrated. A program that
 * stopped early, a cap, a slow host or an oversized call has no page here that fixes it, so the
 * guide that says who can is the way out. How the program ended comes before its models, because
 * the review lost what the program did not reach.
 */
export type PracticeScriptFix =
	| { kind: "ASSIGN" | "CHECK"; purpose: AgentPurpose }
	| { kind: "EDIT" }
	| { kind: "GUIDE"; guide: PracticeScriptGuide };

export function practiceScriptFix(run: PracticeScriptRun): PracticeScriptFix | undefined {
	switch (run.status) {
		case "SKIPPED": {
			const [purpose] = missingRequiredModels(run);
			return purpose === undefined ? undefined : { kind: "ASSIGN", purpose };
		}
		case "FAILED": {
			return failedFix(run);
		}
		case "NOT_REACHED":
		case "NOT_FINISHED": {
			return { kind: "GUIDE", guide: "STOPPED_EARLY" };
		}
		case "OK": {
			const [purpose] = practiceScriptModelsToCheck(run.models);
			if (purpose !== undefined) {
				return { kind: "CHECK", purpose };
			}
			return practiceScriptNotRated(run.models).outsideTheModel
				? { kind: "GUIDE", guide: "UNRATED_CALLS" }
				: undefined;
		}
	}
}

/**
 * A program that ran out of time or budget is helped by the guide, not by an edit. A cancelled
 * review needs nothing. Anything else is the script's own doing, and the built-in review has no
 * script anyone here can edit.
 */
function failedFix(run: PracticeScriptRun): PracticeScriptFix | undefined {
	if (run.stopReason === "DEADLINE" || run.stopReason === "BUDGET") {
		return { kind: "GUIDE", guide: "STOPPED_EARLY" };
	}
	if (run.stopReason === "CANCELLED" || run.programKind === "BUILTIN") {
		return undefined;
	}
	return { kind: "EDIT" };
}
