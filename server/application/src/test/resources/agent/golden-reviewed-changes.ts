/**
 * The reviewed output-contract changes of the review session's prompts since 5347f0108, block by block: each
 * frozen block and the text that replaces it. Everything else the session is told stays byte for byte. The
 * prepared review input's section is compared on its own (pi-review-golden.spec.ts).
 */
import { readFileSync } from "node:fs";
import nodePath from "node:path";

const reviewed = (file: string) =>
	readFileSync(nodePath.join(import.meta.dirname, "golden-prompts", "reviewed", file), "utf8");

export const REVIEWED_CHANGES: Record<"system" | "turn", readonly (readonly [string, string])[]> = {
	/** The orchestrator: the result contract replaces the observation contract, and submit_result replaces report_observation. */
	system: [
		[
			"You review one piece of work against engineering practices and record what you saw as observations\nwith `report_observation`. Each fresh session names one practice and carries its complete criteria,",
			"You review one piece of work against engineering practices and submit what you saw as one result\nfor each practice with `submit_result`. Each fresh session names one practice and carries its complete criteria,",
		],
		[
			"`work/notes/review.md` retains all drafts, but another practice's observation does not decide this one.",
			"`work/notes/review.md` retains every result so far, but another practice's result does not decide this one.",
		],
		[
			"work, and NOT_MET is not the default when the evidence is thin. One observation per practice.\nCorrect an earlier local draft only by resending the complete observation with `revises` set to its\nreturned draft reference; an invalid correction keeps the previous draft. You are measuring, not\nadvising: there is no field for a next step,\nand the server decides what, if anything, is said to the developer.",
			"work, and NOT_MET is not the default when the evidence is thin. One result per practice.\nCorrect an earlier local draft only by submitting the complete result again; an invalid correction\nkeeps the previous draft. You are measuring, not advising: a problem's next step only names the change\nits criteria require, and the server decides what, if anything, is said to the developer.",
		],
		["## Observation contract", "## Result contract"],
		[
			"Name the specific evidence-backed conformance or shortfall in the summary. Submit one `outcome`:",
			"Name the specific evidence-backed conformance or problem in the summary. Submit one `outcome`:",
		],
		["An appropriate omission is not a shortfall.", "An appropriate omission is not a problem."],
		[
			"— earlier observations about this person and what was already said",
			"— earlier results about this person and what was already said",
		],
		[
			"6. Earlier observations and feedback (`history`) are earlier judgments, not evidence about this",
			"6. Earlier results and feedback (`history`) are earlier judgments, not evidence about this",
		],
		[
			"has none to look for. The leads are an initial advisory from a static scan: a lead is a place to inspect, not",
			"has none to look for. The leads are review input from the practice program: a lead is a place to inspect, not",
		],
		["| outcome | Meaning | severity |", "| outcome | Meaning | problems |"],
		[
			"| MET | The applicable practice standard is met in the captured evidence. | null |\n| NOT_MET | The applicable practice standard is not met in the captured evidence. | required |\n| NOT_APPLICABLE | A concrete fact rules out the practice's prerequisite occasion. | null |\n| UNDETERMINED | Captured and read evidence does not settle conformance. | null |",
			"| MET | The applicable practice standard is met in the captured evidence. | none |\n| NOT_MET | The applicable practice standard is not met in the captured evidence. | one or more |\n| NOT_APPLICABLE | A concrete fact rules out the practice's prerequisite occasion. | none |\n| UNDETERMINED | Captured and read evidence does not settle conformance. | none |",
		],
		[
			"Submit outcome and severity explicitly. A NOT_MET outcome needs an evidenced shortfall.",
			"Submit the outcome explicitly. A NOT_MET result lists each evidenced problem: its\nseverity, a one-line summary, its explanation, the lines that show it, and its basis — introduced or\nworsened by this change, or a duty the criteria state that this work lacks.",
		],
		[
			"A MET or NOT_MET claim based on absence requires `evidence.search` (sources consulted, what you looked for,",
			"A MET, NOT_MET or UNDETERMINED claim based on absence requires `evidence.search` (sources consulted, what you looked for,",
		],
		[
			"find a problem is not proof that the standard is met. UNDETERMINED requires\n`evidence.undecidability` (the open question and what would settle it). NOT_APPLICABLE and UNDETERMINED\nuse their own warrants rather than `evidence.search`. A failed, missing, truncated,\nor blocked required source is a capture/readiness failure: record no observation, never UNDETERMINED.\nAn undecided observation for a practice whose `readsSources` include `scm.pull-request.diff` must\nshow it read the change: cite the diff or name it among consulted sources. Severity follows the practice's own impact criteria.\nDo not infer mastery, intent, or behavior outside the captured records from MET.",
			"find a problem is not proof that the standard is met. The runner measures what your reads reached: a\nread cut short or failed leaves MET unavailable until you read the rest. UNDETERMINED requires\n`evidence.undecidability` (each open question, what would settle it, and the line it arises at when one\ndoes); a NOT_MET may list open questions beside its problems. NOT_APPLICABLE uses its own warrant\nrather than `evidence.search`. A failed, missing, truncated,\nor blocked required source is a capture/readiness failure: submit no result, never UNDETERMINED.\nAn undecided result for a practice whose `readsSources` include `scm.pull-request.diff` must\nshow it read the change: cite the diff or name it among consulted sources. Severity follows the practice's own impact criteria;\na result's severity is its worst problem's unless the criteria rate the pattern higher.\nDo not infer mastery, intent, or behavior outside the captured records from MET. An `example` is\noptional: one place of this work that shows the practice as the criteria ask, never on a problem's lines.",
		],
		[
			"`evidenceRationale` is read verbatim by the developer: plain prose about what you looked for, where,",
			"`explanation` is read verbatim by the developer: plain prose about what you looked for, where,",
		],
		[
			"Every observation cites the exact text that decides it. A citation names the source kind, the artifact",
			"Every result and every problem cites the exact text that decides it. A citation names the source kind, the artifact",
		],
		[
			"   collection gap: record no observation for that practice. Do not invent a file, a count or a field.",
			"   collection gap: submit no result for that practice. Do not invent a file, a count or a field.",
		],
		[
			'A practice whose practice script found leads carries them under its criteria, headed "Leads from the\npractice script", the full list in `work/practice-scripts-out/<slug>.json`; a practice without that heading',
			'A practice whose program prepared review input carries it under its criteria, headed "Leads from the\npractice script", the whole input in `work/review-input/<slug>.json`; a practice without that heading',
		],
		[
			"`report_observation` takes one complete observation for this session's practice as its arguments, not\na list or a JSON-encoded string. Call it directly: a codemode script cannot call it, and an observation that\na script prints is not recorded. It is stored or refused with the reason; correct a refused observation\nand send it again whole. A stored observation may list what the runner filled in or moved for you: it is",
			"`submit_result` takes one complete result for this session's practice as its arguments, not\na list or a JSON-encoded string. Call it directly: a codemode script cannot call it, and a result that\na script prints is not submitted. It is stored or refused with the reason; correct a refused result\nand send it again whole. A stored result may list what the runner filled in or moved for you: it is",
		],
		[
			"questions your outcome only when it says so, and resending the same observation gets the same answer.",
			"questions your outcome only when it says so, and resending the same result gets the same answer.",
		],
		[
			"observations as plain text, and do not write planning prose once you know the observation.",
			"results as plain text, and do not write planning prose once you know the result.",
		],
	],
	/** The practice turn: the call examples, the closing instruction, the once-more request and the nudge. */
	turn: [
		[reviewed("example-before.md"), reviewed("example-after.md")],
		[
			"Record one observation for test-practice with report_observation: the outcome its criterion above decides through its Occasion, Judge and Defer, as the evidence supports it, NOT_APPLICABLE and UNDETERMINED included. What the brief shows is yours to quote; read more when the criterion needs evidence beyond it. This session records and revises only this practice.",
			"Submit one result for test-practice with submit_result: the outcome its criterion above decides through its Occasion, Judge and Defer, as the evidence supports it, NOT_APPLICABLE and UNDETERMINED included. What the brief shows is yours to quote; read more when the criterion needs evidence beyond it. This session submits and corrects only this practice's result.",
		],
		[
			"## Not recorded\nThis session ended without an observation for test-practice: text outside a report_observation call records nothing. Record the outcome its criterion and the evidence you read support, in one report_observation call. If a required source is missing, truncated or blocked, that is a collection gap: record nothing.",
			"## Not recorded\nThis session ended without a result for test-practice: text outside a submit_result call records nothing. Submit the outcome its criterion and the evidence you read support, in one submit_result call. If a required source is missing, truncated or blocked, that is a collection gap: submit nothing.",
		],
		[
			"This turn has the work left to write its observations and no more. Stop exploring. Record what the inspected evidence supports for this session's practice with report_observation. Record the outcome the evidence supports, MET, NOT_MET, NOT_APPLICABLE, or UNDETERMINED; there is no quota and no next step to write. Use tools only from this point onward; no planning prose.",
			"This turn has the work left to write its result and no more. Stop exploring. Submit what the inspected evidence supports for this session's practice with submit_result. Submit the outcome the evidence supports, MET, NOT_MET, NOT_APPLICABLE, or UNDETERMINED; there is no quota. Use tools only from this point onward; no planning prose.",
		],
	],
};
