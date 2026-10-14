import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import test from "node:test";

import { Ajv } from "ajv";

import type { PublicReviewHistory } from "../../../main/resources/agent/pi-review-brief.ts";

import {
	type Destination,
	type ComposedFeedbackEnvelope,
	type ComposedFeedbackPiece,
	REVIEW_LIMITS,
	DISPOSITIONS,
	type ReviewedResult,
	STANDARD_REFERENCE,
	WRITE_CONTRACT,
	buildReviewTurn,
	decidedByReview,
	evidenceRefs,
	namedEvidence,
	notReachedNote,
	type PriorAdviceWitness,
	priorAdviceWitnesses,
	priorPublicFeedback,
	publicResults,
	type ReviewContext,
	practiceStandard,
	readPracticePageSupport,
	readConversationEligibility,
	readReview,
	reviewToolParameters,
	notMetReference,
	publicQualifications,
	undeliverableFeedback,
	validateFeedbackEvidence,
} from "../../../main/resources/agent/pi-runner-composition.ts";

const supersede = (threadKey: string): ComposedFeedbackPiece => ({
	action: "SUPERSEDE",
	destination: "CONVERSATION",
	practiceSlug: "writes-focused-pull-requests",
	supersedesThreadKey: threadKey,
});

const target = (threadKey: string, destination: Destination = "CONVERSATION") => ({
	threadKey,
	destination,
	practiceSlug: "writes-focused-pull-requests",
});

void test("an envelope that lists the threads its feedback supersede delivers all of them", () => {
	const envelope = {
		preparedTargets: [target("t-1"), target("t-2")],
		feedback: [supersede("t-1"), supersede("t-2")],
	};

	assert.deepEqual(undeliverableFeedback(envelope), []);
});

void test("only the piece naming an unlisted or other-lane thread is undeliverable", () => {
	assert.deepEqual(
		undeliverableFeedback({
			preparedTargets: [target("t-1")],
			feedback: [supersede("t-1"), supersede("t-9")],
		}),
		[supersede("t-9")],
	);
	assert.deepEqual(
		undeliverableFeedback({
			preparedTargets: [target("t-1", "PRACTICE_PAGE")],
			feedback: [supersede("t-1")],
		}),
		[supersede("t-1")],
	);
	const quiet: ComposedFeedbackEnvelope = {
		preparedTargets: [],
		feedback: [
			{
				action: "WITHHOLD",
				destination: "CONVERSATION",
				practiceSlug: "p",
				withholdReason: "ALREADY_SAID",
			},
		],
	};
	assert.deepEqual(undeliverableFeedback(quiet), []);
	assert.deepEqual(undeliverableFeedback(undefined), []);
});

void test("private feedback rests on a problem of its own practice, named by its result or a problem", () => {
	const results = new Map([
		["confirmed-criteria", { practiceSlug: "acceptance-criteria", outcome: "MET", problems: [] }],
		[
			"unconfirmed-outcome",
			{
				practiceSlug: "issue-outcome",
				outcome: "NOT_MET",
				problems: [{ problemId: "no-closing-note" }],
			},
		],
	]);
	const basedOn = ["confirmed-criteria", "unconfirmed-outcome"];

	assert.match(
		validateFeedbackEvidence("acceptance-criteria", basedOn, results) ?? "",
		/must name the NOT_MET result of the primary practice 'acceptance-criteria' or one of its problems/u,
	);
	assert.equal(validateFeedbackEvidence("issue-outcome", basedOn, results), null);
	// An problem stands for its result: it is what the piece rests on, and it decides the practice.
	assert.equal(validateFeedbackEvidence("issue-outcome", ["no-closing-note"], results), null);
	const missing = validateFeedbackEvidence("issue-outcome", ["missing"], results) ?? "";
	assert.match(missing, /does not name an admitted result or problem/u);
	assert.match(missing, /unconfirmed-outcome \(its problems: no-closing-note\)/u);
});

void test("every id a text or piece names is written as the reference it resolves to, each once", () => {
	const named = namedEvidence(
		new Map([
			["result-1", { problems: [{ problemId: "problem-1" }] }],
			["result-2", { problems: [] }],
		]),
	);
	assert.deepEqual(
		evidenceRefs(["problem-1", "result-1", "problem-1", "result-2", "result-1"], named),
		[
			{ resultId: "result-1", problemId: "problem-1" },
			{ resultId: "result-1" },
			{ resultId: "result-2" },
		],
	);
	// Only checked ids reach this point: an id that names nothing is a defect, never dropped.
	assert.throws(() => evidenceRefs(["elsewhere"], named), /elsewhere names no admitted result/u);
});

void test("the routing read after admission is one decision per NOT_MET result, or none", () => {
	const current = [
		{ resultId: "r-1", practiceSlug: "one", outcome: "NOT_MET" },
		{ resultId: "r-2", practiceSlug: "two", outcome: "NOT_MET" },
		{ resultId: "r-3", practiceSlug: "three", outcome: "MET" },
	];
	const readAt = "2026-10-12T09:41:07Z";
	const routes = [
		{ resultId: "r-1", decision: "ADMIT" },
		{ resultId: "r-2", decision: "HAS_INLINE_ANCHOR" },
	];
	assert.deepEqual(
		readConversationEligibility({ state: "COMPLETE", readAt, results: routes }, current),
		{
			state: "COMPLETE",
			readAt,
			results: routes,
		},
	);
	assert.deepEqual(
		readConversationEligibility({ state: "UNAVAILABLE", readAt, results: [] }, current),
		{
			state: "UNAVAILABLE",
			readAt,
		},
	);
	for (const malformed of [
		// One NOT_MET result left without a decision, a MET result routed, a decision twice.
		{ state: "COMPLETE", readAt, results: routes.slice(0, 1) },
		{
			state: "COMPLETE",
			readAt,
			results: [...routes, { resultId: "r-3", decision: "ADMIT" }],
		},
		{ state: "COMPLETE", readAt, results: [...routes, routes[0]] },
		// The retired per-problem shape is no routing at all.
		{
			state: "COMPLETE",
			readAt,
			problems: routes.map(({ resultId, decision }) => ({
				problemId: resultId,
				decision,
			})),
		},
		{ state: "UNAVAILABLE", readAt, results: routes },
		{ state: "COMPLETE", readAt, results: [{ resultId: "r-1", decision: "MAYBE" }] },
	]) {
		assert.equal(readConversationEligibility(malformed, current), null, JSON.stringify(malformed));
	}
	const occurrence = {
		resultId: "r-1",
		itemKind: "scm.pull_request",
		itemId: "7",
		outcome: "NOT_MET",
		origin: "EVENT",
		summary: "Errors are swallowed.",
		recordedAt: readAt,
	};
	const support = readPracticePageSupport(
		{
			state: "COMPLETE",
			readAt,
			practices: [
				{ practiceSlug: "one", decision: "ADMIT", occurrences: [occurrence] },
				{ practiceSlug: "two", decision: "NO_EVIDENCE", occurrences: [] },
			],
		},
		current,
	);
	assert.ok(support?.state === "COMPLETE", JSON.stringify(support));
	assert.deepEqual(support.practices[0]?.occurrences[0], {
		...occurrence,
		explanation: null,
		evidence: null,
		practiceVersionId: null,
	});
	const { resultId, ...legacy } = occurrence;
	assert.equal(
		readPracticePageSupport(
			{
				state: "COMPLETE",
				readAt,
				practices: [
					{
						practiceSlug: "one",
						decision: "ADMIT",
						occurrences: [{ ...legacy, problemId: resultId }],
					},
					{ practiceSlug: "two", decision: "NO_EVIDENCE", occurrences: [] },
				],
			},
			current,
		),
		null,
	);
});

void test("a review names the practices it never settled and forbids a verdict on them", () => {
	assert.equal(notReachedNote([]), "");
	const many = notReachedNote(["ships-tests-with-the-change", "describe-what-and-why"]);
	assert.match(many, /2 of its practices: ships-tests-with-the-change, describe-what-and-why\./u);
	assert.match(many, /Say nothing about them, for or against/u);
});

// Two practices citing one line can record separate gaps with separate remedies; the line alone decides neither.
const sharedLine = { path: "App/LocationModel.swift", startLine: 28, anchorable: true };

void test("negatives that quote the same line each keep their own standard, rationale and quote", () => {
	const reference = notMetReference(
		[
			{
				resultId: "swallowed",
				practiceSlug: "handles-errors",
				outcome: "NOT_MET",
				publicEligible: true,
				explanation: "The failure callback drops its error and nothing records it.",
				citations: [],
				problems: [
					{
						problemId: "swallowed-callback",
						citations: [{ ...sharedLine, quote: "func didFail(error: any Error) {}" }],
					},
				],
			},
			{
				resultId: "unfinished",
				practiceSlug: "ready-handoff",
				outcome: "NOT_MET",
				publicEligible: true,
				explanation: "The promised terminal preview state is not reached when this callback fails.",
				citations: [
					{
						path: "description.md",
						startLine: 5,
						quote: "This change completes loading with a ready or failed preview state.",
					},
				],
				problems: [
					{
						problemId: "unfinished-callback",
						citations: [{ ...sharedLine, quote: "func didFail(error: any Error) {}" }],
					},
				],
			},
		],
		(slug) =>
			slug === "handles-errors"
				? "Record or handle a dropped failure; logging or deliberate safe recovery may suffice."
				: "Complete the terminal preview state promised within this change; follow-up work is not owed here.",
	);

	for (const shown of [
		"### Criteria of `handles-errors`",
		"Record or handle a dropped failure; logging or deliberate safe recovery may suffice.",
		"### Criteria of `ready-handoff`",
		"Complete the terminal preview state promised within this change; follow-up work is not owed here.",
		"The failure callback drops its error and nothing records it.",
		"The promised terminal preview state is not reached when this callback fails.",
		"This change completes loading with a ready or failed preview state.",
		'"resultId": "swallowed"',
		'"resultId": "unfinished"',
		'"problemId": "swallowed-callback"',
		'"problemId": "unfinished-callback"',
	]) {
		assert.ok(reference.includes(shown), shown);
	}
	assert.equal(reference.match(/"path": "App\/LocationModel\.swift"/gu)?.length, 2);
	assert.equal(reference.match(/"startLine": 28/gu)?.length, 2);
	assert.equal(reference.match(/func didFail\(error: any Error\) \{\}/gu)?.length, 2);
});

void test("negatives that quote the same line may take separate remedies or one shared one", () => {
	const sameLine = new Map<string, ReviewedResult>([
		[
			"swallowed",
			{
				practiceSlug: "handles-errors",
				outcome: "NOT_MET",
				problems: [{ problemId: "swallowed-callback", citations: [sharedLine] }],
			},
		],
		[
			"unfinished",
			{
				practiceSlug: "ready-handoff",
				outcome: "NOT_MET",
				problems: [{ problemId: "unfinished-callback", citations: [sharedLine] }],
			},
		],
	]);
	const context: ReviewContext = { witnesses: new Map<string, PriorAdviceWitness>() };
	const decisions = [
		{ resultId: "swallowed", disposition: "RAISE" },
		{ resultId: "unfinished", disposition: "RAISE" },
	];

	// Separate remedies: the handoff ask in the summary, the handler's own repair on its line.
	const summary = {
		body: "Complete the promised failed preview state so loading ends on this path.",
		basedOn: ["unfinished"],
	};
	const repair = {
		body: "This handler drops the failure; record it or establish why safe recovery needs no report.",
		basedOn: ["swallowed-callback"],
		anchor: { problemId: "swallowed-callback", citationIndex: 0 },
	};
	assert.deepEqual(readReview({ decisions, summary, inline: [repair] }, sameLine, true, context), {
		review: {
			summary: { ...summary, basedOn: [{ resultId: "unfinished" }] },
			inline: [
				{
					...repair,
					basedOn: [{ resultId: "swallowed", problemId: "swallowed-callback" }],
				},
			],
			withheld: [],
		},
	});

	// One remedy that genuinely answers both stays one point resting on both.
	const shared = {
		body: "Handle this callback by recording the failure and setting the promised failed preview state.",
		basedOn: ["swallowed", "unfinished"],
		anchor: { problemId: "swallowed-callback", citationIndex: 0 },
	};
	assert.deepEqual(
		readReview({ decisions, summary: null, inline: [shared] }, sameLine, true, context),
		{
			review: {
				summary: null,
				inline: [
					{
						...shared,
						// The note sits on swallowed-callback, so it rests on that problem as well as its result.
						basedOn: [
							{ resultId: "swallowed" },
							{ resultId: "unfinished" },
							{ resultId: "swallowed", problemId: "swallowed-callback" },
						],
					},
				],
				withheld: [],
			},
		},
	);
});

// --- The review on the work -------------------------------------------------------------------

const diffCitation = (line: number) => ({
	index: 0,
	sourceKind: "scm.pull-request.diff",
	path: "App/ContentView.swift",
	side: "NEW",
	startLine: line,
	anchorable: true,
});

/**
 * One result per practice; each NOT_MET result holds its problems, and only a problem's citations can carry a
 * line note.
 */
const results = new Map<string, ReviewedResult>([
	[
		"colors",
		{
			practiceSlug: "uses-adaptive-colors",
			outcome: "NOT_MET",
			problems: [
				{
					problemId: "colors-fill",
					citations: [diffCitation(13), { ...diffCitation(16), index: 1 }],
				},
			],
		},
	],
	[
		"handoff",
		{
			practiceSlug: "honours-linked-issue-acceptance-criteria",
			outcome: "NOT_MET",
			problems: [{ problemId: "handoff-criteria", citations: [] }],
		},
	],
	[
		"why",
		{
			practiceSlug: "describe-what-and-why",
			outcome: "NOT_MET",
			problems: [{ problemId: "why-reason", citations: [] }],
		},
	],
	["preview", { practiceSlug: "ships-a-preview", outcome: "MET", problems: [] }],
	["unsure", { practiceSlug: "keeps-tests-honest", outcome: "COULD_NOT_DETERMINE", problems: [] }],
	[
		"description",
		{
			practiceSlug: "states-how-to-verify",
			outcome: "NOT_MET",
			problems: [
				{
					problemId: "description-steps",
					citations: [
						{
							index: 0,
							sourceKind: "scm.pull-request.core",
							path: "description.md",
							anchorable: false,
						},
					],
				},
			],
		},
	],
]);

/** A tutor's comment and Hephaestus's own delivered comment may stand as prior advice; the author's own may not. */
const witnesses = new Map<string, PriorAdviceWitness>([
	["github:review-comment:7", { eligibleForPriorAdvice: true, eligibleForAlreadySaid: true }],
	[
		"feedback:00000000-0000-4000-8000-000000000001",
		{ eligibleForPriorAdvice: true, eligibleForAlreadySaid: true },
	],
	["github:issue-comment:9", { eligibleForPriorAdvice: false, eligibleForAlreadySaid: false }],
]);

const reviewContext: ReviewContext = { witnesses };

const raise = (resultId: string) => ({ resultId, disposition: "RAISE" });
const below = (resultId: string) => ({ resultId, disposition: "BELOW_BAR" });
const allRaised = [raise("colors"), raise("handoff"), raise("why"), raise("description")];

const errorsOf = (value: unknown, lineNotes = true, context = reviewContext): string[] => {
	const read = readReview(value, results, lineNotes, context);
	return "errors" in read ? read.errors : [];
};

void test("a complete summary is stored exactly as written, resting on exactly what it names", () => {
	const body =
		"The description stops at the issue link. Say which of #4's checks this screen covers and why it matters.\n\n" +
		"    let fill = Color(red: 0.2, green: 0.6, blue: 0.3)\n";
	const read = readReview(
		{
			summary: { body, basedOn: ["handoff", "why-reason", "why-reason"] },
			decisions: [raise("handoff"), raise("why"), below("colors"), below("description")],
		},
		results,
		true,
		reviewContext,
	);
	assert.ok("review" in read, JSON.stringify(read));
	assert.equal(read.review.summary?.body, body);
	assert.deepEqual(read.review.summary.basedOn, [
		{ resultId: "handoff" },
		{ resultId: "why", problemId: "why-reason" },
	]);
	assert.deepEqual(read.review.inline, []);
});

void test("a review with only line notes is a whole review: no summary is required", () => {
	const read = readReview(
		{
			inline: [
				{
					body: "This explicit RGB fill stays unchanged in Dark Mode; check contrast in both appearances and use an adaptive fill.",
					basedOn: ["colors"],
					anchor: { problemId: "colors-fill", citationIndex: 0 },
				},
			],
			decisions: [
				raise("colors"),
				...["handoff", "why", "description"].map((resultId) => ({
					resultId,
					disposition: "ALREADY_SAID",
					witnessIds: ["github:review-comment:7"],
				})),
			],
		},
		results,
		true,
		reviewContext,
	);
	assert.ok("review" in read, JSON.stringify(read));
	assert.equal(read.review.summary, null);
	assert.deepEqual(read.review.inline[0]?.anchor, {
		problemId: "colors-fill",
		citationIndex: 0,
	});
	// The server places a note only on a problem its basedOn names: the anchor's problem joins the result.
	assert.deepEqual(read.review.inline[0].basedOn, [
		{ resultId: "colors" },
		{ resultId: "colors", problemId: "colors-fill" },
	]);
});

void test("a stored review is the wire shape: every basedOn a list of references, the anchor a problem's citation", () => {
	const read = readReview(
		{
			decisions: [raise("colors"), raise("why"), below("handoff"), below("description")],
			summary: { body: "Say why the screen exists.", basedOn: ["why-reason"] },
			inline: [
				{
					body: "Use an adaptive fill.",
					basedOn: ["colors-fill", "colors"],
					anchor: { problemId: "colors-fill", citationIndex: 1 },
				},
			],
		},
		results,
		true,
		reviewContext,
	);
	assert.ok("review" in read, JSON.stringify(read));
	// What feedback.json holds is the JSON text of the stored review, not the object in memory.
	const text = JSON.stringify({ review: read.review });
	const written: unknown = JSON.parse(text);
	assert.deepEqual(written, {
		review: {
			summary: {
				body: "Say why the screen exists.",
				basedOn: [{ resultId: "why", problemId: "why-reason" }],
			},
			inline: [
				{
					body: "Use an adaptive fill.",
					basedOn: [{ resultId: "colors", problemId: "colors-fill" }, { resultId: "colors" }],
					anchor: { problemId: "colors-fill", citationIndex: 1 },
				},
			],
			withheld: [
				{ basedOn: [{ resultId: "handoff" }], reason: "BELOW_BAR" },
				{ basedOn: [{ resultId: "description" }], reason: "BELOW_BAR" },
			],
		},
	});
});

const note = (citationIndex: number) => ({
	body: `The fill on this line fixes one appearance (${citationIndex}).`,
	basedOn: ["colors"],
	anchor: { problemId: "colors-fill", citationIndex },
});

void test("one practice may carry several notes about different lines, never two on one line", () => {
	assert.ok(
		"review" in
			readReview(
				{
					inline: [note(0), note(1)],
					decisions: [raise("colors"), below("handoff"), below("why"), below("description")],
				},
				results,
				true,
				reviewContext,
			),
	);
	assert.match(
		errorsOf({ inline: [note(0), note(0)] }).join("\n"),
		/inline #2: another note already sits on citation 0 of colors-fill/u,
	);
});

void test("an anchor is a line of this change cited by a problem the note rests on, itself or through its result", () => {
	const errors = errorsOf({
		inline: [
			{
				body: "One.",
				basedOn: ["handoff"],
				anchor: { problemId: "colors-fill", citationIndex: 0 },
			},
			{
				body: "Two.",
				basedOn: ["description"],
				anchor: { problemId: "description-steps", citationIndex: 0 },
			},
			{
				body: "Three.",
				basedOn: ["colors"],
				anchor: { problemId: "colors-fill", citationIndex: 7 },
			},
			{ body: "Four.", basedOn: ["colors"], anchor: { problemId: "colors", citationIndex: 0 } },
			{
				body: "Five.",
				basedOn: ["colors"],
				anchor: { problemId: "elsewhere", citationIndex: 0 },
			},
		],
	}).join("\n");
	assert.match(errors, /inline #1: anchor names colors-fill, which this note's basedOn does not/u);
	assert.match(errors, /inline #2: citation 0 of description-steps is not a line of this change/u);
	assert.match(errors, /inline #3: colors-fill has no citation 7/u);
	// A result's own citations never anchor: the note names the problem whose citation it sits on.
	assert.match(errors, /inline #4: anchor names colors, which is a result/u);
	assert.match(errors, /inline #5: anchor names elsewhere, which is not a problem/u);
	// Naming the problem itself rests the note on it, as naming its result does.
	assert.ok(
		"review" in
			readReview(
				{
					decisions: [raise("colors"), below("handoff"), below("why"), below("description")],
					inline: [
						{
							body: "Adapt.",
							basedOn: ["colors-fill"],
							anchor: { problemId: "colors-fill", citationIndex: 0 },
						},
					],
				},
				results,
				true,
				reviewContext,
			),
	);
	assert.match(
		errorsOf(
			{
				inline: [
					{
						body: "x",
						basedOn: ["colors"],
						anchor: { problemId: "colors-fill", citationIndex: 0 },
					},
				],
			},
			false,
		).join("\n"),
		/this work has no lines to place a note on/u,
	);
});

void test("a part may rest only on admitted NOT_MET results or their problems, never on a MET result", () => {
	const errors = errorsOf({
		summary: { body: "A sentence.", basedOn: ["why", "unsure", "elsewhere"] },
	}).join("\n");
	assert.match(
		errors,
		/elsewhere, which is not one of the NOT_MET results or problems this review may rest on/u,
	);
	assert.match(
		errorsOf({ summary: { body: "A sentence.", basedOn: ["unsure"] } }).join("\n"),
		/basedOn names unsure, which is not a NOT_MET result or one of its problems/u,
	);
	// The review on the work never acknowledges a MET result: a summary or a note resting on one is refused.
	const acknowledged = {
		decisions: [raise("colors"), below("handoff"), below("why"), below("description")],
		summary: {
			body: "The preview you added makes the layout easy to check.",
			basedOn: ["preview"],
		},
		inline: [
			{
				body: "Adapt this fill; the preview shows it well.",
				basedOn: ["colors", "preview"],
				anchor: { problemId: "colors-fill", citationIndex: 0 },
			},
		],
	};
	assert.deepEqual(errorsOf(acknowledged), [
		"summary: basedOn names preview, which is not a NOT_MET result or one of its problems. The review on the work speaks only about NOT_MET results and their problems; it never acknowledges a MET result",
		"inline #1: basedOn names preview, which is not a NOT_MET result or one of its problems. The review on the work speaks only about NOT_MET results and their problems; it never acknowledges a MET result",
	]);
	assert.match(
		errorsOf({ summary: { body: "A sentence.", basedOn: [] } }).join("\n"),
		/basedOn is required/u,
	);
});

void test("a NOT_MET result is raised or withheld whole, and only a NOT_MET result takes a decision", () => {
	assert.match(
		errorsOf({
			decisions: [below("why")],
			summary: { body: "A sentence.", basedOn: ["why"] },
		}).join("\n"),
		/why is withheld as BELOW_BAR, and a text speaks about it/u,
	);
	// A withheld result stays unsaid through every one of its problems.
	assert.match(
		errorsOf({
			decisions: [below("why")],
			summary: { body: "A sentence.", basedOn: ["why-reason"] },
		}).join("\n"),
		/why is withheld as BELOW_BAR, and a text speaks about it or its problems/u,
	);
	assert.match(
		errorsOf({ decisions: [below("preview")] }).join("\n"),
		/preview is not a NOT_MET result/u,
	);
	// The decision is the result's: a problem's id is named back to its result.
	assert.match(
		errorsOf({ decisions: [below("why-reason")] }).join("\n"),
		/why-reason is a problem of why; a decision names its result's resultId/u,
	);
	assert.match(
		errorsOf({ decisions: [{ problemId: "why", disposition: "BELOW_BAR" }] }).join("\n"),
		/unknown decision field\(s\): problemId[\s\S]*resultId is required/u,
	);
	assert.match(
		errorsOf({ decisions: [{ resultId: "why", disposition: "BORED" }] }).join("\n"),
		/disposition must be one of RAISE, NO_MATERIAL_CHANGE, ALREADY_SAID, BELOW_BAR/u,
	);
});

void test("a raised result is spoken about through any of its problems", () => {
	const read = readReview(
		{
			decisions: [raise("why"), raise("colors"), below("handoff"), below("description")],
			summary: { body: "Say why, and adapt the fill.", basedOn: ["why-reason", "colors-fill"] },
		},
		results,
		false,
		reviewContext,
	);
	assert.ok("review" in read, JSON.stringify(read));
	assert.deepEqual([...decidedByReview(read.review)].toSorted(), [
		"colors",
		"description",
		"handoff",
		"why",
	]);
	assert.match(
		errorsOf({
			decisions: [raise("why"), raise("colors"), below("handoff"), below("description")],
			summary: { body: "Say why.", basedOn: ["why-reason"] },
		}).join("\n"),
		/colors is decided RAISE, and no text speaks about it or its problems/u,
	);
});

void test("a text is refused for what only the server writes, never for its words", () => {
	assert.ok(
		"review" in
			readReview(
				{
					summary: {
						body: "The acceptance criteria and your own assessment of the empty state are both missing.",
						basedOn: ["handoff"],
					},
					decisions: [raise("handoff"), below("colors"), below("why"), below("description")],
				},
				results,
				true,
				reviewContext,
			),
	);
	assert.match(
		errorsOf({
			summary: { body: "Fine. <!-- hephaestus:practice-review:x -->", basedOn: ["why"] },
		}).join("\n"),
		/may not contain an HTML comment/u,
	);
	assert.match(
		errorsOf({ summary: { body: 'Add the reason.}]"', basedOn: ["why"] } }).join("\n"),
		/left over from a JSON envelope/u,
	);
	assert.match(
		errorsOf({
			summary: { body: "x".repeat(REVIEW_LIMITS.summaryChars + 1), basedOn: ["why"] },
		}).join("\n"),
		/at most 8000 characters/u,
	);
});

void test("a review over the line-note bound is refused whole, not cut", () => {
	const notes = Array.from({ length: REVIEW_LIMITS.inlineNotes + 1 }, () => ({
		body: "x",
		basedOn: ["colors"],
		anchor: { problemId: "colors-fill", citationIndex: 0 },
	}));
	assert.match(
		errorsOf({ inline: notes }).join("\n"),
		/at most 30 line notes; this review has 31/u,
	);
});

void test("what a review decided covers the results it says, through any problem, and those it withholds", () => {
	assert.deepEqual(
		[
			...decidedByReview({
				summary: {
					body: "x",
					basedOn: [{ resultId: "why", problemId: "why-reason" }],
				},
				inline: [
					{
						body: "y",
						basedOn: [{ resultId: "colors" }],
						anchor: { problemId: "colors-fill", citationIndex: 0 },
					},
				],
				withheld: [{ basedOn: [{ resultId: "handoff" }], reason: "ALREADY_SAID" }],
			}),
		].toSorted(),
		["colors", "handoff", "why"],
	);
	assert.equal(decidedByReview(null).size, 0);
});

// --- What the review composition may see --------------------------------------------------------

const PRIVATE_SENTENCE = "Earlier reviews told this person the same thing twice before.";

/** An abstention's own prose can assert more than it decided; only its recorded limit is context. */
const CONFLICT_SUMMARY = "One ticket bundles three independently shippable deliverables";
const CONFLICT_RATIONALE = "Against the stated request, the scoping is plainly several concerns.";
const WITHDRAWN = "No CSV download is requested. This work is withdrawn.";

const admitted = [
	{
		resultId: "current",
		practiceSlug: "describe-what-and-why",
		outcome: "NOT_MET",
		summary: "The description gives no reason for the screen.",
		explanation: "The authored description names the screen and nothing about why.",
		publicEligible: true,
		citations: [],
		example: {
			citation: {
				sourceKind: "scm.pull-request.core",
				path: "description.md",
				quote: "Adds the login screen.",
				verification: { status: "VERIFIED", quoteSha256: "fed" },
			},
			note: "The first line already names what changed.",
		},
		problems: [
			{
				problemId: "current-reason",
				severity: "MINOR",
				summary: "No reason for the screen",
				citations: [
					{
						index: 0,
						sourceKind: "scm.pull-request.core",
						path: "description.md",
						quote: "Closes #4",
						verification: { status: "VERIFIED", quoteSha256: "abc" },
						anchorable: false,
					},
				],
				anchorable: false,
			},
		],
	},
	{
		resultId: "from-history",
		practiceSlug: "defers-review-asks-into-tracked-work",
		outcome: "NOT_MET",
		summary: PRIVATE_SENTENCE,
		explanation: PRIVATE_SENTENCE,
		publicEligible: false,
		citations: [],
		problems: [
			{
				problemId: "from-history-ask",
				summary: PRIVATE_SENTENCE,
				citations: [
					{ index: 0, sourceKind: "hephaestus.feedback-history", path: "history/feedback.json" },
				],
			},
		],
	},
];

void test("the review composition sees only the NOT_MET results admission marked eligible, and those whole", () => {
	// A public MET result is never acknowledged on the work, so the review composition never sees it.
	const shown = publicResults([
		...admitted,
		{
			resultId: "named",
			practiceSlug: "names-the-screen",
			outcome: "MET",
			summary: "The description names the screen.",
			publicEligible: true,
			citations: [],
			problems: [],
		},
	]);
	assert.deepEqual(
		shown.map((result) => result.resultId),
		["current"],
	);
	assert.equal(
		shown[0]?.explanation,
		"The authored description names the screen and nothing about why.",
	);
	// Every citation keeps its quoted lines and coordinates, the problems' and the example's too; only digests go.
	assert.deepEqual(shown[0].problems, [
		{
			problemId: "current-reason",
			severity: "MINOR",
			summary: "No reason for the screen",
			citations: [
				{
					index: 0,
					sourceKind: "scm.pull-request.core",
					path: "description.md",
					quote: "Closes #4",
					anchorable: false,
				},
			],
			anchorable: false,
		},
	]);
	assert.deepEqual(shown[0].example, {
		citation: {
			sourceKind: "scm.pull-request.core",
			path: "description.md",
			quote: "Adds the login screen.",
		},
		note: "The first line already names what changed.",
	});
	const read: string[] = [];
	const describeCriteria = "## The standard\nThe description says why the change is made.\n";
	const turn = buildReviewTurn({
		sameWork:
			"A scm.pull_request, captured at 2026-10-01T10:00:00Z. Its title: Add the login screen.",
		results: shown,
		qualifications: publicQualifications([
			...admitted,
			{
				resultId: "conflict",
				practiceSlug: "issue-scoped-to-single-concern",
				outcome: "COULD_NOT_DETERMINE",
				summary: CONFLICT_SUMMARY,
				explanation: CONFLICT_RATIONALE,
				publicEligible: true,
				evidence: {
					openQuestions: [
						{
							openQuestion:
								"Does the withdrawal or the delivery requirement state the current request?",
							wouldSettleIt: "An authored statement that resolves the two current sections.",
						},
					],
				},
				citations: [
					{
						index: 0,
						sourceKind: "scm.issue.core",
						path: "description.md",
						startLine: 3,
						quote: WITHDRAWN,
						verification: { status: "VERIFIED", quoteSha256: "def" },
					},
				],
			},
			{
				resultId: "na",
				practiceSlug: "ships-tests",
				outcome: "DOES_NOT_APPLY",
				summary: "No behaviour changed, so no test is owed.",
				explanation: "Nothing in this work needs a test.",
				publicEligible: true,
				evidence: {
					inapplicability: {
						consulted: ["scm.pull-request.diff"],
						prerequisite: "changed behaviour a test could cover",
						ruledOutBy: "the diff changes only the text of one label",
					},
				},
				citations: [{ index: 0, sourceKind: "scm.pull-request.diff", quote: "+ text" }],
			},
			{
				resultId: "reviewer-limit",
				practiceSlug: "reviews-respectfully-asks-rather-than-demands",
				outcome: "COULD_NOT_DETERMINE",
				summary: PRIVATE_SENTENCE,
				publicEligible: false,
				evidence: {
					openQuestions: [{ openQuestion: PRIVATE_SENTENCE, wouldSettleIt: PRIVATE_SENTENCE }],
				},
				citations: [],
			},
		]),
		alreadySaid: [],
		captured: {
			capturedAt: null,
			recipient: { author: null, authorId: null },
			sources: [],
			statements: [],
		},
		practices: [
			{
				slug: "describe-what-and-why",
				name: "Describe what changed and why",
				whyItMatters: "A reviewer needs the reason before the diff.",
				knownLimitations: ["The review cannot see conversations outside the change."],
			},
		],
		notReached: ["keeps-tests-honest"],
		lineNotes: true,
		stagedCriteria: (slug) => {
			read.push(slug);
			return describeCriteria;
		},
	});
	assert.ok(!turn.includes(PRIVATE_SENTENCE), turn);
	assert.ok(!turn.includes("from-history"), turn);
	// The work is named before anything the review may rest on.
	const record = turn.indexOf("Add the login screen.");
	assert.ok(record !== -1 && record < turn.indexOf('"resultId": "current"'), turn);
	// The whole standard of the NOT_MET practice comes before its result; a private practice's criteria are not read.
	assert.deepEqual(read, ["describe-what-and-why"]);
	const standard = turn.indexOf(`\`\`\`markdown\n${describeCriteria}\n\`\`\``);
	assert.ok(record < standard && standard < turn.indexOf('"resultId": "current"'), turn);
	// Nothing offers recognition: no MET result, no index of one, and no tool to read one.
	assert.doesNotMatch(turn, /"resultId": "named"|recognition|read_practice/u);
	// How to write ends the opening turn, after every reference it relies on.
	assert.ok(turn.endsWith(WRITE_CONTRACT), turn);
	// The concern carries its result with its problems nested, each by its own id.
	assert.match(turn, /"resultId": "current"/u);
	assert.match(turn, /"problemId": "current-reason"/u);
	assert.doesNotMatch(turn, /"id":/u);
	assert.match(turn, /"name": "Describe what changed and why"/u);
	assert.doesNotMatch(turn, /whyItMatters/u);
	assert.match(turn, /"knownLimitations":/u);
	// An eligible abstention shows the whole limit it recorded and its quoted lines, never its id or its prose.
	const limits = turn.slice(
		turn.indexOf("### Recorded limits of this review"),
		turn.indexOf("### Where the words go"),
	);
	assert.match(limits, /"openQuestion": "Does the withdrawal or the delivery requirement/u);
	assert.match(limits, /"wouldSettleIt": "An authored statement that resolves/u);
	assert.match(limits, /"prerequisite": "changed behaviour a test could cover"/u);
	assert.match(limits, /"ruledOutBy": "the diff changes only the text of one label"/u);
	assert.equal(turn.split(WITHDRAWN).length - 1, 1, turn);
	for (const hidden of [
		CONFLICT_SUMMARY,
		CONFLICT_RATIONALE,
		"No behaviour changed",
		"Nothing in this work needs a test",
		'"conflict"',
		'"na"',
		"quoteSha256",
		"reviews-respectfully-asks-rather-than-demands",
	]) {
		assert.ok(!turn.includes(hidden), hidden);
	}
	assert.match(turn, /did not settle one of its practices: keeps-tests-honest/u);
	assert.match(turn, /No same-work delivered feedback is shown here\./u);
	// A discussion this run did not capture is unknown, never an empty one.
	assert.match(turn, /was not part of this capture, so it is unknown/u);
});

void test("the public turn carries captured discussion once, with its locator and omission qualifications", () => {
	const captured = {
		capturedAt: "2026-10-06T09:00:00Z",
		recipient: { author: "developer", authorId: "10" },
		sources: [
			{
				kind: "scm.issue.comments",
				path: "context/comments.json",
				availability: "AVAILABLE",
				content: "NONEMPTY",
				completeness: "PARTIAL",
				limitations: ["Older comments are outside the captured window."],
				omitted: null,
				qualifications: [],
			},
		],
		statements: [
			{
				witnessId: "comment:context/comments.json:7",
				sourcePath: "context/comments.json",
				sourceKind: "scm.issue.comments",
				nativeId: "7",
				author: "reviewer",
				authorId: "11",
				origin: "UNKNOWN",
				body: "Please describe how to try the screen.",
				statedAt: "2026-10-06T08:00:00Z",
				updatedAt: null,
				itemVersion: null,
				eligibleForPriorAdvice: true,
				state: null,
				dismissed: null,
				outdated: null,
			},
		],
	} satisfies PublicReviewHistory;
	const turn = buildReviewTurn({
		sameWork: "The captured issue asks for a login screen.",
		results: [],
		qualifications: [],
		alreadySaid: [],
		captured,
		practices: [],
		notReached: [],
		lineNotes: false,
		stagedCriteria: () => null,
	});
	assert.equal(turn.split('"path": "context/comments.json"').length - 1, 1);
	assert.equal(turn.split("Please describe how to try the screen.").length - 1, 1);
	assert.ok(turn.includes('"witnessId": "comment:context/comments.json:7"'));
	assert.ok(turn.includes("Older comments are outside the captured window."));
	const omitted = "Captured discussion and its omission index exceed the size bound.";
	const withoutBodies = buildReviewTurn({
		sameWork: "The captured issue asks for a login screen.",
		results: [],
		qualifications: [],
		alreadySaid: [],
		captured: { ...captured, sources: [], statements: [], omitted },
		practices: [],
		notReached: [],
		lineNotes: false,
		stagedCriteria: () => null,
	});
	assert.ok(withoutBodies.includes(omitted));
});

void test("a result admission did not mark, or one that decided nothing, is left out", () => {
	assert.deepEqual(
		publicResults([{ resultId: "unmarked", outcome: "NOT_MET", citations: [], problems: [] }]),
		[],
	);
	assert.deepEqual(
		publicResults([
			{
				resultId: "unsure",
				outcome: "COULD_NOT_DETERMINE",
				publicEligible: true,
				citations: [],
				problems: [],
			},
		]),
		[],
	);
});

void test("a text resting on anything but result and problem ids is refused whole, never filtered", () => {
	assert.match(
		errorsOf({ summary: { body: "A sentence.", basedOn: ["why", 42] } }).join("\n"),
		/basedOn must be an array of result and problem id strings/u,
	);
	assert.match(
		errorsOf({ summary: { body: "A sentence.", basedOn: "why" } }).join("\n"),
		/basedOn must be an array of result and problem id strings/u,
	);
	assert.match(
		errorsOf({
			decisions: [{ resultId: "why", disposition: "ALREADY_SAID", witnessIds: [null] }],
		}).join("\n"),
		/witnessIds must be an array/u,
	);
});

void test("only what was said on this same work, on the work, is supplied as already said", () => {
	const work = "scm.pull_request:https://gitlab.example/group/repo/-/merge_requests/3";
	const history = {
		feedback: [
			{
				destination: "ON_WORK",
				publicEligible: true,
				item: {
					kind: "scm.pull_request",
					url: "https://gitlab.example/group/repo/-/merge_requests/3",
				},
				deliveredAt: "2026-10-05T09:00:00Z",
				body: "Earlier comment on this change.",
				recordedClaimCurrentness: "CURRENT",
			},
			{
				destination: "PRACTICE_PAGE",
				item: {
					kind: "scm.pull_request",
					url: "https://gitlab.example/group/repo/-/merge_requests/3",
				},
				body: "A card on their practice page.",
			},
			{
				destination: "ON_WORK",
				publicEligible: true,
				item: {
					kind: "scm.pull_request",
					url: "https://gitlab.example/group/repo/-/merge_requests/1",
				},
				body: "A comment on other work.",
			},
		],
	};
	assert.deepEqual(priorPublicFeedback(history, work, "2026-10-06T09:00:00Z").feedback, [
		{
			// The history names no id for it: shown as context, never as advice this work received.
			witnessId: null,
			deliveredAt: "2026-10-05T09:00:00Z",
			body: "Earlier comment on this change.",
			recordedClaimCurrentness: "CURRENT",
			withdrawn: undefined,
			eligibleForPriorAdvice: false,
			eligibleForAlreadySaid: false,
		},
	]);
	assert.deepEqual(priorPublicFeedback(history, undefined, "2026-10-06T09:00:00Z").feedback, []);
});

const historyFeedbackId = (n: number) => `00000000-0000-4000-8000-00000000000${n}`;

void test("own feedback on this work stands as prior advice only when named, current, shown and delivered before the capture", () => {
	const work = "scm.pull_request:https://gitlab.example/group/repo/-/merge_requests/3";
	const item = {
		kind: "scm.pull_request",
		url: "https://gitlab.example/group/repo/-/merge_requests/3",
	};
	const entry = (n: number, overrides: Record<string, unknown> = {}) => ({
		destination: "ON_WORK",
		publicEligible: true,
		item,
		id: historyFeedbackId(n),
		itemVersion: "a".repeat(40),
		basedOn: [{ problemId: `earlier-${n}` }],
		deliveredAt: "2026-10-05T09:00:00Z",
		body: `Delivered comment ${n}.`,
		recordedClaimCurrentness: "CURRENT",
		...overrides,
	});
	const history = {
		feedback: [
			entry(1),
			entry(2, { deliveredAt: "2026-10-07T09:00:00Z" }),
			entry(3, { recordedClaimCurrentness: "STALE", body: undefined }),
			entry(4, { withdrawn: true, body: undefined }),
			entry(5, { deliveredAt: undefined }),
		],
	};
	const said = priorPublicFeedback(history, work, "2026-10-06T09:00:00Z").feedback;
	assert.deepEqual(
		said.map((statement) => [statement.witnessId, statement.eligibleForPriorAdvice]),
		[
			[`feedback:${historyFeedbackId(1)}`, true],
			[`feedback:${historyFeedbackId(2)}`, false],
			[`feedback:${historyFeedbackId(3)}`, false],
			[`feedback:${historyFeedbackId(4)}`, false],
			[`feedback:${historyFeedbackId(5)}`, false],
		],
	);
	// What the history recorded about the feedback stays as it was staged.
	const original = said[0];
	assert.ok(original);
	assert.equal(original.itemVersion, "a".repeat(40));
	assert.deepEqual(original.basedOn, [{ problemId: "earlier-1" }]);
	// Without a known capture time nothing can be placed before it.
	assert.ok(
		priorPublicFeedback(history, work, null).feedback.every(
			(statement) => !statement.eligibleForPriorAdvice,
		),
	);
	const derived = priorAdviceWitnesses(said, [
		{
			witnessId: "github:review-comment:7",
			sourcePath: "context/review_threads.json",
			sourceKind: "scm.pull-request.review-threads",
			nativeId: "7",
			author: "tutor",
			authorId: "11",
			origin: "UNKNOWN",
			body: "Please describe how to try this.",
			statedAt: "2026-10-05T08:00:00Z",
			updatedAt: null,
			itemVersion: null,
			eligibleForPriorAdvice: true,
			state: null,
			dismissed: null,
			outdated: null,
		},
	]);
	assert.deepEqual(derived.get(`feedback:${historyFeedbackId(1)}`), {
		eligibleForPriorAdvice: true,
		eligibleForAlreadySaid: true,
	});
	assert.deepEqual(derived.get(`feedback:${historyFeedbackId(4)}`), {
		eligibleForPriorAdvice: false,
		eligibleForAlreadySaid: false,
	});
	assert.deepEqual(derived.get("github:review-comment:7"), {
		eligibleForPriorAdvice: true,
		eligibleForAlreadySaid: true,
	});
});

void test("own history omits whole oversized or over-budget entries without hiding later usable advice", () => {
	const item = {
		kind: "scm.pull_request",
		url: "https://gitlab.example/group/repo/-/merge_requests/3",
	};
	const entry = (n: number, body: string) => ({
		destination: "ON_WORK",
		publicEligible: true,
		item,
		id: historyFeedbackId(n),
		body,
		deliveredAt: "2026-10-05T09:00:00Z",
		recordedClaimCurrentness: "CURRENT",
	});
	const history = {
		feedback: [
			entry(1, "oversized ".repeat(300)),
			entry(2, "Useful earlier advice."),
			entry(3, "Long earlier advice. ".repeat(35)),
			entry(4, "Later usable advice."),
		],
	};
	const limits = { sourceChars: 1200, totalChars: 1300 };
	const view = priorPublicFeedback(
		history,
		"scm.pull_request:https://gitlab.example/group/repo/-/merge_requests/3",
		"2026-10-06T09:00:00Z",
		limits,
	);
	assert.deepEqual(
		view.feedback.map((row) => row.id),
		[historyFeedbackId(2), historyFeedbackId(4)],
	);
	assert.deepEqual(view.omissions, { oversizedEntries: 1, budgetEntries: 1 });
	const shownWitnesses = priorAdviceWitnesses(view.feedback, []);
	assert.ok(!shownWitnesses.has(`feedback:${historyFeedbackId(1)}`));
	assert.ok(!shownWitnesses.has(`feedback:${historyFeedbackId(3)}`));
	assert.equal(
		shownWitnesses.get(`feedback:${historyFeedbackId(4)}`)?.eligibleForPriorAdvice,
		true,
	);
	const input = {
		sameWork: "The captured work.",
		results: [],
		qualifications: [],
		alreadySaid: view.feedback,
		captured: {
			capturedAt: null,
			recipient: { author: null, authorId: null },
			sources: [],
			statements: [],
		},
		practices: [],
		notReached: [],
		lineNotes: false,
		stagedCriteria: () => null,
	};
	const turn = buildReviewTurn({ ...input, ownHistoryOmissions: view.omissions });
	const own = turn
		.split("### Already said on this work\n")[1]
		?.split("What people and tools said")[0];
	assert.ok(typeof own === "string");
	assert.ok(own.length <= limits.totalChars);
	assert.match(own, /1 entries exceed the per-entry limit/u);
	assert.match(own, /1 exceed the total history limit/u);
	assert.ok(!turn.includes(historyFeedbackId(1)));
	assert.ok(!turn.includes(historyFeedbackId(3)));
	assert.ok(!turn.includes("oversized"));
	assert.equal(
		buildReviewTurn(input),
		buildReviewTurn({ ...input, ownHistoryOmissions: { oversizedEntries: 0, budgetEntries: 0 } }),
	);
});

// --- The report_review schema of one run ---------------------------------------------------------

const decidedOnly = new Map([...results].filter(([id]) => id !== "unsure"));

const eligibleWitnesses = [
	"github:review-comment:7",
	"feedback:00000000-0000-4000-8000-000000000001",
];

const validatorFor = (reviewable: ReadonlyMap<string, ReviewedResult>, lineNotes = true) =>
	new Ajv({ strict: true, allErrors: true }).compile(
		reviewToolParameters(reviewable, lineNotes, eligibleWitnesses),
	);

void test("the native schema requires complete decisions and each text's fields with only permitted ids", () => {
	const valid = validatorFor(decidedOnly);
	const quiet = {
		decisions: [below("colors"), below("handoff"), below("why"), below("description")],
		summary: null,
		inline: [],
	};
	assert.ok(valid(quiet), JSON.stringify(valid.errors));
	assert.ok(!valid({}), "a report must deliberately account for every negative");
	assert.ok(!valid({ decisions: quiet.decisions, inline: [] }), "no summary is expressed as null");
	assert.ok(
		valid({
			decisions: allRaised,
			summary: {
				body: "Say why.",
				basedOn: ["why", "handoff-criteria", "description-steps"],
			},
			inline: [
				{
					body: "Adapt.",
					basedOn: ["colors-fill"],
					anchor: { problemId: "colors-fill", citationIndex: 1 },
				},
			],
		}),
		JSON.stringify(valid.errors),
	);
	for (const malformed of [
		{ ...quiet, decisions: [{ resultId: "handoff" }] },
		{ ...quiet, decisions: [{ problemId: "handoff", disposition: "BELOW_BAR" }] },
		{ ...quiet, decisions: [below("preview"), ...quiet.decisions.slice(1)] },
		// A decision names the result, never one of its problems.
		{ ...quiet, decisions: [below("colors-fill"), ...quiet.decisions.slice(1)] },
		{ ...quiet, summary: { body: "Say why." } },
		{ ...quiet, summary: { body: "Say why.", basedOn: [] } },
		{ ...quiet, summary: { body: "Say why.", basedOn: ["why", 42] } },
		{ ...quiet, summary: { body: "Say why.", basedOn: ["elsewhere"] } },
		{ ...quiet, summary: { body: "Say why.", basedOn: ["unsure"] } },
		// A MET result is never acknowledged on the work: the schema offers no MET id to rest on.
		{ ...quiet, summary: { body: "The preview helps.", basedOn: ["preview"] } },
		{ ...quiet, summary: { body: "x".repeat(REVIEW_LIMITS.summaryChars + 1), basedOn: ["why"] } },
		{ ...quiet, inline: [{ body: "Adapt.", basedOn: ["colors"] }] },
		// A note sits on a problem's citation: neither a result nor a problem without a line takes one.
		{
			...quiet,
			inline: [
				{ body: "x", basedOn: ["colors"], anchor: { problemId: "colors", citationIndex: 0 } },
			],
		},
		{
			...quiet,
			inline: [
				{
					body: "x",
					basedOn: ["why"],
					anchor: { problemId: "why-reason", citationIndex: 0 },
				},
			],
		},
		{ ...quiet, inline: Array.from({ length: REVIEW_LIMITS.inlineNotes + 1 }, () => note(0)) },
	]) {
		assert.ok(!valid(malformed), JSON.stringify(malformed));
	}
	const witnessed = {
		...quiet,
		decisions: [
			below("colors"),
			{
				resultId: "handoff",
				disposition: "ALREADY_SAID",
				witnessIds: ["github:review-comment:7"],
			},
			below("why"),
			below("description"),
		],
	};
	assert.ok(valid(witnessed), JSON.stringify(valid.errors));
	assert.ok(
		!valid({
			...witnessed,
			decisions: [
				below("colors"),
				{
					resultId: "handoff",
					disposition: "ALREADY_SAID",
					witnessIds: ["github:issue-comment:9"],
				},
				below("why"),
				below("description"),
			],
		}),
	);
});

void test("native Responses serialization keeps a quiet summary nullable without accepting malformed text", () => {
	const parameters = reviewToolParameters(decidedOnly, true, []);
	const quiet = {
		decisions: ["colors", "handoff", "why", "description"].map((resultId) => ({
			resultId,
			disposition: "BELOW_BAR",
			witnessIds: [],
		})),
		summary: null,
		inline: [],
	};
	const native = spawnSync(
		process.execPath,
		[
			"--experimental-import-meta-resolve",
			"--input-type=module",
			"-e",
			`import assert from "node:assert/strict";
const sdk = import.meta.resolve("@earendil-works/pi-coding-agent");
const { validateToolArguments } = await import(import.meta.resolve("@earendil-works/pi-ai", sdk));
const { stream } = await import(import.meta.resolve("@earendil-works/pi-ai/api/openai-responses", sdk));
const tool = { name: "report_review", description: "Store the whole review.", parameters: JSON.parse(process.argv[1]) };
const quiet = JSON.parse(process.argv[2]);
const { prepareReviewArguments } = await import(process.argv[3]);
const validate = (parameters, arguments_) => validateToolArguments({ ...tool, parameters }, { name: tool.name, arguments: prepareReviewArguments(arguments_) });
assert.deepEqual(validate(tool.parameters, quiet), quiet);
for (const value of [null, [], "", JSON.stringify(quiet)]) {
  assert.throws(() => validate(tool.parameters, value));
}
for (const summary of ["", false, 0, [], {}, { body: "Point", basedOn: [] }]) {
  assert.throws(() => validate(tool.parameters, { ...quiet, summary }));
}
let payload;
const model = { id: "configured-model", name: "configured-model", api: "openai-responses", provider: "hephaestus", baseUrl: "http://127.0.0.1:9/v1", reasoning: false, input: ["text"], cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 }, contextWindow: 128000, maxTokens: 1000 };
const answer = await stream(model, { messages: [{ role: "system", content: "Write the review.", toolsAdded: [tool], timestamp: 0 }, { role: "user", content: "Write it.", timestamp: 0 }] }, { apiKey: "synthetic", onPayload: (params) => { payload = params; throw new Error("stopped before any request left"); } }).result();
assert.equal(answer.stopReason, "error");
const outgoing = payload.tools.find((entry) => entry.name === tool.name);
assert.deepEqual(outgoing.parameters, tool.parameters);
assert.notEqual(outgoing.strict, true);
assert.deepEqual(validate(outgoing.parameters, quiet), quiet);
assert.throws(() => validate(outgoing.parameters, { ...quiet, summary: {} }));
`,
			JSON.stringify(parameters),
			JSON.stringify(quiet),
			new URL("../../../main/resources/agent/pi-runner-composition.ts", import.meta.url).href,
		],
		{ encoding: "utf8", cwd: process.cwd() },
	);
	assert.equal(native.status, 0, native.stderr);
	assert.ok("review" in readReview(quiet, decidedOnly, true, reviewContext));
});

void test("report_review exists only with a NOT_MET result to rest on, and work without lines accepts no notes", () => {
	// With only MET results, or only abstentions, no review on the work is composed.
	for (const id of ["preview", "unsure"]) {
		assert.throws(
			() => reviewToolParameters(new Map([...results].filter(([key]) => key === id)), true, []),
			/report_review needs at least one NOT_MET result to rest on/u,
		);
	}
	const issue = validatorFor(decidedOnly, false);
	assert.ok(!issue({ decisions: allRaised, inline: [note(0)] }));
	assert.deepEqual(
		reviewToolParameters(decidedOnly, true, []).properties.decisions.items.properties.disposition
			.enum,
		[...DISPOSITIONS],
	);
	const withoutWitness = new Ajv({ strict: true, allErrors: true }).compile(
		reviewToolParameters(decidedOnly, true, []),
	);
	assert.ok(
		!withoutWitness({
			decisions: [
				below("colors"),
				{
					resultId: "handoff",
					disposition: "ALREADY_SAID",
					witnessIds: ["github:review-comment:7"],
				},
				below("why"),
				below("description"),
			],
		}),
	);
});

void test("an empty or partial public review leaves negatives undecided", () => {
	const negative = new Map<string, ReviewedResult>([
		["one", { practiceSlug: "one", outcome: "NOT_MET", problems: [] }],
		["two", { practiceSlug: "two", outcome: "NOT_MET", problems: [] }],
	]);
	const empty = readReview({}, negative, false, reviewContext);
	assert.ok("errors" in empty);
	assert.match(empty.errors.join("\n"), /one, two has no decision/u);
	const partial = readReview(
		{
			decisions: [raise("one")],
			summary: { body: "The first description needs its purpose.", basedOn: ["one"] },
		},
		negative,
		false,
		reviewContext,
	);
	assert.ok("errors" in partial);
	assert.match(partial.errors.join("\n"), /two has no decision/u);
});

void test("summary-only support refuses a whole inline body while allowing its summary", () => {
	const support = new Map<string, ReviewedResult>([
		[
			"summary",
			{ practiceSlug: "description", outcome: "NOT_MET", problems: [], summaryOnly: true },
		],
		[
			"line",
			{
				practiceSlug: "code",
				outcome: "NOT_MET",
				problems: [{ problemId: "line-1", citations: [{ anchorable: true }] }],
			},
		],
	]);
	assert.ok(
		"errors" in
			readReview(
				{
					inline: [
						{
							body: "Both concerns.",
							basedOn: ["summary", "line"],
							anchor: { problemId: "line-1", citationIndex: 0 },
						},
					],
				},
				support,
				true,
				reviewContext,
			),
	);
	assert.ok(
		"review" in
			readReview(
				{
					decisions: [raise("summary"), raise("line")],
					summary: { body: "Both concerns.", basedOn: ["summary", "line"] },
				},
				support,
				true,
				reviewContext,
			),
	);
});

// --- The whole review: decisions and witnesses -----------------------------------------------------

/** Every problem decided: two raised and spoken about, two withheld. */
const wholeReview = (summaryBasedOn: string[]) => ({
	summary: { body: "Say why.", basedOn: summaryBasedOn },
	inline: [
		{
			body: "Use an adaptive color here.",
			basedOn: ["colors"],
			anchor: { problemId: "colors-fill", citationIndex: 0 },
		},
	],
	decisions: [raise("colors"), raise("why"), below("handoff"), below("description")],
});

void test("malformed lists and legacy selection or withholding authorities are refused whole", () => {
	const spoken = {
		decisions: allRaised,
		summary: { body: "Four concerns.", basedOn: ["colors", "handoff", "why", "description"] },
	};
	assert.ok("review" in readReview(spoken, results, true, reviewContext));
	assert.ok("review" in readReview({ ...spoken, inline: null }, results, true, reviewContext));
	for (const container of [{ basedOn: ["colors"], body: "One note." }, "colors", true]) {
		assert.deepEqual(errorsOf({ ...spoken, inline: container }), [
			"inline must be an array of line notes",
		]);
	}
	for (const container of [
		{ resultId: "handoff", disposition: "BELOW_BAR" },
		"BELOW_BAR",
		false,
		null,
	]) {
		assert.match(
			errorsOf({ ...spoken, decisions: container }).join("\n"),
			/decisions is required: an array/u,
		);
	}
	for (const field of ["selected", "selection", "withheld"]) {
		assert.deepEqual(errorsOf({ ...spoken, [field]: [] }), [
			`unknown review field(s): ${field} — a review takes decisions, summary and inline`,
		]);
	}
});

void test("a report decides every negative once and raises only spoken concerns", () => {
	assert.ok("review" in readReview(wholeReview(["why"]), results, true, reviewContext));
	assert.match(
		errorsOf({ ...wholeReview(["why"]), decisions: [raise("colors"), raise("why")] }).join("\n"),
		/handoff, description has no decision/u,
	);
	assert.match(
		errorsOf({
			...wholeReview(["why"]),
			decisions: [...wholeReview(["why"]).decisions, below("description")],
		}).join("\n"),
		/description already has a decision/u,
	);
	assert.match(
		errorsOf({
			...wholeReview(["why"]),
			decisions: [raise("colors"), raise("why"), raise("handoff"), below("description")],
		}).join("\n"),
		/handoff is decided RAISE, and no text speaks about it/u,
	);
	assert.match(
		errorsOf({
			...wholeReview(["why"]),
			decisions: [raise("colors"), raise("why"), below("elsewhere"), below("description")],
		}).join("\n"),
		/elsewhere is not one of the results/u,
	);
});

void test("a whole review that also rests on a MET result is refused, with that one reason", () => {
	const acknowledged = readReview(wholeReview(["why", "preview"]), results, true, reviewContext);
	assert.ok("errors" in acknowledged);
	assert.deepEqual(acknowledged.errors, [
		"summary: basedOn names preview, which is not a NOT_MET result or one of its problems. The review on the work speaks only about NOT_MET results and their problems; it never acknowledges a MET result",
	]);
});

/** The whole review with one decision about handoff in place of the given one. */
const withholding = (decision: Record<string, unknown>) => ({
	...wholeReview(["why"]),
	decisions: [raise("colors"), raise("why"), below("description"), decision],
});

void test("a withholding that says the advice was given names a statement that may stand as that advice", () => {
	for (const reason of ["ALREADY_SAID", "NO_MATERIAL_CHANGE"]) {
		assert.match(
			errorsOf(withholding({ resultId: "handoff", disposition: reason })).join("\n"),
			new RegExp(`decisions #4: ${reason} names in witnessIds`, "u"),
		);
	}
	assert.match(
		errorsOf(
			withholding({
				resultId: "handoff",
				disposition: "ALREADY_SAID",
				witnessIds: ["github:issue-comment:9"],
			}),
		).join("\n"),
		/github:issue-comment:9, which is shown as context but cannot stand as advice/u,
	);
	assert.match(
		errorsOf(
			withholding({
				resultId: "handoff",
				disposition: "ALREADY_SAID",
				witnessIds: ["github:review-comment:8"],
			}),
		).join("\n"),
		/github:review-comment:8, which is not a statement shown under what was already said/u,
	);
	// A human reviewer's request and Hephaestus's own delivered comment can each stand as the advice. The witness is
	// checked and not stored: the stored decision is its result and reason.
	for (const witness of [
		"github:review-comment:7",
		"feedback:00000000-0000-4000-8000-000000000001",
	]) {
		const read = readReview(
			withholding({
				resultId: "handoff",
				disposition: "NO_MATERIAL_CHANGE",
				witnessIds: [witness],
			}),
			results,
			true,
			reviewContext,
		);
		assert.ok("review" in read, JSON.stringify(read));
		assert.deepEqual(read.review.withheld, [
			{ basedOn: [{ resultId: "description" }], reason: "BELOW_BAR" },
			{ basedOn: [{ resultId: "handoff" }], reason: "NO_MATERIAL_CHANGE" },
		]);
	}
	// Below the bar is a judgement about this reader, not a claim about what was said before.
	assert.ok(
		"review" in
			readReview(
				withholding({ resultId: "handoff", disposition: "BELOW_BAR" }),
				results,
				true,
				reviewContext,
			),
	);
});

void test("a raised decision names no witness and unknown decision fields are refused", () => {
	assert.match(
		errorsOf({
			...wholeReview(["why"]),
			decisions: [
				raise("colors"),
				{ ...raise("why"), witnessIds: ["github:review-comment:7"] },
				below("handoff"),
				below("description"),
			],
		}).join("\n"),
		/a raised result names no witness/u,
	);
	assert.match(
		errorsOf({
			...wholeReview(["why"]),
			decisions: [
				raise("colors"),
				{ ...raise("why"), selected: true },
				below("handoff"),
				below("description"),
			],
		}).join("\n"),
		/unknown decision field\(s\): selected/u,
	);
});

void test("concern projection preserves new same-practice grounds and wrong old advice without deciding their match", () => {
	// Synthetic counterfactual: the old decoder claim is wrong; the newly changed status check is a distinct gap.
	const item = { kind: "scm.pull_request", url: "https://example.test/pull/1" };
	const old = {
		destination: "ON_WORK",
		publicEligible: true,
		item,
		id: historyFeedbackId(1),
		deliveredAt: "2026-10-05T09:00:00Z",
		recordedClaimCurrentness: "CURRENT",
		body: "The error object decodes into default repository fields.",
		basedOn: [{ practiceSlug: "handles-errors", outcome: "NOT_MET" }],
	};
	const history = priorPublicFeedback(
		{
			feedback: [
				old,
				{ ...old, id: historyFeedbackId(2), destination: "PRACTICE_PAGE", body: PRIVATE_SENTENCE },
				{
					...old,
					id: historyFeedbackId(3),
					item: { ...item, url: "https://example.test/pull/2" },
				},
				{ ...old, id: historyFeedbackId(4), withdrawn: true },
				{ ...old, id: historyFeedbackId(5), basedOn: "handles-errors" },
			],
		},
		`${item.kind}:${item.url}`,
		"2026-10-06T09:00:00Z",
	).feedback;
	const current = [
		{
			resultId: "new-status",
			practiceSlug: "handles-errors",
			outcome: "NOT_MET",
			publicEligible: true,
			summary: "The added request accepts a non-success response with a valid typed array.",
			explanation: "Conditional typed-array case, not an ordinary error-object decoder claim.",
			citations: [],
			problems: [
				{
					problemId: "new-status-check",
					summary: "A non-success status is accepted",
					citations: [{ path: "Loader.swift", startLine: 51, anchorable: true }],
				},
			],
		},
		{
			resultId: "caught-decode",
			practiceSlug: "handles-errors",
			outcome: "MET",
			publicEligible: true,
			summary: "Required fields are decoded inside a throwing try/catch.",
			explanation: "Required id, name and URL have no defaults; errors reach failed.",
			citations: [{ path: "Loader.swift", startLine: 63, anchorable: true }],
			problems: [],
		},
	];
	const [negative, positive] = current;
	assert.ok(negative !== undefined && positive !== undefined);
	const practices = [
		{
			slug: "handles-errors",
			name: "Handle errors",
			knownLimitations: ["No execution was captured."],
			whyItMatters: "Survey prose is not needed.",
		},
	];
	const turn = buildReviewTurn({
		sameWork: "Explain failed requests to the user.",
		results: current,
		alreadySaid: history,
		captured: {
			capturedAt: null,
			recipient: { author: null, authorId: null },
			sources: [],
			statements: [],
		},
		practices,
		qualifications: [],
		notReached: [],
		lineNotes: true,
		stagedCriteria: () => "Whole error standard.",
	});
	assert.equal(
		turn.split(old.body).length,
		history.filter((entry) => entry.body === old.body).length + 1,
	);
	assert.ok(turn.indexOf(old.body) < turn.indexOf('"resultId": "new-status"'));
	assert.ok(!turn.includes(PRIVATE_SENTENCE));
	assert.ok(!turn.includes(positive.explanation));
	assert.ok(!turn.includes("Survey prose is not needed."));
	const cases = [...turn.matchAll(/```json\n(?<body>[\s\S]*?)\n```/gu)].map((match): unknown =>
		JSON.parse(match.groups?.body ?? "null"),
	);
	const concern = cases.find(
		(entry) =>
			typeof entry === "object" &&
			entry !== null &&
			Reflect.get(entry, "candidatePriorWitnesses") !== undefined,
	);
	assert.ok(typeof concern === "object" && concern !== null);
	assert.deepEqual(Reflect.get(concern, "results"), [negative]);
	assert.deepEqual(Reflect.get(concern, "candidatePriorWitnesses"), [
		{
			witnessId: `feedback:${historyFeedbackId(1)}`,
			itemVersion: null,
			basedOn: old.basedOn,
			eligibleForAlreadySaid: true,
			eligibleForPriorAdvice: true,
		},
	]);
	// Same-practice history grants a witness, not an automatic suppression of materially new supported advice.
	const currentById = new Map<string, ReviewedResult>([
		[
			"new-status",
			{
				practiceSlug: "handles-errors",
				outcome: "NOT_MET",
				problems: [{ problemId: "new-status-check", citations: [] }],
			},
		],
	]);
	const decision = readReview(
		{
			decisions: [{ resultId: "new-status", disposition: "RAISE" }],
			summary: {
				body: "Check the added response status before accepting a typed array.",
				basedOn: ["new-status-check"],
			},
		},
		currentById,
		false,
		{ witnesses: priorAdviceWitnesses(history, []) },
	);
	assert.ok(!("errors" in decision));
});

void test("practice references pair recorded assessment coordinates without inferring progress", () => {
	const head = "a".repeat(40);
	const item = { kind: "scm.pull_request", url: "https://example.test/pull/1" };
	const current = [
		{
			resultId: "current",
			practiceSlug: "subjects",
			outcome: "NOT_MET",
			publicEligible: true,
			summary: "The unchanged subjects do not identify the changes.",
			citations: [],
			problems: [],
		},
	];
	const priorSupport = {
		id: "earlier",
		practiceSlug: "subjects",
		practiceVersion: { id: "10", number: 2 },
		outcome: "NOT_MET",
	};
	for (const priorHead of [head, "b".repeat(40), undefined]) {
		const history = priorPublicFeedback(
			{
				feedback: [
					{
						destination: "ON_WORK",
						publicEligible: true,
						item,
						id: historyFeedbackId(1),
						itemVersion: priorHead,
						deliveredAt: "2026-10-05T09:00:00Z",
						recordedClaimCurrentness: "STALE",
						body: "Explain what the subject changes.",
						basedOn: [priorSupport, { practiceSlug: "other", outcome: "MET" }],
					},
				],
			},
			`${item.kind}:${item.url}`,
			"2026-10-06T09:00:00Z",
		).feedback;
		const text = notMetReference(
			current,
			() => "The whole subject standard.",
			[{ slug: "subjects", name: "Commit subjects", versionId: "11", knownLimitations: [] }],
			history,
			head,
		);
		const body = /```json\n(?<body>[\s\S]*?)\n```/u.exec(text)?.groups?.body;
		assert.ok(body !== undefined);
		const shown: unknown = JSON.parse(body);
		assert.deepEqual(shown, {
			itemVersion: head,
			practice: {
				slug: "subjects",
				name: "Commit subjects",
				versionId: "11",
				knownLimitations: [],
			},
			results: current,
			candidatePriorWitnesses: [
				{
					witnessId: `feedback:${historyFeedbackId(1)}`,
					itemVersion: priorHead ?? null,
					basedOn: [priorSupport],
					eligibleForAlreadySaid: true,
					eligibleForPriorAdvice: false,
				},
			],
		});
		assert.equal(history[0]?.itemVersion, priorHead);
	}
});

const ENGAGEMENT_CRITERIA =
	"## The standard\nA review request is answered by a fix, a reasoned decline, or a clarification.\n\n" +
	"## Judge\n- MET: each request has one of those responses.\n- NOT_MET: a request has none.\n\n" +
	"```swift\n// a staged example stays inside its own fence\n```";

void test("the opening carries each NOT_MET practice's whole criteria once, and never a MET practice's", () => {
	const reviewable = publicResults([
		{
			resultId: "unanswered",
			practiceSlug: "engages-with-review",
			outcome: "NOT_MET",
			publicEligible: true,
			summary: "A design request has no reply",
			citations: [],
			problems: [],
		},
		{
			resultId: "unanswered-again",
			practiceSlug: "engages-with-review",
			outcome: "NOT_MET",
			publicEligible: true,
			summary: "A second request has no reply",
			citations: [],
			problems: [],
		},
		{
			resultId: "held",
			practiceSlug: "describe-what-and-why",
			outcome: "NOT_MET",
			publicEligible: true,
			summary: "The description gives no reason",
			citations: [],
			problems: [],
		},
		{
			resultId: "routine",
			practiceSlug: "ships-a-preview",
			outcome: "MET",
			publicEligible: true,
			citations: [],
			problems: [],
		},
	]);
	const read: string[] = [];
	const staged = (slug: string) => {
		read.push(slug);
		return slug === "engages-with-review" ? ENGAGEMENT_CRITERIA : `Whole criteria of ${slug}.`;
	};

	// Every NOT_MET practice's criterion opens the review, once and whole; a MET practice's is never read.
	assert.ok(!reviewable.some((result) => result.resultId === "routine"));
	const opening = notMetReference(reviewable, staged);
	assert.deepEqual(read, ["engages-with-review", "describe-what-and-why"]);
	const criteria = `\`\`\`\`markdown\n${ENGAGEMENT_CRITERIA}\n\`\`\`\``;
	assert.ok(opening.includes(criteria), opening);
	assert.equal(opening.split("### Criteria of `engages-with-review`").length, 2, opening);
	// What the standards are for is said once, before them.
	assert.equal(opening.split(STANDARD_REFERENCE).length, 2, opening);
	assert.ok(opening.indexOf(STANDARD_REFERENCE) < opening.indexOf(criteria), opening);
	assert.ok(opening.includes("Whole criteria of describe-what-and-why."), opening);
	assert.ok(!opening.includes("ships-a-preview"), opening);
});

const unreadableCriteria = (): string | null => {
	throw new Error("EACCES");
};

void test("a missing, unreadable, empty or blank criterion is shown as such and never counts as a standard", () => {
	const reviewable = publicResults([
		{
			resultId: "held",
			practiceSlug: "describe-what-and-why",
			outcome: "NOT_MET",
			publicEligible: true,
			summary: "The description gives no reason",
			citations: [],
			problems: [],
		},
		{
			resultId: "shared",
			practiceSlug: "describe-what-and-why",
			outcome: "MET",
			publicEligible: true,
			citations: [],
			problems: [],
		},
		{
			resultId: "kept",
			practiceSlug: "private-practice",
			outcome: "MET",
			publicEligible: false,
			citations: [],
			problems: [],
		},
	]);
	// Only the public NOT_MET result of a practice with both outcomes is shown; a private result never is.
	assert.deepEqual(
		reviewable.map((result) => result.resultId),
		["held"],
	);
	assert.equal(practiceStandard("describe-what-and-why", () => "Whole.").whole, true);
	for (const stagedCriteria of [() => null, () => "", () => " \n\t ", unreadableCriteria]) {
		assert.equal(practiceStandard("describe-what-and-why", stagedCriteria).whole, false);
	}
	// A blank criterion is still shown as staged, never rewritten.
	assert.ok(practiceStandard("describe-what-and-why", () => " \n\t ").text.includes(" \n\t "));

	const missing = notMetReference(reviewable, () => null);
	assert.ok(
		missing.includes("### Criteria of `describe-what-and-why` — not available for this review"),
		missing,
	);
	const empty = notMetReference(reviewable, () => "");
	assert.ok(empty.includes("### Criteria of `describe-what-and-why` — staged empty"), empty);
	const unreadableReference = notMetReference(reviewable, () => {
		throw new Error("EACCES");
	});
	assert.ok(
		unreadableReference.includes("### Criteria of `describe-what-and-why` — could not be read"),
		unreadableReference,
	);
});

void test("public history excludes unqualified legacy and reviewer advice while preserving author advice", () => {
	const item = {
		kind: "scm.pull_request",
		url: "https://gitlab.example/group/repo/-/merge_requests/3",
	};
	const row = {
		destination: "ON_WORK",
		item,
		body: "Prior advice",
		deliveredAt: "2026-10-05T09:00:00Z",
		recordedClaimCurrentness: "CURRENT",
	};
	const history = {
		feedback: [row, { ...row, publicEligible: false }, { ...row, publicEligible: true }],
	};
	assert.equal(
		priorPublicFeedback(history, `${item.kind}:${item.url}`, "2026-10-06T09:00:00Z").feedback
			.length,
		1,
	);
});

void test("unavailable historical words retain the delivery fact without supplying a novelty witness", () => {
	const item = {
		kind: "scm.pull_request",
		url: "https://gitlab.example/group/repo/-/merge_requests/3",
	};
	const view = priorPublicFeedback(
		{
			feedback: [
				{
					id: historyFeedbackId(1),
					destination: "ON_WORK",
					publicEligible: true,
					item,
					deliveredAt: "2026-10-05T09:00:00Z",
					recordedClaimCurrentness: "CURRENT",
					wordsUnavailable: "COPY_AUTHORITY_UNAVAILABLE",
				},
			],
		},
		`${item.kind}:${item.url}`,
		"2026-10-06T09:00:00Z",
	);
	assert.equal(view.feedback.length, 1);
	const [row] = view.feedback;
	assert.ok(row);
	assert.equal(row.wordsUnavailable, "COPY_AUTHORITY_UNAVAILABLE");
	assert.equal(row.body, undefined);
	assert.equal(row.eligibleForAlreadySaid, false);
	assert.equal(row.eligibleForPriorAdvice, false);
	assert.deepEqual(
		priorAdviceWitnesses(view.feedback, []).get(`feedback:${historyFeedbackId(1)}`),
		{ eligibleForPriorAdvice: false, eligibleForAlreadySaid: false },
	);
});

void test("a delivery after work capture informs novelty without becoming advice the captured work answered", () => {
	const item = {
		kind: "scm.pull_request",
		url: "https://gitlab.example/group/repo/-/merge_requests/3",
	};
	const history = {
		feedback: [
			{
				id: historyFeedbackId(1),
				destination: "ON_WORK",
				publicEligible: true,
				item,
				body: "Handle the failure visibly.",
				deliveredAt: "2026-10-07T10:00:00Z",
				recordedClaimCurrentness: "CURRENT",
			},
		],
	};
	const original = JSON.stringify(history);
	const said = priorPublicFeedback(
		history,
		`${item.kind}:${item.url}`,
		"2026-10-07T09:00:00Z",
		undefined,
		"2026-10-07T11:00:00Z",
	).feedback;
	const late = said.at(0);
	assert.ok(late !== undefined);
	assert.equal(late.eligibleForAlreadySaid, true);
	assert.equal(late.eligibleForPriorAdvice, false);
	const proof = priorAdviceWitnesses(said, []);
	const currentRows = new Map<string, ReviewedResult>([
		["current", { practiceSlug: "errors", outcome: "NOT_MET", problems: [] }],
	]);
	const selected = (reason: string) =>
		readReview(
			{
				decisions: [
					{
						resultId: "current",
						disposition: reason,
						witnessIds: [`feedback:${historyFeedbackId(1)}`],
					},
				],
			},
			currentRows,
			false,
			{ witnesses: proof },
		);
	assert.ok(!("errors" in selected("ALREADY_SAID")));
	assert.ok("errors" in selected("NO_MATERIAL_CHANGE"));
	assert.equal(JSON.stringify(history), original);
	const unknownCapture = priorPublicFeedback(
		history,
		`${item.kind}:${item.url}`,
		null,
		undefined,
		"2026-10-07T11:00:00Z",
	).feedback;
	const unknownCutoff = unknownCapture.at(0);
	assert.ok(unknownCutoff !== undefined);
	assert.equal(unknownCutoff.eligibleForAlreadySaid, true);
	assert.equal(unknownCutoff.eligibleForPriorAdvice, false);
	const turn = buildReviewTurn({
		sameWork: "Captured work without a known capture time.",
		results: [],
		qualifications: [],
		alreadySaid: unknownCapture,
		ownHistoryReadAt: "2026-10-07T11:00:00Z",
		captured: {
			capturedAt: null,
			recipient: { author: null, authorId: null },
			sources: [],
			statements: [],
		},
		practices: [],
		notReached: [],
		lineNotes: false,
		stagedCriteria: () => null,
	});
	assert.ok(turn.includes('"eligibleForAlreadySaid": true'));
	assert.ok(!turn.includes("was delivered after this work was captured"));
	const unknownDelivery = priorPublicFeedback(
		{ feedback: history.feedback.map((row) => ({ ...row, deliveredAt: null })) },
		`${item.kind}:${item.url}`,
		"2026-10-07T09:00:00Z",
		undefined,
		"2026-10-07T11:00:00Z",
	).feedback;
	const unknownTime = unknownDelivery.at(0);
	assert.ok(unknownTime !== undefined);
	assert.equal(unknownTime.deliveredAt, null);
	assert.equal(unknownTime.eligibleForAlreadySaid, false);
	assert.equal(unknownTime.eligibleForPriorAdvice, false);
	assert.equal(
		priorPublicFeedback(
			history,
			`${item.kind}:${item.url}`,
			"2026-10-07T10:30:00Z",
			undefined,
			"2026-10-07T11:00:00Z",
		).feedback[0]?.eligibleForPriorAdvice,
		true,
	);
	assert.equal(
		priorPublicFeedback(
			history,
			`${item.kind}:${item.url}`,
			"2026-10-07T09:00:00Z",
			undefined,
			"2026-10-07T09:30:00Z",
		).feedback[0]?.eligibleForAlreadySaid,
		false,
	);
	for (const change of [
		{ withdrawn: true },
		{ recordedClaimCurrentness: "UNVERIFIABLE" },
		{ body: undefined },
		{ publicEligible: false },
		{ destination: "CONVERSATION" },
		{ item: { ...item, url: "https://gitlab.example/group/repo/-/merge_requests/4" } },
	]) {
		const limited = priorPublicFeedback(
			{ feedback: history.feedback.map((row) => ({ ...row, ...change })) },
			`${item.kind}:${item.url}`,
			"2026-10-07T09:00:00Z",
			undefined,
			"2026-10-07T11:00:00Z",
		);
		assert.ok(limited.feedback.every((row) => !row.eligibleForAlreadySaid));
	}
});

void test("STALE delivered words permit ALREADY_SAID by the read time, not NO_MATERIAL_CHANGE", () => {
	const item = {
		kind: "scm.pull_request",
		url: "https://gitlab.example/group/repo/-/merge_requests/3",
	};
	const stale = {
		id: historyFeedbackId(1),
		destination: "ON_WORK",
		publicEligible: true,
		item,
		body: "Handle the failure visibly.",
		deliveredAt: "2026-10-07T08:00:00Z",
		recordedClaimCurrentness: "STALE",
	};
	const read = (row: Record<string, unknown>, readAt = "2026-10-07T11:00:00Z") =>
		priorPublicFeedback(
			{ feedback: [row] },
			`${item.kind}:${item.url}`,
			"2026-10-07T09:00:00Z",
			undefined,
			readAt,
		).feedback;
	const said = read(stale);
	// Delivered before the capture, yet stale: a record of what was said, not a current claim.
	assert.deepEqual(
		said.map((row) => [
			row.recordedClaimCurrentness,
			row.body,
			row.eligibleForAlreadySaid,
			row.eligibleForPriorAdvice,
		]),
		[["STALE", stale.body, true, false]],
	);
	const proof = priorAdviceWitnesses(said, []);
	const currentRows = new Map<string, ReviewedResult>([
		["current", { practiceSlug: "errors", outcome: "NOT_MET", problems: [] }],
	]);
	const selected = (reason: string) =>
		readReview(
			{
				decisions: [
					{
						resultId: "current",
						disposition: reason,
						witnessIds: [`feedback:${historyFeedbackId(1)}`],
					},
				],
			},
			currentRows,
			false,
			{ witnesses: proof },
		);
	assert.ok(!("errors" in selected("ALREADY_SAID")));
	assert.ok("errors" in selected("NO_MATERIAL_CHANGE"));
	assert.equal(read(stale, "2026-10-07T07:30:00Z")[0]?.eligibleForAlreadySaid, false);
	for (const change of [
		{ body: undefined },
		{ body: " " },
		{ withdrawn: true },
		{ recordedClaimCurrentness: undefined },
	]) {
		assert.ok(
			read({ ...stale, ...change }).every(
				(row) => !row.eligibleForAlreadySaid && !row.eligibleForPriorAdvice,
			),
		);
	}
});
