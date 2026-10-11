import type { PracticeScriptModelUse, PracticeScriptRun } from "@/api/types.gen";

import { AGENT_PURPOSE_DEFS, type AgentPurpose } from "./agent-purpose-defs";
import {
	practiceScriptModelsToCheck,
	practiceScriptNotRated,
} from "./practice-script-not-rated-defs";

export type PracticeScriptRunStatus = PracticeScriptRun["status"];

const orList = new Intl.ListFormat("en-GB", { type: "disjunction" });

const places = (leads: number) => `${leads} ${leads === 1 ? "place" : "places"}`;

/** A run that ended early can still have handed the review places, and the words say so. */
const endedEarly = (sentence: string, result: string) => ({
	sentence: (run: PracticeScriptRun) =>
		run.leads > 0 ? `${sentence} but found ${places(run.leads)} to check.` : `${sentence}.`,
	result: (run: PracticeScriptRun) =>
		run.leads > 0 ? `${result}, found ${places(run.leads)}` : result,
});

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
 * A finished run that handed the review no places while some calls went unrated. Its words never
 * say that the work held no places to check: that would turn a capture failure into a claim about
 * the work.
 */
const handedNoPlacesWithUnratedCalls = (run: PracticeScriptRun) =>
	run.leads === 0 && practiceScriptNotRated(run.models).count > 0;

/**
 * How one practice script's run ended. Never a badge: it is an operating fact about the script
 * that ran before the review, and a badge would read as the practice's own outcome. A skipped
 * script found nothing because it did not run, so its words never count places.
 */
export const PRACTICE_SCRIPT_RUN_STATUS_DEFS = {
	OK: {
		sentence: (run) => {
			if (run.leads > 0) {
				return `Practice script found ${places(run.leads)} to check.`;
			}
			return handedNoPlacesWithUnratedCalls(run)
				? "Practice script handed the review no places."
				: "Practice script found no places to check.";
		},
		result: (run) => {
			if (run.leads > 0) {
				return `Found ${places(run.leads)}`;
			}
			return handedNoPlacesWithUnratedCalls(run)
				? "Handed the review no places"
				: "Found no places";
		},
	},
	SKIPPED: {
		sentence: (run) => {
			const missing = missingRequiredModels(run).map((purpose) => AGENT_PURPOSE_DEFS[purpose].noun);
			return missing.length === 0
				? "Practice script did not run."
				: `Practice script did not run: no ${orList.format(missing)} was set for this review.`;
		},
		result: () => "Did not run",
	},
	FAILED: endedEarly("Practice script failed", "Failed"),
	TIMED_OUT: endedEarly("Practice script ran out of time", "Ran out of time"),
	NOT_FINISHED: endedEarly("Practice script did not finish", "Did not finish"),
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
	/** Why a script ran out of time or did not finish, and what to raise or speed up. */
	STOPPED_EARLY: { label: "Why scripts stop early", anchor: "the-trace-line" },
	/** Each reason a call was not rated, and who fixes it. */
	UNRATED_CALLS: { label: "Why calls go unrated", anchor: "calls-that-were-not-rated" },
} satisfies Record<string, PracticeScriptGuideDef>;

export type PracticeScriptGuide = keyof typeof PRACTICE_SCRIPT_GUIDE_DEFS;

export function practiceScriptGuideUrl(guide: PracticeScriptGuide): string {
	return `${OPERATIONS_GUIDE_URL}#${PRACTICE_SCRIPT_GUIDE_DEFS[guide].anchor}`;
}

/**
 * The one change that would most help a script next time: assign the model a skipped script
 * required, edit a script that failed, or check a model whose answers went unrated. A script that
 * stopped early, a cap, a slow host or an oversized call has no page here that fixes it, so the
 * guide that says who can is the way out. How the script ended comes before its models, because
 * the review lost what the script did not reach.
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
			return { kind: "EDIT" };
		}
		case "TIMED_OUT":
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
