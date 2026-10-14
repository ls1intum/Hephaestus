import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
	type Outcome,
	OUTCOMES,
	parseCandidate,
} from "../../../../../../docker/agents/practice-scripts/lib/core/contract.ts";
import { folderCitationIndex } from "../../../main/resources/agent/pi-folder-index.ts";
import {
	CONVERSATION_THREAD,
	citationMatchesSourceFile,
	citesReviewedTurn,
	describeCitationMismatch,
	MAX_SUMMARY_CHARS,
	type NormalizedCitation,
	normalizeCitation,
	normalizePracticeSlug,
	normalizeSubmission,
	resolveQuote,
	resolveCapturedText,
	submissionCitations,
	validateEvidenceSources,
	validateInapplicabilityScope,
	validateSearchScope,
	withoutCoordinates,
} from "../../../main/resources/agent/pi-result-normalize.ts";

const DIFF_CITATION = {
	sourceKind: "scm.pull-request.diff",
	sourcePath: "inputs/context/diff.patch",
	path: "src/Auth.java",
	side: "NEW",
	startLine: 10,
	endLine: 10,
	quote: "+ insecure();",
};

/** A fresh copy of a citation, so that a test can change it. */
function citationFixture(overrides: Record<string, unknown> = {}): Record<string, unknown> {
	return { ...DIFF_CITATION, ...overrides };
}

function problemFixture(overrides: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		severity: "MAJOR",
		summary: "Insecure call added to the auth path",
		explanation: "The change calls insecure() where a request is authorized.",
		citations: [citationFixture()],
		basis: { type: "introduced", head: 0, explanation: "The change adds the call." },
		...overrides,
	};
}

/** A NOT_MET result with one problem. */
function baseSubmission(overrides: Record<string, unknown> = {}): Record<string, unknown> {
	return {
		outcome: "NOT_MET",
		summary: "PR mixes unrelated changes",
		explanation: "The diff touches auth and billing in one PR.",
		citations: [citationFixture()],
		problems: [problemFixture()],
		evidence: {},
		...overrides,
	};
}

/** A submission that cites one place of the work: the citation under test. */
function citing(citation: Record<string, unknown>): Record<string, unknown> {
	return baseSubmission({ citations: [citation] });
}

function onlyCitation<T extends object>(citations: readonly T[]): T {
	const [citation] = citations;
	if (!citation) {
		throw new Error("expected exactly one citation");
	}
	return citation;
}

/** The result citation of a normalized submission that cites one place. */
function normalizedCitation(citation = citationFixture()): NormalizedCitation {
	return onlyCitation(normalizeSubmission(citing(citation)).citations);
}

/** The message of a refusal. */
function refusal(raw: unknown, notes: string[] = []): string {
	try {
		normalizeSubmission(raw, notes);
	} catch (error) {
		return error instanceof Error ? error.message : String(error);
	}
	return assert.fail("expected the submission to be refused");
}

const goodSearch = {
	consulted: ["scm.review-threads"],
	lookedFor: "a review thread raising the migration",
	boundary: "only threads on this pull request; nothing in chat",
};

const goodInapplicability = {
	consulted: ["scm.pull-request.diff"],
	prerequisite: "error handling around outbound network calls",
	ruledOutBy: "the change touches only Markdown documentation and makes no network calls",
};

const UNDECIDABLE = {
	openQuestion: "Whether the body states a why, or only restates the title",
	wouldSettleIt: "Clarification of the contradictory acceptance requirements",
};

const EXAMPLE = {
	citation: {
		sourceKind: "scm.pull-request.core",
		sourcePath: "inputs/context/metadata.json",
		path: "inputs/context/metadata.json",
		startLine: 2,
		endLine: 2,
		quote: '"title" : "Split auth from billing"',
	},
	note: "The title names the one change that the pull request makes.",
};

/** A valid submission of each outcome, with every part that the outcome permits. */
function resultOf(outcome: Outcome): Record<string, unknown> {
	const question = {
		...UNDECIDABLE,
		citation: citationFixture({ path: "src/Billing.java", quote: "+ charge();" }),
	};
	switch (outcome) {
		case "MET": {
			return baseSubmission({
				outcome,
				problems: undefined,
				example: EXAMPLE,
				evidence: { search: goodSearch },
			});
		}
		case "NOT_MET": {
			return baseSubmission({
				citations: [],
				example: EXAMPLE,
				evidence: { search: goodSearch, openQuestions: [question] },
			});
		}
		case "DOES_NOT_APPLY": {
			return baseSubmission({
				outcome,
				problems: undefined,
				evidence: { inapplicability: goodInapplicability },
			});
		}
		case "COULD_NOT_DETERMINE": {
			return baseSubmission({
				outcome,
				problems: undefined,
				evidence: { search: goodSearch, openQuestions: [question] },
			});
		}
	}
}

// ── The submission ───────────────────────────────────────────────────────────

void test("each outcome is accepted with every part that it permits", () => {
	for (const outcome of OUTCOMES) {
		const submission = normalizeSubmission(resultOf(outcome));
		assert.equal(submission.outcome, outcome);
		assert.equal("problems" in submission, outcome === "NOT_MET");
		assert.equal("severity" in submission, false);
		assert.equal("example" in submission, outcome === "MET" || outcome === "NOT_MET");
		assert.deepEqual(
			Object.keys(submission.evidence).toSorted(),
			{
				MET: ["search"],
				NOT_MET: ["openQuestions", "search"],
				DOES_NOT_APPLY: ["inapplicability"],
				COULD_NOT_DETERMINE: ["openQuestions", "search"],
			}[outcome],
		);
	}
});

void test("a normalized submission with host coverage passes the contract for each outcome", () => {
	const coverage = {
		source: "host",
		reads: 1,
		truncatedReads: 0,
		failedReads: 0,
		receipts: [],
		complete: true,
	};
	for (const outcome of OUTCOMES) {
		const parsed = parseCandidate({ ...normalizeSubmission(resultOf(outcome)), coverage });
		assert.deepEqual(parsed.ok ? "ok" : `${parsed.reasonCode}: ${parsed.message}`, "ok", outcome);
	}
});

void test("a NOT_MET result lists its problems, and no other outcome has one", () => {
	assert.match(
		refusal(baseSubmission({ problems: undefined })),
		/^a NOT_MET result lists its problems/u,
	);
	assert.match(refusal(baseSubmission({ problems: [] })), /^a NOT_MET result lists its problems/u);
	assert.match(
		refusal(baseSubmission({ outcome: "MET" })),
		/^a MET result has no problems: a problem makes it NOT_MET$/u,
	);
	assert.match(
		refusal({ ...resultOf("COULD_NOT_DETERMINE"), problems: [problemFixture()] }),
		/^an? COULD_NOT_DETERMINE result has no problems/u,
	);
	// An empty list on another outcome carries nothing.
	assert.throws(() => normalizeSubmission({ ...resultOf("MET"), problems: [] }), /no problems/u);
});

void test("a NOT_MET result may cite nothing itself; every other outcome cites what it rests on", () => {
	assert.deepEqual(normalizeSubmission(baseSubmission({ citations: [] })).citations, []);
	assert.deepEqual(normalizeSubmission(baseSubmission({ citations: undefined })).citations, []);
	for (const outcome of ["MET", "DOES_NOT_APPLY", "COULD_NOT_DETERMINE"] as const) {
		assert.match(
			refusal({ ...resultOf(outcome), citations: [] }),
			new RegExp(
				`^citations are required: a ${outcome} result cites the evidence it rests on$`,
				"u",
			),
		);
	}
});

void test("result severity is refused on every outcome", () => {
	for (const outcome of ["MET", "NOT_MET", "DOES_NOT_APPLY", "COULD_NOT_DETERMINE"] as const) {
		assert.throws(
			() => normalizeSubmission({ ...resultOf(outcome), severity: "CRITICAL" }),
			/unknown|severity/u,
		);
	}
});

void test("each problem names its severity, summary, explanation, citations and basis", () => {
	const problem = onlyCitation(
		normalizeSubmission(
			baseSubmission({
				problems: [problemFixture({ key: "Insecure_Call", nextStep: "  Remove the call.  " })],
			}),
		).problems ?? [],
	);
	assert.equal(problem.key, "insecure-call");
	assert.equal(problem.nextStep, "Remove the call.");
	assert.deepEqual(problem.basis, {
		type: "introduced",
		head: 0,
		explanation: "The change adds the call.",
	});
	const message = refusal(
		baseSubmission({
			problems: [
				problemFixture({
					severity: undefined,
					summary: "Test",
					explanation: "",
					citations: [],
					basis: undefined,
					label: "x",
				}),
			],
		}),
	);
	assert.match(message, /^problem 1 has unknown field\(s\): label; it has only key, severity/u);
	assert.match(message, /; also: problem 1: invalid severity 'undefined' \(missing\)/u);
	assert.match(message, /; also: problem 1: summary must say what was observed as a short phrase/u);
	assert.match(message, /; also: problem 1: explanation is required/u);
	assert.match(message, /; also: problem 1: citations are required/u);
	assert.match(message, /; also: problem 1: basis is required/u);
	assert.match(
		refusal(baseSubmission({ problems: [problemFixture({ key: "not a slug!" })] })),
		/^problem 1: key must be a short slug/u,
	);
	assert.match(refusal(baseSubmission({ problems: ["gap"] })), /^problem 1 must be an object/u);
});

void test("a basis names a known type and only citations the problem has", () => {
	const twoCited = (basis: Record<string, unknown>) =>
		baseSubmission({
			problems: [
				problemFixture({
					citations: [citationFixture(), citationFixture({ side: "OLD", quote: "- secure();" })],
					basis,
				}),
			],
		});
	const because = { explanation: "The change replaced the check." };
	for (const basis of [
		{ type: "worsened", base: 1, head: 0, ...because },
		{ type: "duty", criterionQuote: "Every request is authorized.", event: 1, ...because },
		{ type: "state", ...because },
	]) {
		assert.deepEqual(
			onlyCitation(normalizeSubmission(twoCited(basis)).problems ?? []).basis,
			basis,
		);
	}
	assert.match(
		refusal(twoCited({ type: "worsened", base: 0, head: 2, ...because })),
		/^problem 1: basis\.head names citation 2, and the problem has 2 citation\(s\); the index is 0-based$/u,
	);
	assert.match(
		refusal(twoCited({ type: "introduced", head: "first", ...because })),
		/^problem 1: basis\.head must be the 0-based index/u,
	);
	assert.match(
		refusal(twoCited({ type: "caused", ...because })),
		/^problem 1: invalid basis type 'caused': one of introduced, worsened, duty, state$/u,
	);
	assert.match(
		refusal(twoCited({ type: "duty", event: 0, ...because })),
		/^problem 1: basis\.criterionQuote is required/u,
	);
	assert.match(
		refusal(twoCited({ type: "state", head: 0, ...because })),
		/^problem 1 basis of type state has unknown field\(s\): head; it has only type, explanation$/u,
	);
});

void test("two problems do not share a key", () => {
	assert.match(
		refusal(
			baseSubmission({
				problems: [
					problemFixture({ key: "missing_test" }),
					problemFixture({ key: "Missing-Test" }),
				],
			}),
		),
		/^problems 1 and 2 share the key 'missing-test': each key names one problem$/u,
	);
});

void test("an example belongs only to a MET or NOT_MET result", () => {
	assert.deepEqual(normalizeSubmission(resultOf("MET")).example, EXAMPLE);
	for (const outcome of ["DOES_NOT_APPLY", "COULD_NOT_DETERMINE"] as const) {
		assert.match(
			refusal({ ...resultOf(outcome), example: EXAMPLE }),
			new RegExp(
				`^example is permitted only for MET or NOT_MET; a ${outcome} result has none$`,
				"u",
			),
		);
	}
	assert.match(
		refusal({ ...resultOf("MET"), example: { citation: EXAMPLE.citation, note: "" } }),
		/^example\.note is required/u,
	);
	assert.match(
		refusal({ ...resultOf("MET"), example: { note: EXAMPLE.note } }),
		/^example\.citation is required/u,
	);
	assert.match(
		refusal({ ...resultOf("MET"), example: { ...EXAMPLE, note: "x ".repeat(141) } }),
		/^example\.note must be at most 280 characters/u,
	);
});

void test("an COULD_NOT_DETERMINE result records each open question it could not settle", () => {
	assert.match(
		refusal({ ...resultOf("COULD_NOT_DETERMINE"), evidence: {} }),
		/^an COULD_NOT_DETERMINE result must say what it could not settle: evidence\.openQuestions lists each open question/u,
	);
	assert.match(
		refusal({ ...resultOf("COULD_NOT_DETERMINE"), evidence: { openQuestions: [] } }),
		/^an COULD_NOT_DETERMINE result must say what it could not settle/u,
	);
	assert.match(
		refusal({
			...resultOf("COULD_NOT_DETERMINE"),
			evidence: { openQuestions: [{ openQuestion: "Why?" }] },
		}),
		/^open question 1: wouldSettleIt is required$/u,
	);
	assert.match(
		refusal({
			...resultOf("COULD_NOT_DETERMINE"),
			evidence: { openQuestions: [{ ...UNDECIDABLE, citation: { path: "a.ts", startLine: 1 } }] },
		}),
		/^open question 1 citation: evidence citation sourceKind is required$/u,
	);
	// An open question may stand without a citation.
	assert.deepEqual(
		normalizeSubmission({
			...resultOf("COULD_NOT_DETERMINE"),
			evidence: { openQuestions: [UNDECIDABLE] },
		}).evidence.openQuestions,
		[UNDECIDABLE],
	);
});

void test("the warrants follow the outcome", () => {
	assert.match(
		refusal({
			...resultOf("DOES_NOT_APPLY"),
			evidence: { inapplicability: goodInapplicability, search: goodSearch },
		}),
		/^evidence\.search is not permitted for DOES_NOT_APPLY: a DOES_NOT_APPLY result carries evidence\.inapplicability only/u,
	);
	assert.match(
		refusal(baseSubmission({ evidence: { inapplicability: goodInapplicability } })),
		/^evidence\.inapplicability is permitted only for DOES_NOT_APPLY$/u,
	);
	assert.match(
		refusal({ ...resultOf("MET"), evidence: { openQuestions: [UNDECIDABLE] } }),
		/^evidence\.openQuestions is permitted only for NOT_MET or COULD_NOT_DETERMINE$/u,
	);
	assert.match(
		refusal({ ...resultOf("DOES_NOT_APPLY"), evidence: {} }),
		/^a DOES_NOT_APPLY result must say why the practice does not apply: evidence\.inapplicability with consulted, prerequisite and ruledOutBy\. If you looked and could not tell, say COULD_NOT_DETERMINE instead$/u,
	);
	assert.match(
		refusal({
			...resultOf("DOES_NOT_APPLY"),
			evidence: { inapplicability: { ...goodInapplicability, ruledOutBy: "" } },
		}),
		/If you are merely unsure, the answer is COULD_NOT_DETERMINE, not DOES_NOT_APPLY$/u,
	);
	assert.match(
		refusal({ ...resultOf("MET"), evidence: { search: goodSearch, problems: [] } }),
		/^unknown evidence field\(s\): problems; evidence has only search, inapplicability, openQuestions$/u,
	);
});

void test("retired result fields and loose warrants are refused", () => {
	for (const fields of [
		{ practiceSlug: "another" },
		{ coverage: {} },
		{ evidenceRationale: "old" },
		{ search: goodSearch },
		{ lookedFor: "a handler" },
		{ openQuestions: UNDECIDABLE },
	]) {
		assert.throws(() => normalizeSubmission(baseSubmission(fields)), /unknown.*field/u);
	}
	assert.throws(
		() => normalizeSubmission(baseSubmission({ evidence: { citations: [citationFixture()] } })),
		/unknown.*field/u,
	);
	assert.throws(
		() =>
			normalizeSubmission(
				baseSubmission({
					outcome: "COULD_NOT_DETERMINE",
					problems: undefined,
					evidence: { openQuestions: UNDECIDABLE },
				}),
			),
		/list|array/u,
	);
});

void test("explicit null warrants and citation fields record the same evidence as absence", () => {
	for (const outcome of OUTCOMES) {
		const raw = resultOf(outcome);
		const sent = isEvidence(raw.evidence) ? raw.evidence : {};
		const normalized = normalizeSubmission({
			...raw,
			citations: [citationFixture({ side: null, revision: null, endLine: null, quote: null })],
			example: null,
			evidence: { search: null, inapplicability: null, openQuestions: null, ...sent },
		});
		const { side: _side, ...plain } = DIFF_CITATION;
		assert.deepEqual(normalized.citations, [{ ...plain, endLine: plain.startLine, quote: "" }]);
		assert.equal("example" in normalized, false);
		assert.deepEqual(Object.keys(normalized.evidence).toSorted(), Object.keys(sent).toSorted());
	}
});

function isEvidence(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

void test("submissionCitations lists the result's, each problem's, the example's, then each open question's", () => {
	const at = (path: string) => citationFixture({ path });
	const submission = normalizeSubmission(
		baseSubmission({
			citations: [at("a.ts")],
			problems: [
				problemFixture({ citations: [at("b.ts"), at("c.ts")] }),
				problemFixture({ citations: [at("d.ts")] }),
			],
			example: { ...EXAMPLE, citation: at("e.ts") },
			evidence: {
				openQuestions: [UNDECIDABLE, { ...UNDECIDABLE, citation: at("f.ts") }],
			},
		}),
	);
	const listed = submissionCitations(submission);
	assert.deepEqual(
		listed.map((citation) => citation.path),
		["a.ts", "b.ts", "c.ts", "d.ts", "e.ts", "f.ts"],
	);
	// The objects are the submission's own: a quote repaired in the list is repaired in the submission.
	for (const citation of listed) {
		citation.quote = "repaired";
	}
	assert.equal(submission.example?.citation.quote, "repaired");
	assert.equal(submission.evidence.openQuestions?.[1]?.citation?.quote, "repaired");
});

void test("every problem of a submission is named in one refusal", () => {
	const longSummary = "word ".repeat(40).trim();
	assert.ok(longSummary.length > MAX_SUMMARY_CHARS);
	assert.match(
		refusal(
			baseSubmission({ outcome: "PASSED", summary: "Test", explanation: " ", confidence: 3 }),
		),
		/^result has unknown field\(s\): confidence; it has only outcome, summary, explanation, citations, problems, example, evidence; also: invalid outcome 'PASSED': one of MET, NOT_MET, DOES_NOT_APPLY, COULD_NOT_DETERMINE; also: summary must say what was observed as a short phrase[^;]*; also: explanation is required: say why the evidence supports this outcome$/u,
	);
	const many = refusal(
		baseSubmission({
			summary: longSummary,
			explanation: "",
			citations: [citationFixture({ startLine: 0 }), citationFixture({ sourcePath: "" })],
			problems: [problemFixture({ basis: { type: "introduced", head: 4, explanation: "x" } })],
			example: { citation: EXAMPLE.citation, note: "" },
		}),
	);
	for (const part of [
		/^summary must be at most 160 characters; this one is \d+\. Resend the result/u,
		/; also: explanation is required/u,
		/; also: citation 1: evidence citation startLine must be a positive integer/u,
		/; also: citation 2: evidence citation sourcePath is required/u,
		/; also: problem 1: basis\.head names citation 4/u,
		/; also: example\.note is required/u,
	]) {
		assert.match(many, part);
	}
});

void test("a submission is one result object", () => {
	for (const raw of [null, "MET", [baseSubmission()]]) {
		assert.throws(
			() => normalizeSubmission(raw),
			/^Error: the arguments must be one result object$/u,
		);
	}
});

void test("a result carries no confidence, and one offered is rejected", () => {
	for (const confidence of [-1, 4200, "very"]) {
		assert.match(
			refusal(baseSubmission({ confidence })),
			/^result has unknown field\(s\): confidence; it has only outcome, summary, explanation, citations, problems, example, evidence$/u,
		);
	}
	for (const confidence of [null, ""]) {
		assert.throws(
			() => normalizeSubmission(baseSubmission({ confidence })),
			/unknown.*confidence/u,
		);
	}
});

void test("removed fields and old axes are rejected rather than silently accepted", () => {
	for (const field of [
		"guidance",
		"suggestedDiffNotes",
		"presence",
		"assessment",
		"assessmentStatus",
	]) {
		assert.match(
			refusal(baseSubmission({ [field]: "legacy" })),
			new RegExp(`^result has unknown field\\(s\\): ${field};`, "u"),
		);
	}
});

void test("the wire contract rejects outcome aliases and text coerced from numbers", () => {
	for (const outcome of ["met", "NOT-MET", "not met", " MET ", "POSITIVE"]) {
		assert.match(refusal(baseSubmission({ outcome })), /invalid outcome/u);
	}
	assert.match(refusal({ ...resultOf("MET"), severity: "null" }), /unknown.*severity/u);
	assert.match(refusal(baseSubmission({ summary: 1234 })), /summary is required/u);
});

void test("practice slugs normalize to one canonical identity", () => {
	assert.equal(
		normalizePracticeSlug("writes_focused_pull_requests"),
		normalizePracticeSlug(" WRITES-FOCUSED-PULL-REQUESTS "),
	);
});

void test("a correction to severity or explanation is not an exact retry", () => {
	const initial = normalizeSubmission(baseSubmission());
	assert.notDeepEqual(
		initial,
		normalizeSubmission(baseSubmission({ problems: [problemFixture({ severity: "CRITICAL" })] })),
	);
	assert.notDeepEqual(
		initial,
		normalizeSubmission(
			baseSubmission({
				explanation: "The complete captured change establishes a different consequence.",
			}),
		),
	);
	assert.notDeepEqual(initial, normalizeSubmission(resultOf("MET")));
});

void test("a one-word summary is refused, because it names nothing on the practice page", () => {
	assert.match(refusal(baseSubmission({ summary: "Test" })), /short phrase/u);
	assert.match(refusal(baseSubmission({ summary: "  Duplication  " })), /short phrase/u);
	assert.equal(normalizeSubmission(baseSubmission({ summary: "No tests" })).summary, "No tests");
});

void test("a summary over the bound is refused whole, never recorded as a fragment of itself", () => {
	const quotedTitle =
		"The MR names the issue it implements via 'Closes #1' in the body and '#1' in the title, " +
		"resolved by the platform to issue #1 'Day 1: Make your first merge request'";
	assert.equal(quotedTitle.length, MAX_SUMMARY_CHARS + 3);
	assert.match(
		refusal(baseSubmission({ summary: quotedTitle })),
		new RegExp(
			`^summary must be at most ${MAX_SUMMARY_CHARS} characters; this one is ${quotedTitle.length}\\. ` +
				"Resend the result with a shorter summary that reads as a complete phrase on its own",
			"u",
		),
	);
	const atTheLimit = `${"x ".repeat(MAX_SUMMARY_CHARS / 2).trim()}x`;
	assert.equal(atTheLimit.length, MAX_SUMMARY_CHARS);
	assert.equal(normalizeSubmission(baseSubmission({ summary: atTheLimit })).summary, atTheLimit);
	// Runs of whitespace are one space: a summary is one line on the page.
	assert.equal(
		normalizeSubmission(baseSubmission({ summary: "PR mixes\n  unrelated   changes" })).summary,
		"PR mixes unrelated changes",
	);
	// The same rule holds for the summary of each problem.
	assert.match(
		refusal(baseSubmission({ problems: [problemFixture({ summary: quotedTitle })] })),
		/^problem 1: summary must be at most 160 characters/u,
	);
});

void test("an explanation over its bound is refused", () => {
	assert.match(
		refusal(baseSubmission({ explanation: "x".repeat(10_001) })),
		/^explanation must be at most 10000 characters; this one is 10001$/u,
	);
	assert.match(
		refusal(baseSubmission({ problems: [problemFixture({ explanation: "x".repeat(4001) })] })),
		/^problem 1: explanation must be at most 4000 characters/u,
	);
	assert.match(
		refusal(baseSubmission({ problems: [problemFixture({ nextStep: "x".repeat(1001) })] })),
		/^problem 1: nextStep must be at most 1000 characters/u,
	);
	assert.match(
		refusal({
			...resultOf("MET"),
			evidence: { search: { ...goodSearch, boundary: "x".repeat(501) } },
		}),
		/^search\.boundary must be at most 500 characters/u,
	);
});

// ── Citations ────────────────────────────────────────────────────────────────

void test("a line-number refusal names what was received, and an omitted line as omitted", () => {
	const cited = (lines: Record<string, unknown>) =>
		citing(citationFixture({ startLine: undefined, endLine: undefined, ...lines }));
	assert.match(
		refusal(cited({})),
		/^citation 1: evidence citation startLine is required: the 1-based line/u,
	);
	assert.match(refusal(cited({ startLine: null })), /startLine is required/u);
	assert.match(
		refusal(cited({ startLine: 0 })),
		/startLine must be a positive integer, received 0; lines are 1-based/u,
	);
	assert.match(refusal(cited({ startLine: "ten" })), /received "ten"; lines are 1-based/u);
	assert.match(
		refusal(cited({ startLine: 10, endLine: 4 })),
		/endLine must be an integer >= startLine, received 4 with startLine 10/u,
	);
});

void test("citation requires an exact sourceFile path and quote", () => {
	assert.match(
		refusal(citing(citationFixture({ sourcePath: undefined }))),
		/sourcePath is required/u,
	);
	// No quote is a citation by coordinates alone; the runner fills it from the sourceFile.
	assert.equal(normalizedCitation(citationFixture({ quote: undefined })).quote, "");
	assert.match(
		refusal(citing(citationFixture({ quote: "x".repeat(4001) }))),
		/^citation 1: quote must be at most 4000 characters/u,
	);
});

void test("normalization preserves side for source-catalog validation", () => {
	assert.equal(
		normalizedCitation(citationFixture({ sourceKind: "custom.source", side: "NEW" })).side,
		"NEW",
	);
	assert.equal(normalizedCitation(citationFixture({ side: undefined })).side, undefined);
});

void test("a citation of change.json is refused with what to quote instead", () => {
	assert.match(
		refusal(citing(citationFixture({ path: "inputs/context/change.json" }))),
		/^citation 1: change\.json pins the reviewed range and is not quotable/u,
	);
	assert.match(
		refusal(
			citing(
				citationFixture({
					sourceKind: "scm.repository.tree",
					sourcePath: "inputs/context/tree.json",
					side: undefined,
				}),
			),
		),
		/^citation 1: repository citations must use the captured \.git\/HEAD sourceFile/u,
	);
});

/** The reviewed checkout's witnesses, which admission needs to verify any quote of code. */
const CHECKOUT_WITNESSES = [
	["repos/reviewed/.git/HEAD", "scm.repository.tree"],
	["repos/reviewed/.git/hephaestus-captured-refs", "scm.repository.tree"],
] as const;

/** The staged sources and sourceFiles with the reviewed checkout captured beside them. */
function withCheckout(
	sources: readonly string[],
	sourceFiles: readonly (readonly [string, string])[],
): [Set<string>, Map<string, string>] {
	return [
		new Set([...sources, "scm.repository.tree"]),
		new Map<string, string>([...sourceFiles, ...CHECKOUT_WITNESSES]),
	];
}

void test("a quote of code is refused unless the checkout admission verifies it against was captured", () => {
	const submission = normalizeSubmission(baseSubmission());
	const diff = [["inputs/context/diff.patch", "scm.pull-request.diff"]] as const;
	assert.doesNotThrow(() =>
		validateEvidenceSources(submission, ...withCheckout(["scm.pull-request.diff"], diff)),
	);
	assert.throws(
		() => validateEvidenceSources(submission, new Set(["scm.pull-request.diff"]), new Map(diff)),
		/a quote of code needs the captured checkout repos\/reviewed\//u,
	);
	// Both witnesses, under the repository tree source, and that source available.
	const [available, sourceFiles] = withCheckout(["scm.pull-request.diff"], diff);
	sourceFiles.delete("repos/reviewed/.git/hephaestus-captured-refs");
	assert.throws(
		() => validateEvidenceSources(submission, available, sourceFiles),
		/needs the captured checkout/u,
	);
	assert.throws(
		() =>
			validateEvidenceSources(
				submission,
				new Set(["scm.pull-request.diff"]),
				new Map([...diff, ...CHECKOUT_WITNESSES]),
			),
		/needs the captured checkout/u,
	);
	// The task names the reviewed checkout; a quote of the change is verified against that one.
	assert.throws(
		() =>
			validateEvidenceSources(
				submission,
				...withCheckout(["scm.pull-request.diff"], diff),
				"repos/primary/",
			),
		/needs the captured checkout repos\/primary\//u,
	);
	// A repository citation names the witness of the checkout it quotes, exactly as admission reads it.
	assert.match(
		refusal(
			citing(
				citationFixture({
					sourceKind: "scm.repository.tree",
					sourcePath: "repos/re.viewed/.git/HEAD",
					side: undefined,
				}),
			),
		),
		/repository citations must use the captured \.git\/HEAD sourceFile/u,
	);
});

void test("the server's live harnesses stage the checkout a quote of the change is verified against", () => {
	// LiveWorkRecordsTest pins this index from the harness that stages the live reviews.
	const { availableSourceKinds, sourceFileSources } = folderCitationIndex(
		JSON.parse(readFileSync(new URL("contract-v3/live-manifest.json", import.meta.url), "utf8")),
	);
	const cited = citationFixture({ sourcePath: "context/change.json", path: "src/cart.ts" });
	const submission = normalizeSubmission(
		baseSubmission({ citations: [cited], problems: [problemFixture({ citations: [cited] })] }),
	);
	assert.doesNotThrow(() =>
		validateEvidenceSources(submission, availableSourceKinds, sourceFileSources),
	);
	const withoutCheckout = new Map(
		[...sourceFileSources].filter(([, kind]) => kind !== "scm.repository.tree"),
	);
	assert.throws(
		() => validateEvidenceSources(submission, availableSourceKinds, withoutCheckout),
		/needs the captured checkout repos\/reviewed\//u,
	);
});

void test("a citation must name a source this run staged, and the sourceFile that source produced", () => {
	const submission = normalizeSubmission(baseSubmission());
	assert.doesNotThrow(() =>
		validateEvidenceSources(
			submission,
			...withCheckout(
				["scm.pull-request.diff"],
				[["inputs/context/diff.patch", "scm.pull-request.diff"]],
			),
		),
	);
	assert.doesNotThrow(() =>
		validateEvidenceSources(
			submission,
			...withCheckout(
				["scm.pull-request.diff", "workspace.project-inventory"],
				[["inputs/context/diff.patch", "scm.pull-request.diff"]],
			),
		),
	);
	assert.throws(
		() =>
			validateEvidenceSources(
				submission,
				new Set(["scm.pull-request.core", "scm.review-threads"]),
				new Map(),
			),
		/was not available.*scm\.pull-request\.core, scm\.review-threads/u,
	);
	assert.throws(
		() =>
			validateEvidenceSources(
				submission,
				new Set(["scm.pull-request.diff"]),
				new Map([["inputs/context/diff.patch", "scm.pull-request.core"]]),
			),
		/belongs to evidence source 'scm\.pull-request\.core', not 'scm\.pull-request\.diff'/u,
	);
	assert.throws(
		() =>
			validateEvidenceSources(
				submission,
				new Set(["scm.pull-request.diff"]),
				new Map([["inputs/context/change.json", "scm.pull-request.diff"]]),
			),
		/was not staged by 'scm\.pull-request\.diff';/u,
	);
	// The change view is derived in the container; a citation of it is told what the sourceFile is.
	const derived = normalizeSubmission(baseSubmission());
	onlyCitation(derived.citations).sourcePath = "work/change/files.json";
	assert.throws(
		() =>
			validateEvidenceSources(
				derived,
				new Set(["scm.pull-request.diff"]),
				new Map([["inputs/context/diff.patch", "scm.pull-request.diff"]]),
			),
		/work\/ is derived here and is not a source file: quote a changed line from work\/change\/diff\.patch/u,
	);
});

void test("the sources of every citation are checked, not only the result's", () => {
	const [available, staged] = withCheckout(
		["scm.pull-request.diff"],
		[["inputs/context/diff.patch", "scm.pull-request.diff"]],
	);
	assert.throws(
		() => validateEvidenceSources(normalizeSubmission(resultOf("MET")), available, staged),
		/evidence source 'scm\.pull-request\.core' was not available/u,
	);
	const inQuestion = normalizeSubmission({
		...resultOf("COULD_NOT_DETERMINE"),
		evidence: {
			openQuestions: [
				{ ...UNDECIDABLE, citation: citationFixture({ sourcePath: "elsewhere.patch" }) },
			],
		},
	});
	assert.throws(
		() => validateEvidenceSources(inQuestion, available, staged),
		/sourceFile 'elsewhere\.patch' was not staged/u,
	);
});

void test("an unstaged citation refuses its reference without disclosing every staged repository path", () => {
	const submission = normalizeSubmission(baseSubmission());
	const sources = new Map<string, string>(
		Array.from({ length: 10_000 }, (_, at) => [
			`repository/unrelated-${at}.swift`,
			"scm.repository.tree",
		]),
	);
	let refused: unknown;
	try {
		validateEvidenceSources(submission, new Set(["scm.pull-request.diff"]), sources);
	} catch (error) {
		refused = error;
	}
	assert.ok(refused instanceof Error);
	assert.ok(refused.message.includes(onlyCitation(submission.citations).sourcePath));
	assert.ok(refused.message.includes("scm.pull-request.diff"));
	assert.equal(
		[...sources.keys()].some((path) => refused.message.includes(path)),
		false,
	);
	assert.throws(
		() => validateEvidenceSources(submission, new Set(["scm.pull-request.diff"]), new Map()),
		{ message: refused.message },
	);
});

void test("a path under another checkout keeps its prefix, and no note says otherwise", () => {
	const notes: string[] = [];
	const citation = normalizeCitation(
		{
			sourceKind: "scm.repository.tree",
			sourcePath: "repos/reviewed/.git/HEAD",
			path: "repos/linked/README.md",
			startLine: 3,
			quote: "Run make.",
		},
		"",
		{ notes },
	);
	assert.equal(citation.path, "repos/linked/README.md");
	assert.ok(!notes.some((note) => note.includes("its path inside the checkout")), notes.join("\n"));
});

void test("a checkout file named by its workspace path is read at its path inside the checkout, with a note", () => {
	const notes: string[] = [];
	const citation = normalizeCitation(
		{
			sourceKind: "scm.repository.tree",
			sourcePath: "repos/reviewed/.git/HEAD",
			path: "repos/reviewed/README.md",
			startLine: 3,
			quote: "Run make.",
		},
		"problem 1 citation 1: ",
		{ notes },
	);
	assert.equal(citation.path, "README.md");
	assert.deepEqual(notes, [
		"problem 1 citation 1: path repos/reviewed/README.md read as README.md, its path inside the checkout",
	]);
});

void test("historical citations preserve a full revision for trusted admission", () => {
	const citation = {
		sourceKind: "scm.repository.tree",
		sourcePath: "repos/repo/.git/HEAD",
		path: "deleted.ts",
		revision: "a".repeat(40),
		startLine: 3,
		quote: "historical text",
	};
	assert.equal(normalizeCitation(citation, "").revision, citation.revision);
	assert.throws(
		() => normalizeCitation({ ...citation, revision: "x".repeat(1025) }, "citation 1: "),
		/^Error: citation 1: revision must be an opaque nonempty version/u,
	);
	// Whether a revision applies is decided once the manifest has settled the source, so it is kept
	// here; the runner drops it from any citation that is not of the repository, with a note.
	assert.equal(
		normalizeCitation({ ...citation, sourceKind: "scm.issue.core" }, "").revision,
		citation.revision,
	);
});

void test("a citation is completed from the manifest and its line numbers read as written", () => {
	const notes: string[] = [];
	const staged = new Map([
		["inputs/context/metadata.json", "scm.pull-request.core"],
		["inputs/context/change.json", "scm.pull-request.diff"],
	]);
	const submission = normalizeSubmission(
		baseSubmission({
			citations: [{ path: "inputs/context/metadata.json", startLine: "[L3]", quote: "x" }],
			problems: [
				problemFixture({
					citations: [
						{
							sourcePath: "inputs/context/change.json",
							path: "src/Auth.java",
							side: "NEW",
							startLine: "L10",
							endLine: "12",
							quote: "y",
						},
					],
				}),
			],
		}),
		notes,
		(sourceFile) => staged.get(sourceFile),
	);
	const [record, change] = submissionCitations(submission);
	assert.deepEqual(
		[record?.sourcePath, record?.sourceKind, record?.startLine],
		["inputs/context/metadata.json", "scm.pull-request.core", 3],
	);
	assert.deepEqual(
		[change?.sourceKind, change?.startLine, change?.endLine],
		["scm.pull-request.diff", 10, 12],
	);
	assert.deepEqual(notes, [
		"citation 1: sourcePath filled in as inputs/context/metadata.json, the staged record the path names",
		"citation 1: sourceKind filled in as scm.pull-request.core, the source that staged inputs/context/metadata.json",
		'citation 1: startLine "[L3]" read as 3',
		"problem 1 citation 1: sourceKind filled in as scm.pull-request.diff, the source that staged inputs/context/change.json",
		'problem 1 citation 1: startLine "L10" read as 10',
		'problem 1 citation 1: endLine "12" read as 12',
	]);
	// What the manifest does not know is still asked for, naming each citation it is missing from.
	assert.throws(
		() =>
			normalizeSubmission(
				baseSubmission({
					citations: [
						{ path: "a.ts", startLine: 1 },
						{ path: "b.ts", startLine: 1 },
					],
				}),
				[],
				() => undefined,
			),
		/^Error: citation 1: evidence citation sourceKind is required; also: citation 2: evidence citation sourceKind is required$/u,
	);
});

void test("normalization preserves the quoted source indentation and trailing spaces", () => {
	const quote = "    insecure();  ";
	assert.equal(normalizedCitation(citationFixture({ quote })).quote, quote);
});

void test("normalization preserves raw quote bytes and local preflight matches server line semantics", () => {
	const diff =
		"--- a/src/Auth.java\n+++ b/src/Auth.java\n@@ -10,2 +10,2 @@\n[L10] +  Trivio:  # TODO: Adjust Name\n[L11] +\n";
	for (const quote of [
		"  Trivio:  # TODO: Adjust Name",
		"  Trivio:  # TODO: Adjust Name\n",
		"+  Trivio:  # TODO: Adjust Name\r\n",
	]) {
		const citation = normalizedCitation(citationFixture({ quote }));
		assert.equal(citation.quote, quote);
		assert.equal(describeCitationMismatch(citation, diff), null);
	}
	const quote = "  Trivio:  # TODO: Adjust Name\r\n\r\n";
	const citation = normalizedCitation(citationFixture({ quote, endLine: 11 }));
	assert.equal(citation.quote, quote);
	assert.equal(describeCitationMismatch(citation, diff), null);
});

void test("citation coordinates are bounded", () => {
	for (const line of [2_147_483_648, 4_294_967_306, Number.MAX_SAFE_INTEGER + 1]) {
		assert.match(
			refusal(citing(citationFixture({ startLine: line, endLine: undefined }))),
			/startLine/u,
		);
		assert.match(refusal(citing(citationFixture({ endLine: line }))), /endLine/u);
	}
});

void test("normalization preserves nonblank citation path identifiers", () => {
	const citation = normalizedCitation(
		citationFixture({ path: " source file ", sourcePath: "inputs/context/ captured file " }),
	);
	assert.equal(citation.path, " source file ");
	assert.equal(citation.sourcePath, "inputs/context/ captured file ");
	for (const field of ["path", "sourcePath"]) {
		assert.match(
			refusal(citing(citationFixture({ [field]: " \t\n" }))),
			new RegExp(`${field} is required`, "u"),
		);
	}
});

void test("a quote copied with the brief's line coordinates is stored without them", () => {
	assert.equal(withoutCoordinates("[L15] to test the winner", 15), "to test the winner");
	assert.equal(withoutCoordinates("[L15] first\n[L16] second\n", 15), "first\nsecond\n");
	// A coordinate that names another line is text, and stays.
	assert.equal(withoutCoordinates("[L9] elsewhere", 15), "[L9] elsewhere");
	assert.equal(withoutCoordinates("plain", 3), "plain");
	assert.equal(
		normalizedCitation(citationFixture({ quote: "[L10] + insecure();" })).quote,
		"+ insecure();",
	);
});

// ── Quote resolution ─────────────────────────────────────────────────────────

void test("diff citations bind the quote to the claimed file and side; a wrong line is corrected when the text is unique", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] + insecure();\n";
	assert.equal(citationMatchesSourceFile(citation, diff), true);
	assert.equal(citationMatchesSourceFile({ ...citation, path: "src/Other.java" }, diff), false);
	assert.equal(citationMatchesSourceFile({ ...citation, side: "OLD" }, diff), false);
	assert.deepEqual(resolveQuote({ ...citation, startLine: 11, endLine: 12 }, diff), {
		quote: " insecure();",
		startLine: 10,
		endLine: 10,
	});
});

void test("a quote may drop the diff marker and read the spacing differently; the text must match", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] +    insecure();\n";

	// The coordinate pins the line, so spacing cannot confuse two lines; the record is the line's bytes.
	assert.deepEqual(resolveQuote({ ...citation, quote: "insecure();" }, diff), {
		quote: "    insecure();",
	});
	assert.equal(describeCitationMismatch({ ...citation, quote: "    insecure();" }, diff), null);
	assert.equal(describeCitationMismatch({ ...citation, quote: "+    insecure();" }, diff), null);
	// Different text at that coordinate is still refused, trimmed or not.
	assert.match(
		describeCitationMismatch({ ...citation, quote: "secure();" }, diff) ?? "",
		/\[L10\] reads/u,
	);

	// Code that begins with the same character the diff uses as a marker keeps it.
	const flagDiff =
		"diff --git a/run.sh b/run.sh\n+++ b/run.sh\n@@ -10 +10 @@\n[L10] +    -flag --now\n";
	assert.equal(
		describeCitationMismatch({ ...citation, path: "run.sh", quote: "    -flag --now" }, flagDiff),
		null,
	);
	assert.equal(
		describeCitationMismatch({ ...citation, path: "run.sh", quote: "+    -flag --now" }, flagDiff),
		null,
	);
});

/** The mismatch a resolution reports, or nothing when it resolved. */
const mismatch = (result: ReturnType<typeof resolveQuote>) =>
	"mismatch" in result ? result.mismatch : "";

/** One indented `"text" : <value>` line of a serialized object per value, comma-separated. */
const serializedFields = (values: string[]) =>
	values.map((value, index) => `    "text" : ${value}${index < values.length - 1 ? "," : ""}`);

/** A citation of the pull request's title, quoting what the test says it quotes. */
const cite = (quote: string): NormalizedCitation => ({
	sourceKind: "scm.pull-request.core",
	sourcePath: "inputs/context/core.md",
	path: "title",
	startLine: 1,
	endLine: 1,
	quote,
});

void test("a matching diff quote is recorded as the content its lines carry, markers dropped", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/run.sh b/run.sh\n+++ b/run.sh\n@@ -10,2 +10,2 @@\n[L10] +    -flag --now\n[L11] +  next\n";
	const cited = { ...citation, path: "run.sh", endLine: 11 };
	// Admission reads the blob at the revision, where no marker exists; so the quote must not carry one.
	assert.deepEqual(resolveQuote({ ...cited, quote: "+    -flag --now\n+  next" }, diff), {
		quote: "    -flag --now\n  next",
	});
	assert.deepEqual(resolveQuote({ ...cited, quote: "    -flag --now\n  next" }, diff), {
		quote: "    -flag --now\n  next",
	});
	// Coordinates alone record the lines.
	assert.deepEqual(resolveQuote({ ...cited, quote: "" }, diff), {
		quote: "    -flag --now\n  next",
	});
	// A marker the quote carries that is not the line's own: a blank context line read as an added one.
	const blankContext =
		"diff --git a/a.md b/a.md\n+++ b/a.md\n@@ -9,2 +10,2 @@\n[L10] +text\n[L11]  \n";
	assert.deepEqual(
		resolveQuote(
			{ ...citation, path: "a.md", startLine: 10, endLine: 11, quote: "+text\n+" },
			blankContext,
		),
		{ quote: "text\n" },
	);
	// Blank lines cannot be evidence: admission refuses a blank quote, so the runner does too.
	const blankDiff = "diff --git a/a.md b/a.md\n+++ b/a.md\n@@ -9,0 +10,2 @@\n[L10] +\n[L11] +\n";
	assert.match(
		mismatch(
			resolveQuote(
				{ ...citation, path: "a.md", startLine: 10, endLine: 11, quote: "\n" },
				blankDiff,
			),
		),
		/cited lines are blank/u,
	);
	assert.match(mismatch(resolveQuote({ ...cited, quote: "", endLine: 12 }, diff)), /no \[L12\]/u);
	// What does not match is refused with what the line reads.
	assert.match(
		mismatch(resolveQuote({ ...cited, quote: "+    -flag\n+  next" }, diff)),
		/\[L10\] reads/u,
	);
});

void test("a quote is recorded as the sourceFile spells it: JSON escapes and non-breaking spaces", () => {
	const { side: _side, ...plain } = normalizedCitation();
	const citation = { ...plain, sourceKind: "scm.linked-work-items", startLine: 2, endLine: 2 };
	const serialized =
		'{\n  "body" : "## Criteria\\n- [x] stored in `diagrams/`\\n- say \\"hi\\""\n}\n';
	// The model quotes the text it read; the record is the escaped form the file holds.
	assert.deepEqual(resolveQuote({ ...citation, quote: '- say "hi"' }, serialized), {
		quote: '- say \\"hi\\"',
	});
	assert.deepEqual(
		resolveQuote({ ...citation, quote: "- [x] stored in `diagrams/`" }, serialized),
		{
			quote: "- [x] stored in `diagrams/`",
		},
	);
	// A non-breaking space read as a space still records the sourceFile's own byte.
	const nbsp = "* Launch app and open\u00A0`Venues`.\n";
	const first = { ...citation, startLine: 1, endLine: 1 };
	assert.deepEqual(resolveQuote({ ...first, quote: "* Launch app and open `Venues`." }, nbsp), {
		quote: "* Launch app and open\u00A0`Venues`.",
	});
	// A blank line read with a stray space, and non-breaking spaces read as spaces, across lines.
	const steps = "## Testing\n\n1. Launch app and open\u00A0**Coupons**\u00A0tab.\n2. Verify.\n";
	assert.deepEqual(
		resolveQuote(
			{
				...citation,
				startLine: 1,
				endLine: 4,
				quote: "## Testing\n \n1. Launch app and open **Coupons** tab.\n2. Verify.",
			},
			steps,
		),
		{ quote: "## Testing\n\n1. Launch app and open\u00A0**Coupons**\u00A0tab.\n2. Verify." },
	);
	// Coordinates alone record the line, up to a bound.
	assert.deepEqual(resolveQuote({ ...first, quote: "" }, nbsp), {
		quote: "* Launch app and open\u00A0`Venues`.",
	});
	assert.match(
		mismatch(resolveQuote({ ...first, quote: "" }, `${"x".repeat(2001)}\n`)),
		/cite fewer lines or quote a fragment/u,
	);
	// A quote across the body's line breaks is a reading of the escaped form and resolves to it.
	assert.deepEqual(resolveQuote({ ...citation, quote: "## Criteria\n- [x] stored" }, serialized), {
		quote: "## Criteria\\n- [x] stored",
	});
});

void test("spacing reads as spacing only where the quote has some, and line breaks stay line breaks", () => {
	const line: NormalizedCitation = {
		sourceKind: "scm.linked-work-items",
		sourcePath: "inputs/context/issue.json",
		path: "inputs/context/issue.json",
		startLine: 1,
		endLine: 1,
		quote: "",
	};
	assert.deepEqual(resolveQuote({ ...line, quote: "return  null;" }, "return null;\n"), {
		quote: "return null;",
	});
	// No spacing in the quote, none in the text: dropping a space is not a reading of it.
	assert.match(
		mismatch(resolveQuote({ ...line, quote: "returnnull;" }, "return null;\n")),
		/reads/u,
	);
	assert.match(mismatch(resolveQuote({ ...line, quote: "foo" }, "f oo\n")), /reads/u);
	// A line break read as a line break, whatever the file ends its lines with and indents them by.
	const crlf = "if (a) {\r\n  return null;\r\n}\r\n";
	assert.deepEqual(resolveQuote({ ...line, endLine: 2, quote: "if (a) {\nreturn null;" }, crlf), {
		quote: "if (a) {\r\n  return null;",
	});
	// Two lines are never one, and one line is never two.
	assert.match(
		mismatch(resolveQuote({ ...line, endLine: 2, quote: "if (a) { return null;" }, crlf)),
		/reads/u,
	);
	assert.match(
		mismatch(resolveQuote({ ...line, quote: "return\nnull;" }, "return null;\n")),
		/reads/u,
	);
	// A quote ending in the `\r` of a `\r\n` line is found where that line is, spacing read as before.
	assert.deepEqual(resolveQuote({ ...line, quote: "end\r" }, "none\nend\r\n"), {
		quote: "end\r",
		startLine: 2,
		endLine: 2,
	});
	assert.deepEqual(resolveQuote({ ...line, quote: "end \r" }, "none\nend  \r\n"), {
		quote: "end  \r",
		startLine: 2,
		endLine: 2,
	});
	assert.match(mismatch(resolveQuote({ ...line, quote: "end\r" }, "none\nend \r\n")), /reads/u);
	assert.match(
		mismatch(resolveQuote({ ...line, quote: "end\r \nnext" }, "none\nend\r\nnext\n")),
		/reads/u,
	);
});

void test("a quote of many indented lines resolves promptly, and so does a near miss", () => {
	// The shape of a serialized review thread: blocks of indented fields, the same names over and over.
	const block = serializedFields([
		"0",
		'"text"',
		"0",
		'"text"',
		'"text"',
		'"text"',
		"false",
		'"text"',
	]);
	const short = serializedFields(['"text"', '"text"', '"text"']);
	const threads = [
		"{",
		'  "text" : [ {',
		...block,
		"  }, {",
		...block,
		"  } ],",
		'  "text" : [ {',
		...short,
		"  }, {",
		...short,
		"  } ],",
		'  "text" : false',
		"}",
	].join("\n");
	const quote = ['  "text" : [ {', ...block, "  }, {", ...block, "  } ]"].join("\n");
	const citation: NormalizedCitation = {
		sourceKind: "scm.review-threads",
		sourcePath: "threads.json",
		path: "threads.json",
		startLine: 2,
		endLine: 19,
		quote,
	};
	// The cited lines stop one line short of the quote; the quote occurs once, a line further on.
	assert.deepEqual(resolveQuote(citation, threads), { quote, startLine: 2, endLine: 20 });
	// Read without its indentation, the same lines are recorded as the file indents them.
	const unindented = quote
		.split("\n")
		.map((quoted) => quoted.trimStart())
		.join("\n");
	assert.deepEqual(resolveQuote({ ...citation, quote: unindented }, threads), {
		quote: quote.trimStart(),
		startLine: 2,
		endLine: 20,
	});
	// One character off at the very end, after every line before it matched.
	assert.match(mismatch(resolveQuote({ ...citation, quote: `${quote}]` }, threads)), /reads/u);
	// A long spaced prefix the text repeats, ending in a character the text does not hold.
	assert.match(
		mismatch(
			resolveQuote(
				{ ...citation, startLine: 1, endLine: 1, quote: `${"a ".repeat(1024)}z` },
				`${"a ".repeat(131_072)}y`,
			),
		),
		/reads/u,
	);
});

void test("a range of the diff view past its last line names no line of it", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n" +
		"@@ -10,2 +10,3 @@\n[L10]  keep();\n[L11] -old();\n[L11] +insecure();\n[L12] +audit();\n";
	for (const path of [citation.path, "work/change/diff.patch"]) {
		assert.notEqual(
			describeCitationMismatch(
				{ ...citation, path, startLine: 5, endLine: 2_147_483_647, quote: "" },
				diff,
			),
			null,
			`${path} has no line ${String(2_147_483_647)}`,
		);
	}
});

void test("a quote from the other side of the change is refused, however it is written", () => {
	const citation: NormalizedCitation = {
		...normalizedCitation(),
		side: "NEW",
		startLine: 47,
		endLine: 47,
		quote: '-@RequestMapping({ "api/legacy/" })',
	};
	const diff =
		"--- a/src/Auth.java\n+++ b/src/Auth.java\n@@ -47 +47 @@\n" +
		'[L47] -@RequestMapping({ "api/legacy/" })\n[L47] +@RequestMapping("api/passkeys/")\n';

	assert.match(describeCitationMismatch(citation, diff) ?? "", /\[L47\] reads/u);
});

void test("a quote may carry its exact displayed coordinate", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] + insecure();\n";

	assert.equal(describeCitationMismatch({ ...citation, quote: "[L10] + insecure();" }, diff), null);
	// The coordinate still has to be the one being matched, so a quote cannot claim a line it did
	// not read — even when that line's text is in the diff somewhere else.
	assert.match(
		describeCitationMismatch({ ...citation, quote: "[L11] + insecure();" }, diff) ?? "",
		/\[L10\] reads/u,
	);
});

void test("a refused citation says which of the coordinate, the side and the text was wrong", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] + insecure();\n";

	assert.equal(describeCitationMismatch(citation, diff), null);
	// The coordinate is not in the diff, but the text is, once: it is recorded where it is.
	assert.deepEqual(resolveQuote({ ...citation, startLine: 11, endLine: 11 }, diff), {
		quote: " insecure();",
		startLine: 10,
		endLine: 10,
	});
	// Neither the coordinate nor the text is in the diff.
	assert.match(
		describeCitationMismatch({ ...citation, startLine: 11, endLine: 11, quote: "gone();" }, diff) ??
			"",
		/no \[L11\] on that side of that path/u,
	);
	// The coordinate is there and says something else, so the refusal shows both.
	assert.match(
		describeCitationMismatch({ ...citation, quote: "+ secure();" }, diff) ?? "",
		/\[L10\] reads "\+ insecure\(\);", not "\+ secure\(\);"/u,
	);
	// The quote and the line span disagree, but the block occurs once: recorded where it is.
	assert.deepEqual(resolveQuote({ ...citation, endLine: 12 }, diff), {
		quote: " insecure();",
		startLine: 10,
		endLine: 10,
	});
	assert.match(
		describeCitationMismatch({ ...citation, endLine: 12, quote: "gone();\nalso();" }, diff) ?? "",
		/quote is 2 line\(s\) and the citation covers 3/u,
	);
});

void test("a citation of the diff view itself is placed at the changed line its quote names, when that is one line", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10,2 @@\n" +
		"[L10] -    secure();\n[L10] +    insecure();\n[L11] +    audit();\n" +
		"diff --git a/src/Log.java b/src/Log.java\n+++ b/src/Log.java\n@@ -3 +3 @@\n[L3] +    audit();\n";
	// Line 92 of the view, not a coordinate of the change: the quote names src/Auth.java [L10] on NEW.
	const ofView = { ...citation, path: "work/change/diff.patch", startLine: 92, endLine: 92 };
	assert.deepEqual(resolveQuote({ ...ofView, quote: "insecure();" }, diff), {
		quote: "    insecure();",
		startLine: 10,
		endLine: 10,
		path: "src/Auth.java",
		side: "NEW",
	});
	assert.deepEqual(resolveQuote({ ...ofView, quote: "secure();" }, diff), {
		quote: "    secure();",
		startLine: 10,
		endLine: 10,
		path: "src/Auth.java",
		side: "OLD",
	});
	// A quote on two changed lines, or on none, is refused with what a citation of the change names.
	assert.match(
		describeCitationMismatch({ ...ofView, quote: "audit();" }, diff) ?? "",
		/work\/change\/diff\.patch is the view of the change, not a file in it: cite the changed file's path[\s\S]*occurs 2 times/u,
	);
	assert.match(
		describeCitationMismatch({ ...ofView, quote: "absent();" }, diff) ?? "",
		/is not a line of the change/u,
	);
});

void test("a range copied from a numbered view of diff.patch is read through both of its ends", () => {
	const citation = normalizedCitation();
	// View lines 5-8 hold NEW [L10]-[L12] with an OLD line among them: the view's count is not the file's.
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n" +
		"@@ -10,2 +10,3 @@\n[L10]  keep();\n[L11] -old();\n[L11] +insecure();\n[L12] +audit();\n";
	const expected = { quote: "keep();\ninsecure();\naudit();", startLine: 10, endLine: 12 };
	assert.deepEqual(
		resolveQuote({ ...citation, startLine: 5, endLine: 8, quote: "" }, diff),
		expected,
	);
	// A range that starts on the file's own header, as a read of the view file by file prints it.
	assert.deepEqual(
		resolveQuote({ ...citation, startLine: 1, endLine: 8, quote: "" }, diff),
		expected,
	);
	// A range of diff.patch itself that holds one file's lines is read as that file's lines.
	assert.deepEqual(
		resolveQuote(
			{ ...citation, path: "work/change/diff.patch", startLine: 1, endLine: 8, quote: "" },
			diff,
		),
		{ ...expected, path: "src/Auth.java", side: "NEW" },
	);
});

void test("a range of diff.patch itself over a hunk that only removes is read on the OLD side", () => {
	const citation = normalizedCitation();
	// View lines 5-8: an unchanged line, two removed lines, an unchanged line. The evidence is what was removed.
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n" +
		"@@ -9,4 +9,2 @@\n[L9]  begin();\n[L10] -checkToken();\n[L11] -checkRole();\n[L10]  end();\n";
	assert.deepEqual(
		resolveQuote(
			{ ...citation, path: "work/change/diff.patch", startLine: 1, endLine: 8, quote: "" },
			diff,
		),
		{
			quote: "checkToken();\ncheckRole();",
			startLine: 10,
			endLine: 11,
			path: "src/Auth.java",
			side: "OLD",
		},
	);
});

void test("a range of diff.patch itself across two files names no one file", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n" +
		"@@ -10,0 +10,1 @@\n[L10] + insecure();\n" +
		"diff --git a/src/Log.java b/src/Log.java\n--- a/src/Log.java\n+++ b/src/Log.java\n@@ -3,0 +3 @@\n[L3] + log();\n";
	assert.match(
		describeCitationMismatch(
			{ ...citation, path: "work/change/diff.patch", startLine: 1, endLine: 10, quote: "" },
			diff,
		) ?? "",
		/work\/change\/diff\.patch is the view of the change, not a file in it/u,
	);
});

void test("a coordinate copied from a numbered view of diff.patch is read as the line it names", () => {
	const citation = normalizedCitation();
	// The text occurs twice in the file; the session cites line 7 of diff.patch, as sed -n prints it.
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n" +
		"@@ -10,0 +10,1 @@\n[L10] + insecure();\n@@ -20,0 +21,1 @@\n[L21] + insecure();\n";
	assert.deepEqual(
		resolveQuote({ ...citation, startLine: 7, endLine: 7, quote: "insecure();" }, diff),
		{ quote: " insecure();", startLine: 21, endLine: 21 },
	);
	// A view line that is not a line of the cited file still names nothing: the choice is the session's.
	assert.match(
		describeCitationMismatch(
			{ ...citation, startLine: 4, endLine: 4, quote: "insecure();" },
			diff,
		) ?? "",
		/occurs at \[L10\], \[L21\] — cite the one you mean/u,
	);
});

void test("a file the diff shows without numbered lines is refused with what to cite instead", () => {
	const citation = normalizedCitation();
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n" +
		"@@ -10 +10 @@\n[L10] + insecure();\n" +
		"diff --git a/logo.png b/logo.png\nnew file mode 100644\nBinary files /dev/null and b/logo.png differ\n" +
		"diff --git a/old.txt b/new.txt\nsimilarity index 100%\nrename from old.txt\nrename to new.txt\n" +
		"diff --git a/gone.txt b/gone.txt\ndeleted file mode 100644\n--- a/gone.txt\n+++ /dev/null\n" +
		"@@ -1 +0,0 @@\n[L1] -bye\n";
	const at = (path: string, side: "OLD" | "NEW" = "NEW") =>
		describeCitationMismatch({ ...citation, path, side, startLine: 361, endLine: 361 }, diff) ?? "";

	// A binary file and a rename without edits have a header only: the commit that touches them is citable.
	assert.match(at("logo.png"), /no numbered lines in the diff[\s\S]*commits\.json/u);
	assert.match(at("new.txt"), /no numbered lines in the diff/u);
	assert.match(at("old.txt", "OLD"), /no numbered lines in the diff/u);
	// A deleted file's lines are on the old side.
	assert.match(at("gone.txt"), /no lines on the NEW side[\s\S]*on the OLD side/u);
	// A path the change never names is not a file of the change at all.
	assert.match(at("elsewhere.txt"), /the change does not touch elsewhere\.txt/u);
});

void test("removed-line citations use old-side coordinates", () => {
	const citation: NormalizedCitation = {
		...normalizedCitation(),
		side: "OLD",
		startLine: 8,
		endLine: 8,
		quote: "- requireAdmin();",
	};
	const diff =
		"--- a/src/Auth.java\n+++ b/src/Auth.java\n@@ -8 +8 @@\n[L8] - requireAdmin();\n[L8] + allowAll();\n";
	assert.equal(citationMatchesSourceFile(citation, diff), true);
	assert.equal(citationMatchesSourceFile({ ...citation, side: "NEW" }, diff), false);
});

void test("a claim about an earlier review is bound to the staged history like any other citation", () => {
	const submission = normalizeSubmission(
		citing({
			sourceKind: "hephaestus.problem-history",
			sourcePath: "inputs/history/problems.json",
			path: "inputs/history/problems.json",
			startLine: 1,
			endLine: 1,
			quote: '"recurrenceKey": "rec-1"',
		}),
	);
	const [staged, sourceFiles] = withCheckout(
		["scm.pull-request.diff", "hephaestus.problem-history"],
		[
			["inputs/history/problems.json", "hephaestus.problem-history"],
			["inputs/context/diff.patch", "scm.pull-request.diff"],
		],
	);

	assert.doesNotThrow(() => validateEvidenceSources(submission, staged, sourceFiles));
	const cited = onlyCitation(submission.citations);
	const bytes = '{"problems":[{"recurrenceKey": "rec-1","title":"Caught and ignored"}]}';
	assert.equal(citationMatchesSourceFile(cited, bytes), true);
	const invented = { ...cited, quote: '"recurrenceKey": "invented"' };
	assert.equal(citationMatchesSourceFile(invented, bytes), false);
	// The refusal shows what the cited lines hold: a body serialized into one JSON line is one line.
	assert.equal(
		describeCitationMismatch(invented, bytes),
		`[L1] reads ${JSON.stringify(bytes)}, not ${JSON.stringify(invented.quote)}`,
	);
	assert.equal(
		describeCitationMismatch({ ...invented, startLine: 3, endLine: 3 }, `${bytes}\n`),
		"the sourceFile has 1 line(s), so there is no [L3]",
	);
	// A quote across a serialized body's line breaks is a reading of the escaped form, and resolves to it;
	// text that is not there at all is refused with how to quote such a line.
	const serialized = '{"body": "## Criteria\\n- [x] stored in `diagrams/`\\n- [x] embedded"}\n';
	assert.deepEqual(
		resolveQuote({ ...invented, quote: "## Criteria\n- [x] stored in `diagrams/`" }, serialized),
		{ quote: "## Criteria\\n- [x] stored in `diagrams/`" },
	);
	assert.match(
		describeCitationMismatch({ ...invented, quote: "## Criteria\n- [x] missing" }, serialized) ??
			"",
		/^\[L1\] reads .*; this is a JSON string whose line breaks are the two characters \\n, so quote a fragment from between two of them, or spell them as the line does$/u,
	);
	assert.equal(
		describeCitationMismatch({ ...invented, quote: "- [x] stored in `diagrams/`" }, serialized),
		null,
	);
});

void test("a citation rejects typographic substitutions not present in the sourceFile", () => {
	const content = 'Resolve "Connect data between screens" — see the plan';
	assert.equal(
		citationMatchesSourceFile(cite('Resolve "Connect data between screens"'), content),
		true,
	);
	assert.equal(
		citationMatchesSourceFile(cite("Resolve “Connect data between screens”"), content),
		false,
	);
	assert.equal(citationMatchesSourceFile(cite("see the plan"), content), true);
});

void test("a citation rejects invented sourceFile text", () => {
	const content = 'Resolve "Connect data between screens"';
	assert.equal(
		citationMatchesSourceFile(cite("Resolve “Disconnect data between screens”"), content),
		false,
	);
	assert.equal(
		citationMatchesSourceFile(cite("a rationale the author never wrote"), content),
		false,
	);
});

void test("live practice fixture permits a citation of its planted credential", () => {
	const diff = readFileSync(new URL("live-practice/diff.patch", import.meta.url), "utf8");
	assert.equal(
		describeCitationMismatch(
			{
				sourceKind: "scm.pull-request.diff",
				sourcePath: "inputs/context/diff.patch",
				path: "LoginService.swift",
				side: "NEW",
				startLine: 4,
				endLine: 4,
				quote: '    private let apiKey = "sk-live-AKIAIOSFODNN7EXAMPLE-prod-2026"',
			},
			diff,
		),
		null,
	);
});

void test("a serialized-source quote at the wrong lines is recorded where it occurs, when that is one place", () => {
	const citation: NormalizedCitation = {
		sourceKind: "scm.pull-request.core",
		sourcePath: "inputs/context/metadata.json",
		path: "inputs/context/metadata.json",
		startLine: 1,
		endLine: 1,
		quote: '"changed_files" : 1',
	};
	const content = '{\n  "changed_files" : 1\n}\n';
	assert.deepEqual(resolveQuote(citation, content), {
		quote: '"changed_files" : 1',
		startLine: 2,
		endLine: 2,
	});
	// The same text in two places is not relocated: the refusal names both.
	assert.match(
		describeCitationMismatch(citation, `${content}  "changed_files" : 1\n`) ?? "",
		/it occurs at \[L2\], \[L4\] — cite the one you mean/u,
	);
	assert.equal(citationMatchesSourceFile({ ...citation, startLine: 2, endLine: 2 }, content), true);
	assert.equal(
		citationMatchesSourceFile(
			{ ...citation, startLine: 2, endLine: 2, quote: '  "changed_files" : 1\n' },
			content,
		),
		true,
	);
});

void test("annotated source text is not parsed as a header and Unicode separators remain source text", () => {
	const cited = normalizedCitation();
	const diff =
		"--- a/src/Auth.java\n+++ b/src/Auth.java\n[L10] --- SQL comment\n[L10] +++ value\u2028tail\u2029end\n";
	assert.equal(
		describeCitationMismatch({ ...cited, side: "OLD", quote: "-- SQL comment" }, diff),
		null,
	);
	assert.equal(
		describeCitationMismatch({ ...cited, quote: "++ value\u2028tail\u2029end" }, diff),
		null,
	);
	assert.notEqual(
		describeCitationMismatch(
			{ ...cited, endLine: 11, quote: "x\n\n" },
			"--- a/src/Auth.java\n+++ b/src/Auth.java\n[L10] +x\n[L11] ",
		),
		null,
	);
	assert.equal(
		describeCitationMismatch({ ...cited, path: '"', quote: "x" }, '--- "\n+++ "\n[L10] +x\n'),
		null,
	);
});

// ── Warrant scope ────────────────────────────────────────────────────────────

void test("a decided claim preserves its submitted bounded search", () => {
	assert.equal("search" in normalizeSubmission(baseSubmission()).evidence, false);
	const withSearch = normalizeSubmission(baseSubmission({ evidence: { search: goodSearch } }));
	assert.deepEqual(withSearch.evidence.search, goodSearch);
	assert.equal(withSearch.citations.length, 1);
});

void test("a direct-evidence claim does not require a search warrant", () => {
	assert.doesNotThrow(() =>
		validateSearchScope(
			normalizeSubmission(baseSubmission()),
			new Set(["scm.review-threads"]),
			new Set(),
		),
	);
});

void test("an absence-based MET claim needs exhaustive captured evidence", () => {
	const submission = normalizeSubmission(resultOf("MET"));
	assert.throws(
		() => validateSearchScope(submission, new Set(), new Set(goodSearch.consulted)),
		/^Error: MET for this practice rests on an absence claim, and the practice declares no source it searches exhaustively, so no search can bound that claim\. Record what the evidence does show: MET from cited evidence, DOES_NOT_APPLY when the work gives the practice no applicability, or COULD_NOT_DETERMINE with what would settle it$/u,
	);
	assert.throws(
		() => validateSearchScope(submission, new Set(["unread"]), new Set(goodSearch.consulted)),
		/^Error: an absence claim for this practice rests on searching unread as well: add it to evidence\.search\.consulted once you have searched it$/u,
	);
	assert.throws(
		() => validateSearchScope(submission, new Set(goodSearch.consulted), new Set()),
		/^Error: searched source 'scm\.review-threads' was not available/u,
	);
	validateSearchScope(submission, new Set(goodSearch.consulted), new Set(goodSearch.consulted));
});

void test("a NOT_MET or COULD_NOT_DETERMINE search names every exhaustive source it left out", () => {
	const available = new Set(["scm.review-threads", "scm.linked-work-items", "scm.issue.core"]);
	for (const submission of [
		normalizeSubmission(baseSubmission({ evidence: { search: goodSearch } })),
		normalizeSubmission(resultOf("COULD_NOT_DETERMINE")),
	]) {
		// A search that is not an absence-only MET needs no exhaustive source.
		assert.doesNotThrow(() => validateSearchScope(submission, new Set(), available));
		assert.throws(
			() => validateSearchScope(submission, available, available),
			/rests on searching scm\.issue\.core, scm\.linked-work-items as well: add them to evidence\.search\.consulted once you have searched them$/u,
		);
	}
});

void test("an inapplicability names only sources the run staged", () => {
	const submission = normalizeSubmission(resultOf("DOES_NOT_APPLY"));
	assert.throws(
		() => validateInapplicabilityScope(submission, new Set()),
		/^Error: consulted source 'scm\.pull-request\.diff' was not available/u,
	);
	assert.doesNotThrow(() =>
		validateInapplicabilityScope(submission, new Set(["scm.pull-request.diff"])),
	);
	// Another outcome has no inapplicability to check.
	assert.doesNotThrow(() =>
		validateInapplicabilityScope(normalizeSubmission(resultOf("MET")), new Set()),
	);
});

// ── Conversation turns ───────────────────────────────────────────────────────

/** A thread as the server writes it: the reviewed participant wrote the second turn. */
const THREAD = `{
  "channel" : "C123",
  "messages" : [ {
    "ts" : "1700000000.100000",
    "text" : "Can someone look at the {failing} build?",
    "accountId" : "42"
  }, {
    "ts" : "1700000000.200000",
    "text" : "It is \\"broken\\". Fix it.",
    "accountId" : "9007199254740993"
  } ]
}
`;

function threadCitation(line: number): NormalizedCitation {
	return {
		...normalizedCitation(),
		sourceKind: "slack.conversation.thread",
		sourcePath: CONVERSATION_THREAD,
		path: "conversation_thread.json",
		startLine: line,
		endLine: line,
	};
}

void test("a conversation citation counts only inside a turn of the participant under review", () => {
	assert.equal(citesReviewedTurn([threadCitation(5)], THREAD, "9007199254740993"), false);
	assert.equal(citesReviewedTurn([threadCitation(9)], THREAD, "9007199254740993"), true);
	assert.equal(
		citesReviewedTurn([threadCitation(5), threadCitation(9)], THREAD, "9007199254740993"),
		true,
	);
	assert.equal(
		citesReviewedTurn(
			[{ ...threadCitation(9), sourcePath: "context/comments.json" }],
			THREAD,
			"9007199254740993",
		),
		false,
	);
	assert.equal(citesReviewedTurn([threadCitation(9)], "not json", "9007199254740993"), false);
});

void test("logical message text citations use the frozen native account rather than another author", () => {
	const quote = (index: number) => ({
		...threadCitation(1),
		path: `${CONVERSATION_THREAD}#/messages/${index}/text`,
	});
	assert.equal(citesReviewedTurn([quote(0)], THREAD, "9007199254740993"), false);
	assert.equal(citesReviewedTurn([quote(1)], THREAD, "9007199254740993"), true);
	assert.equal(citesReviewedTurn([quote(1)], THREAD, "42"), false);
	assert.equal(citesReviewedTurn([quote(0)], THREAD, "42"), true);
	assert.equal(citesReviewedTurn([quote(1)], THREAD, "7"), false);
});

void test("a registered logical text resolves plaintext independently of source naming", () => {
	const citation = {
		sourceKind: "scm.pull-request.diff",
		sourcePath: "context/change.json",
		path: "src/a.ts",
		side: "NEW" as const,
		startLine: 2,
		endLine: 2,
		quote: "  checked();",
	};
	assert.deepEqual(resolveCapturedText(citation, "first\n  checked();\n"), {
		quote: "  checked();",
	});
});
