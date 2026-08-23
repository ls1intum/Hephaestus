import assert from "node:assert/strict";
import test from "node:test";
// Imported statically rather than through `await import(path)`: a dynamic specifier types every export
// as `any`, which is how a spec covering a vocabulary contract came to be checked against nothing.
import {
	ASSESSMENT_DESCRIPTIONS,
	ASSESSMENT_VALUES,
	carriesValence,
	citationMatchesArtifact,
	dedupeKeyForObservation,
	describeVocabulary,
	type NormalizedCitation,
	normalizeObservation as normalizeFinalObservation,
	PRESENCE_DESCRIPTIONS,
	PRESENCE_VALUES,
	type Presence,
	type RecordedInapplicability,
	type RecordedSearch,
	SEVERITY_DESCRIPTIONS,
	SEVERITY_VALUES,
	validateEvidenceSources,
	validateInapplicabilityScope,
	validateSearchScope,
} from "../../../main/resources/agent/pi-observation-normalize.ts";

/**
 * What a test may override. The fixture speaks in the normalized field names these tests were written
 * against — presence, assessment, severity, title, reasoning — and translates them into the `outcome`
 * word the wire shape actually carries. Anything else is copied through untouched, which is how the
 * unknown-field tests smuggle a `confidence` or a `guidance` in.
 */
interface ObservationOverrides {
	practiceSlug?: unknown;
	title?: unknown;
	// The three words the fixture up-cases and folds into an `outcome`. A test that needs a value
	// outside the vocabulary sets `outcome` on the fixture it gets back, which is the field the wire
	// actually carries.
	presence?: string;
	assessment?: string;
	severity?: string;
	reasoning?: unknown;
	evidence?: EvidenceOverrides;
	[key: string]: unknown;
}

/**
 * The evidence block as the fixture assembles it: citations are named because several tests reach into
 * one and delete a field, and the warrant branches ride the index signature.
 */
type EvidenceFixture = { citations: Record<string, unknown>[] } & Record<string, unknown>;

/** The same block as a test hands it in, where every part of it is optional. */
type EvidenceOverrides = Partial<EvidenceFixture> & Record<string, unknown>;

/** An observation in the shape the model sends one, as the fixture assembles it. */
interface RawObservationFixture {
	practiceSlug: unknown;
	summary: unknown;
	outcome: unknown;
	evidence: EvidenceFixture;
	evidenceRationale: unknown;
	[key: string]: unknown;
}

function baseObservation(overrides: ObservationOverrides = {}): RawObservationFixture {
	const presence = (overrides.presence ?? "PRESENT").toUpperCase();
	const assessment = (overrides.assessment ?? "BAD").toUpperCase();
	const severity = (overrides.severity ?? "MAJOR").toUpperCase();
	const rawEvidence: EvidenceOverrides = overrides.evidence ?? {};
	const citations: Record<string, unknown>[] = Array.isArray(rawEvidence.citations)
		? rawEvidence.citations
		: [
				{
					sourceKind: "scm.pull-request.diff",
					artifactPath: "inputs/context/diff.patch",
					path: "src/Auth.java",
					side: "NEW",
					startLine: 10,
					endLine: 10,
					quote: "+ insecure();",
				},
			];
	const evidence: EvidenceFixture = {
		citations,
		...((rawEvidence.search ?? rawEvidence.exhaustiveSearch) == null
			? {}
			: { exhaustiveSearch: rawEvidence.search ?? rawEvidence.exhaustiveSearch }),
		...((rawEvidence.inapplicability ?? rawEvidence.exclusion) == null
			? {}
			: { exclusion: rawEvidence.inapplicability ?? rawEvidence.exclusion }),
		...((rawEvidence.undecidability ?? rawEvidence.missingEvidence) == null
			? {}
			: { missingEvidence: rawEvidence.undecidability ?? rawEvidence.missingEvidence }),
	};
	const outcome = ["PRESENT", "ABSENT"].includes(presence)
		? `BEHAVIOR_${presence}_${assessment}${assessment === "BAD" ? `_${severity}` : ""}`
		: presence === "NOT_APPLICABLE"
			? "NO_REVIEW_OCCASION"
			: "INSUFFICIENT_EVIDENCE";
	const translated: RawObservationFixture = {
		practiceSlug: overrides.practiceSlug ?? "writes_focused_pull_requests",
		summary: overrides.title ?? "PR mixes unrelated changes",
		outcome,
		evidence,
		evidenceRationale: overrides.reasoning ?? "The diff touches auth and billing in one PR.",
	};
	for (const [key, value] of Object.entries(overrides)) {
		if (
			![
				"title",
				"presence",
				"assessment",
				"severity",
				"reasoning",
				"evidence",
				"summary",
				"outcome",
				"evidenceRationale",
			].includes(key)
		)
			translated[key] = value;
	}
	return translated;
}

function normalizeObservation(input: ObservationOverrides) {
	if (
		"presence" in input ||
		"assessment" in input ||
		"severity" in input ||
		"title" in input ||
		"reasoning" in input
	) {
		return normalizeFinalObservation(baseObservation(input));
	}
	if (
		input.evidence &&
		(input.evidence.search || input.evidence.inapplicability || input.evidence.undecidability)
	) {
		return normalizeFinalObservation({
			...input,
			evidence: {
				citations: input.evidence.citations,
				...(input.evidence.search == null ? {} : { exhaustiveSearch: input.evidence.search }),
				...(input.evidence.inapplicability == null
					? {}
					: { exclusion: input.evidence.inapplicability }),
				...(input.evidence.undecidability == null
					? {}
					: { missingEvidence: input.evidence.undecidability }),
			},
		});
	}
	return normalizeFinalObservation(input);
}

/**
 * The one citation a test reaches into. Every fixture here builds exactly one, so an empty list means
 * the fixture or the normalizer stopped producing it — which should fail by that name, not several
 * assertions later on a field read off `undefined`.
 */
function onlyCitation<T extends object>(citations: readonly T[]): T {
	const [citation] = citations;
	if (!citation) throw new Error("expected the observation to carry exactly one citation");
	return citation;
}

/** The ground an INCONCLUSIVE observation owes, mirroring `search` for ABSENT. */
const UNDECIDABLE = {
	openQuestion: "Whether the body states a why, or only restates the title",
	wouldSettleIt: "The body of the issue the description defers to",
};

// `void`: node:test's own runner owns the promise each test hands back, and awaiting one here
// would register the next test only after the previous had finished.
void test("lowercase enums + underscored slug normalize and are accepted (not dropped)", () => {
	const out = normalizeObservation(baseObservation());
	assert.equal(out.practiceSlug, "writes-focused-pull-requests");
	assert.equal(out.presence, "PRESENT");
	assert.equal(out.assessment, "BAD");
	assert.equal(out.severity, "MAJOR");
});

void test("mixed-case enums up-case", () => {
	const out = normalizeObservation(
		baseObservation({ presence: "Present", assessment: "Good", severity: "Minor" }),
	);
	assert.equal(out.presence, "PRESENT");
	assert.equal(out.assessment, "GOOD");
	assert.equal(out.severity, "INFO");
});

// ── No confidence ────────────────────────────────────────────────────────────
// Measured over 580 live observations, confidence never fell below 0.90 and was exactly 1.00 in 55% of
// them. A field with no usable range is not a measurement, and every consumer that ranked on it was
// ranking on noise. It is gone from the final schema, so emitting it is a contract error rather than a
// silently accepted alternate payload.

void test("an observation carries no confidence, and one offered is rejected", () => {
	const out = normalizeObservation(baseObservation());
	assert.equal("confidence" in out, false);
	for (const confidence of [-1, 4200, "very", null]) {
		assert.throws(
			() => normalizeObservation(baseObservation({ confidence })),
			/unknown observation field.*confidence/,
		);
	}
});

void test("NOT_APPLICABLE (lowercase) nulls assessment", () => {
	const out = normalizeObservation(
		notApplicableObservation(goodInapplicability, {
			presence: "not_applicable",
			assessment: "bad",
		}),
	);
	assert.equal(out.presence, "NOT_APPLICABLE");
	assert.equal(out.assessment, undefined);
});

void test("dedupe key uses the normalized hyphenated slug", () => {
	const a = dedupeKeyForObservation(
		normalizeObservation(baseObservation({ practiceSlug: "writes_focused_pull_requests" })),
	);
	const b = dedupeKeyForObservation(
		normalizeObservation(baseObservation({ practiceSlug: "WRITES-FOCUSED-PULL-REQUESTS" })),
	);
	assert.equal(a, b, "underscored and upper-hyphenated slugs must dedupe to the same key");
});

void test("genuinely invalid enum still rejected after normalization", () => {
	const invalid = baseObservation();
	invalid.outcome = "MAYBE";
	assert.throws(() => normalizeObservation(invalid), /invalid outcome/);
});

void test("missing evidence-source attribution is rejected", () => {
	assert.throws(
		() => normalizeObservation(baseObservation({ evidence: { citations: [] } })),
		/citations are required/,
	);
});

void test("citation requires an exact artifact path and quote", () => {
	const missingPath = baseObservation();
	delete onlyCitation(missingPath.evidence.citations).artifactPath;
	assert.throws(() => normalizeObservation(missingPath), /artifactPath is required/);

	const missingQuote = baseObservation();
	delete onlyCitation(missingQuote.evidence.citations).quote;
	assert.throws(() => normalizeObservation(missingQuote), /quote is required/);
});

void test("citation side is present exactly for pull-request diffs", () => {
	const missingDiffSide = baseObservation();
	delete onlyCitation(missingDiffSide.evidence.citations).side;
	assert.throws(() => normalizeObservation(missingDiffSide), /side must be OLD or NEW/);

	const nonDiffSide = baseObservation();
	onlyCitation(nonDiffSide.evidence.citations).sourceKind = "scm.pull-request.core";
	assert.throws(() => normalizeObservation(nonDiffSide), /must not specify side/);
});

void test("a citation must name a source this run staged, and the artifact that source produced", () => {
	const observation = normalizeObservation(baseObservation());
	assert.doesNotThrow(() =>
		validateEvidenceSources(
			observation,
			new Set(["scm.pull-request.diff"]),
			new Map([["inputs/context/diff.patch", "scm.pull-request.diff"]]),
		),
	);
	// The practice's own bindings no longer narrow this: every source that applies to the artifact is
	// staged, so a quote from one the practice did not name is still a quote from bytes that were there.
	assert.doesNotThrow(() =>
		validateEvidenceSources(
			observation,
			new Set(["scm.pull-request.diff", "workspace.project-inventory"]),
			new Map([["inputs/context/diff.patch", "scm.pull-request.diff"]]),
		),
	);
	assert.throws(
		() => validateEvidenceSources(observation, new Set(), new Map()),
		/was not available/,
	);
	assert.throws(
		() =>
			validateEvidenceSources(
				observation,
				new Set(["scm.pull-request.diff"]),
				new Map([["inputs/context/diff.patch", "scm.pull-request.core"]]),
			),
		/does not belong/,
	);
});

void test("diff citations bind the quote to the claimed file and line", () => {
	const citation = onlyCitation(normalizeObservation(baseObservation()).evidence.citations);
	const diff =
		"diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] + insecure();\n";
	assert.equal(citationMatchesArtifact(citation, diff), true);
	assert.equal(citationMatchesArtifact({ ...citation, path: "src/Other.java" }, diff), false);
	assert.equal(citationMatchesArtifact({ ...citation, startLine: 11 }, diff), false);
	assert.equal(citationMatchesArtifact({ ...citation, endLine: 12 }, diff), false);
});

void test("removed-line citations use old-side coordinates", () => {
	const citation: NormalizedCitation = {
		...onlyCitation(normalizeObservation(baseObservation()).evidence.citations),
		side: "OLD",
		startLine: 8,
		endLine: 8,
		quote: "- requireAdmin();",
	};
	const diff =
		"--- a/src/Auth.java\n+++ b/src/Auth.java\n@@ -8 +8 @@\n[L8] - requireAdmin();\n[L8] + allowAll();\n";
	assert.equal(citationMatchesArtifact(citation, diff), true);
	assert.equal(citationMatchesArtifact({ ...citation, side: "NEW" }, diff), false);
});

// ── INCONCLUSIVE ────────────────────────────────────────────────────────────
// The orchestrator asks for this value in seventeen places. It used to be rejected here, so a model
// that obeyed got an error back and refiled the observation as NOT_APPLICABLE — writing "nothing to
// see here" into a person's record on a change where there was something to see.

void test("INCONCLUSIVE is accepted and carries no assessment", () => {
	const out = normalizeObservation({
		...baseObservation(),
		presence: "INCONCLUSIVE",
		assessment: undefined,
		evidence: { ...baseObservation().evidence, undecidability: UNDECIDABLE },
	});
	assert.equal(out.presence, "INCONCLUSIVE");
	assert.equal("assessment" in out, false);
});

void test("an assessment attached to a valence-free presence is dropped, not rejected", () => {
	const observation = (presence: Presence) =>
		presence === "NOT_APPLICABLE"
			? notApplicableObservation(goodInapplicability, { assessment: "GOOD" })
			: {
					...baseObservation(),
					presence,
					assessment: "GOOD",
					evidence: { ...baseObservation().evidence, undecidability: UNDECIDABLE },
				};
	for (const presence of ["NOT_APPLICABLE", "INCONCLUSIVE"] as const) {
		// each of the two grounds its own claim; supply whichever this presence owes
		const out = normalizeObservation(observation(presence));
		assert.equal("assessment" in out, false, `${presence} must not carry an assessment`);
	}
});

void test("carriesValence agrees with the presence/assessment coupling", () => {
	assert.equal(carriesValence("PRESENT"), true);
	assert.equal(carriesValence("ABSENT"), true);
	assert.equal(carriesValence("NOT_APPLICABLE"), false);
	assert.equal(carriesValence("INCONCLUSIVE"), false);
	// PRESENT and ABSENT still REQUIRE one.
	const missingAssessment = baseObservation();
	missingAssessment.outcome = "BEHAVIOR_PRESENT_BAD";
	assert.throws(() => normalizeObservation(missingAssessment), /requires a severity suffix/);
});

// ── Recorded search scope ────────────────────────────────────────────────────

function absentObservation(
	search: Partial<RecordedSearch> | undefined,
	overrides: ObservationOverrides = {},
) {
	return {
		...baseObservation(),
		presence: "ABSENT",
		assessment: "BAD",
		evidence: { ...baseObservation().evidence, ...(search === undefined ? {} : { search }) },
		...overrides,
	};
}

const goodSearch = {
	consulted: ["scm.review-threads"],
	lookedFor: "a review thread raising the migration",
	boundary: "only threads on this pull request; nothing in chat",
};

void test("an ABSENT observation must record where it searched", () => {
	assert.throws(
		() => normalizeObservation(absentObservation(undefined)),
		/exactly exhaustiveSearch/,
	);
	assert.throws(
		() => normalizeObservation(absentObservation({ ...goodSearch, consulted: [] })),
		/at least one source/,
	);
	assert.throws(
		() => normalizeObservation(absentObservation({ ...goodSearch, lookedFor: " " })),
		/lookedFor is required/,
	);
	assert.throws(
		() => normalizeObservation(absentObservation({ ...goodSearch, boundary: "" })),
		/boundary is required/,
	);

	const out = normalizeObservation(absentObservation(goodSearch));
	assert.deepEqual(out.evidence.search?.consulted, ["scm.review-threads"]);
});

void test("a non-ABSENT observation rejects an exhaustive-search branch", () => {
	assert.doesNotThrow(() => normalizeObservation(baseObservation()));
	assert.equal("search" in normalizeObservation(baseObservation()).evidence, false);
	assert.throws(
		() =>
			normalizeObservation({
				...baseObservation(),
				evidence: { ...baseObservation().evidence, search: goodSearch },
			}),
		/exactly citations/,
	);
});

void test("ABSENT is refused unless the search covered every source the practice asserts absence over", () => {
	const observation = normalizeObservation(absentObservation(goodSearch));
	const available = new Set(["scm.review-threads", "scm.linked-work-items"]);

	// Searched exactly the exhaustive domain.
	assert.doesNotThrow(() =>
		validateSearchScope(observation, new Set(["scm.review-threads"]), available),
	);
	// A source the practice asserts absence over that the search never opened: the claim ranges over a
	// corpus that was not read, which is the one thing an absence may never do.
	assert.throws(
		() =>
			validateSearchScope(
				observation,
				new Set(["scm.review-threads", "scm.linked-work-items"]),
				available,
			),
		/without searching scm.linked-work-items/,
	);
	// Claiming to have searched something this run never staged.
	assert.throws(() => validateSearchScope(observation, new Set(), new Set()), /was not available/);
});

// ── ABSENT + GOOD: a clean surface, over a corpus the practice bounded ───────
//
// The eight defect detectors used to forbid GOOD outright, on the true premise that "no duplication
// anywhere" cannot be proved from a fragment. The cost was that a developer who wrote clean error
// handling was told NOT_APPLICABLE — "this work had no subject for this practice" — which is false, and
// which collapses "you touched nothing relevant" together with "you did this well".
//
// The premise only ever held for an UNBOUNDED corpus. Where the practice declares an exhaustive stance
// and the search covered it whole, the negative is provable, and that is exactly the condition the
// existing search-scope rule already measures. So the gate is not new machinery: an ABSENT+GOOD is
// admitted on the same evidence an ABSENT+BAD is, plus the requirement that a corpus was declared at all.

void test("ABSENT + GOOD needs a practice that bounded its corpus; ABSENT + BAD does not", () => {
	const strength = normalizeObservation(
		absentObservation(goodSearch, { assessment: "GOOD", severity: undefined }),
	);
	const gap = normalizeObservation(absentObservation(goodSearch));
	const available = new Set(["scm.review-threads"]);

	// Declared exhaustive over the corpus it searched → the clean result is assertable.
	assert.doesNotThrow(() =>
		validateSearchScope(strength, new Set(["scm.review-threads"]), available),
	);
	// Declared nothing exhaustive → "the harmful behaviour is nowhere" ranges past anything it read.
	assert.throws(() => validateSearchScope(strength, new Set(), available), /ABSENT \+ GOOD/);
	assert.throws(() => validateSearchScope(strength, new Set(), available), /INCONCLUSIVE/);
	// A gap is anchored to the locus it cites, so it never needed a declared corpus and still does not.
	assert.doesNotThrow(() => validateSearchScope(gap, new Set(), available));
});

void test("a bounded corpus does not excuse a partial search, in either direction", () => {
	const strength = normalizeObservation(
		absentObservation(goodSearch, { assessment: "GOOD", severity: undefined }),
	);
	const available = new Set(["scm.review-threads", "scm.linked-work-items"]);
	assert.throws(
		() =>
			validateSearchScope(
				strength,
				new Set(["scm.review-threads", "scm.linked-work-items"]),
				available,
			),
		/without searching scm.linked-work-items/,
	);
});

void test("the search scope rule applies to ABSENT only", () => {
	const present = normalizeObservation(baseObservation());
	assert.doesNotThrow(() =>
		validateSearchScope(present, new Set(["scm.review-threads"]), new Set()),
	);
});

void test("a claim about an earlier review is bound to the staged history like any other citation", () => {
	// The history is staged for every review without any binding declaring it, as every source now is.
	// The point of declaring it as a source rather than dropping it in as loose context is exactly this:
	// "we raised this before" becomes a quote that has to match staged bytes, instead of a plausible
	// sentence nothing can check.
	const observation = normalizeObservation(
		baseObservation({
			evidence: {
				citations: [
					{
						sourceKind: "hephaestus.observation-history",
						artifactPath: "inputs/history/observations.json",
						path: "inputs/history/observations.json",
						startLine: 1,
						endLine: 1,
						quote: '"recurrenceKey": "rec-1"',
					},
				],
			},
		}),
	);
	const artifacts = new Map([
		["inputs/history/observations.json", "hephaestus.observation-history"],
	]);
	const staged = new Set(["scm.pull-request.diff", "hephaestus.observation-history"]);

	assert.doesNotThrow(() => validateEvidenceSources(observation, staged, artifacts));
	const bytes = '{"observations":[{"recurrenceKey": "rec-1","title":"Caught and ignored"}]}';
	assert.equal(citationMatchesArtifact(onlyCitation(observation.evidence.citations), bytes), true);
	// An earlier observation that was never staged cannot be quoted into existence.
	assert.equal(
		citationMatchesArtifact(
			{ ...onlyCitation(observation.evidence.citations), quote: '"recurrenceKey": "invented"' },
			bytes,
		),
		false,
	);
});

void test("the history is never an exhaustive source, so it can never carry an absence", () => {
	// It is a bounded window over a growing record: it can show that something recurred and can never
	// show that something never happened. No practice may hold it EXHAUSTIVE, so an ABSENT claim can
	// cite it as one place it looked but can never rest on it.
	const observation = normalizeObservation(
		absentObservation({
			consulted: ["scm.review-threads", "hephaestus.observation-history"],
			lookedFor: "a review thread raising the migration",
			boundary: "threads on this pull request, plus the earlier record for this person",
		}),
	);
	const staged = new Set(["scm.review-threads", "hephaestus.observation-history"]);

	// Consulting it is fine — it is a place the review genuinely looked.
	assert.doesNotThrow(() =>
		validateSearchScope(observation, new Set(["scm.review-threads"]), staged),
	);
});

// ── Stated inapplicability ───────────────────────────────────────────────────
// NOT_APPLICABLE was the one presence that cost nothing to say: PRESENT is warranted by its citation and
// ABSENT has to record its search, but a citation attached to NOT_APPLICABLE proves nothing about a
// practice having no subject. So it became where uncertainty drained to — 160 of them in live data against
// zero of the value that means "I looked and could not tell", a fifth of them phrased in their own
// reasoning as could-not-tell. Naming the ground is what makes the two answers cost the same.

function notApplicableObservation(
	inapplicability: Partial<RecordedInapplicability> | undefined,
	overrides: ObservationOverrides = {},
) {
	const base = baseObservation();
	return {
		...base,
		presence: "NOT_APPLICABLE",
		assessment: undefined,
		evidence: {
			...base.evidence,
			...(inapplicability === undefined ? {} : { inapplicability }),
		},
		...overrides,
	};
}

const goodInapplicability = {
	consulted: ["scm.pull-request.diff"],
	subject: "error handling around outbound network calls",
	ruledOutBy: "the change touches only Markdown documentation and makes no network calls",
};

void test("a NOT_APPLICABLE observation must say what rules the practice out", () => {
	assert.throws(
		() => normalizeObservation(notApplicableObservation(undefined)),
		/exactly exclusion/,
	);
	assert.throws(
		() => normalizeObservation(notApplicableObservation({ ...goodInapplicability, consulted: [] })),
		/at least one source/,
	);
	assert.throws(
		() => normalizeObservation(notApplicableObservation({ ...goodInapplicability, subject: " " })),
		/subject is required/,
	);
	assert.throws(
		() =>
			normalizeObservation(notApplicableObservation({ ...goodInapplicability, ruledOutBy: "" })),
		/ruledOutBy is required/,
	);

	const out = normalizeObservation(notApplicableObservation(goodInapplicability));
	const { inapplicability } = out.evidence;
	assert.ok(inapplicability, "a NOT_APPLICABLE observation must come back carrying its ground");
	assert.deepEqual(inapplicability.consulted, ["scm.pull-request.diff"]);
	assert.equal(inapplicability.subject, goodInapplicability.subject);
});

void test("the refusal points at INCONCLUSIVE, because that is the answer it is asking for", () => {
	// The whole point of the rule: a model that cannot name the ground has not found an inapplicable
	// practice, it has found one it could not call. If the error did not say so it would just teach the
	// model to invent a ruledOutBy.
	assert.throws(() => normalizeObservation(notApplicableObservation(undefined)), /exclusion/);
	assert.throws(
		() =>
			normalizeObservation(notApplicableObservation({ ...goodInapplicability, ruledOutBy: "" })),
		/ruledOutBy/,
	);
});

void test("INCONCLUSIVE needs no inapplicability block — it is not claiming anything about the work", () => {
	const out = normalizeObservation({
		...baseObservation(),
		presence: "INCONCLUSIVE",
		assessment: undefined,
		evidence: { ...baseObservation().evidence, undecidability: UNDECIDABLE },
	});
	assert.equal("inapplicability" in out.evidence, false);
});

void test("a NOT_APPLICABLE claim may only rest on sources this run staged", () => {
	const observation = normalizeObservation(notApplicableObservation(goodInapplicability));
	assert.doesNotThrow(() =>
		validateInapplicabilityScope(observation, new Set(["scm.pull-request.diff"])),
	);
	assert.throws(
		() => validateInapplicabilityScope(observation, new Set(["scm.review-threads"])),
		/was not available/,
	);

	// Every other presence is none of this validator's business.
	const present = normalizeObservation(baseObservation());
	assert.doesNotThrow(() => validateInapplicabilityScope(present, new Set()));
});

void test("removed measurement fields are rejected rather than silently accepted", () => {
	assert.throws(
		() => normalizeObservation(baseObservation({ guidance: "Split into two PRs." })),
		/unknown observation field.*guidance/,
	);
	assert.throws(
		() => normalizeObservation(baseObservation({ suggestedDiffNotes: [] })),
		/unknown observation field.*suggestedDiffNotes/,
	);
});

void test("final discriminated outcomes map to the persisted vocabulary", () => {
	const assessed = baseObservation();
	assert.deepEqual(
		{
			presence: normalizeFinalObservation(assessed).presence,
			assessment: normalizeFinalObservation(assessed).assessment,
		},
		{ presence: "PRESENT", assessment: "BAD" },
	);

	const declined = baseObservation({
		presence: "INCONCLUSIVE",
		evidence: { undecidability: UNDECIDABLE },
	});
	const normalized = normalizeFinalObservation(declined);
	assert.equal(normalized.presence, "INCONCLUSIVE");
	assert.equal(normalized.assessment, undefined);
});

void test("outcome is one exact semantic value rather than nullable peer fields", () => {
	const objectOutcome = baseObservation();
	objectOutcome.outcome = { kind: "ASSESSED", occurrence: "PRESENT", assessment: "GOOD" };
	assert.throws(() => normalizeFinalObservation(objectOutcome), /invalid outcome/);

	const invalidGood = baseObservation();
	invalidGood.outcome = "BEHAVIOR_PRESENT_GOOD_MINOR";
	assert.throws(() => normalizeFinalObservation(invalidGood), /must not carry severity/);
});

// ── Vocabulary descriptions ──────────────────────────────────────────────────
// The tool schema is generated from these, so a value with no description reaches the model as one of
// four undifferentiated words — which is how a live corpus produced NOT_APPLICABLE 61% of the time and
// INCONCLUSIVE not once. The parity test pins the vocabularies against Java; this pins the meanings
// against the vocabularies.

void test("every vocabulary value carries a description", () => {
	const vocabularies: { values: readonly string[]; descriptions: object; label: string }[] = [
		{ values: PRESENCE_VALUES, descriptions: PRESENCE_DESCRIPTIONS, label: "presence" },
		{ values: ASSESSMENT_VALUES, descriptions: ASSESSMENT_DESCRIPTIONS, label: "assessment" },
		{ values: SEVERITY_VALUES, descriptions: SEVERITY_DESCRIPTIONS, label: "severity" },
	];
	for (const { values, descriptions, label } of vocabularies) {
		assert.deepEqual(
			Object.keys(descriptions).toSorted(),
			[...values].toSorted(),
			`${label} descriptions must cover exactly ${label} values`,
		);
	}
});

void test("describeVocabulary refuses a value it cannot describe", () => {
	// The guard that makes the coverage above structural: adding a presence without describing it fails
	// when the schema is built, rather than shipping a word the model has no way to choose.
	// A description map that makes no promise about which vocabulary it covers is what lets this call
	// compile at all: describeVocabulary's own signature refuses PRESENCE_DESCRIPTIONS for a vocabulary
	// it does not describe, which is the same guard one step earlier.
	const unpromising: Record<string, string> = { ...PRESENCE_DESCRIPTIONS };
	assert.throws(
		() => describeVocabulary([...PRESENCE_VALUES, "UNDECIDED"], unpromising),
		/'UNDECIDED' has no description/,
	);
});

void test("each presence description discriminates it from its nearest neighbour", () => {
	// Not prose-checking: the failure mode is a description that defines a value in isolation, which is
	// no help at the only moment it is read — when two values both look defensible.
	const rendered = describeVocabulary(PRESENCE_VALUES, PRESENCE_DESCRIPTIONS);
	for (const value of PRESENCE_VALUES) {
		assert.ok(rendered.includes(`${value} — `), `${value} is rendered on its own line`);
		const others = PRESENCE_VALUES.filter((other) => other !== value);
		assert.ok(
			others.some((other) => PRESENCE_DESCRIPTIONS[value].includes(other)),
			`${value} must say how it differs from another presence`,
		);
	}
});

// Measured against gpt-oss-120b: asked to quote the merge-request title
// `Resolve "Connect data between screens"` verbatim, it returned curly quotes in 6 of 6 runs across
// three tool-schema shapes. The transcription was faithful; only the glyphs moved. Before this fold
// the citation failed `includes`, report_observation threw, and the observation was lost.
void test("a citation survives the typographic substitutions a model makes while transcribing", () => {
	const content = 'Resolve "Connect data between screens" — see the plan';
	const cite = (quote: string): NormalizedCitation => ({
		sourceKind: "scm.pull-request.core",
		artifactPath: "inputs/context/core.md",
		path: "title",
		startLine: 1,
		endLine: 1,
		quote,
	});

	assert.equal(
		citationMatchesArtifact(cite('Resolve "Connect data between screens"'), content),
		true,
	);
	assert.equal(
		citationMatchesArtifact(cite("Resolve “Connect data between screens”"), content),
		true,
	);
	assert.equal(citationMatchesArtifact(cite("see the plan"), content), true);
});

void test("folding glyphs never makes a quote the artifact does not contain match", () => {
	const content = 'Resolve "Connect data between screens"';
	const cite = (quote: string): NormalizedCitation => ({
		sourceKind: "scm.pull-request.core",
		artifactPath: "inputs/context/core.md",
		path: "title",
		startLine: 1,
		endLine: 1,
		quote,
	});

	assert.equal(
		citationMatchesArtifact(cite("Resolve “Disconnect data between screens”"), content),
		false,
	);
	assert.equal(citationMatchesArtifact(cite("a rationale the author never wrote"), content), false);
});

// INCONCLUSIVE was the one presence with no ground, and the bench says that mattered in both
// directions: it made the value cheap to write, and — because it appeared in no schema — hard to find.
// Moving evidence ahead of the verdict dropped it from 6/6 of the undecidable cases to 1/6; adding this
// block restored 6/6.
void test("an INCONCLUSIVE observation must say what it could not settle", () => {
	const base = {
		practiceSlug: "describe-what-and-why",
		title: "Rationale lives somewhere this review cannot read",
		presence: "INCONCLUSIVE",
		reasoning: "The body points at an issue for the why, and that issue was not staged.",
		evidence: { citations: baseObservation().evidence.citations },
	};

	assert.throws(() => normalizeObservation(base), /missingEvidence/);
	assert.throws(
		() =>
			normalizeObservation({
				...base,
				evidence: { ...base.evidence, undecidability: { openQuestion: "x" } },
			}),
		/wouldSettleIt/,
	);

	const ok = normalizeObservation({
		...base,
		evidence: {
			...base.evidence,
			undecidability: {
				openQuestion: "Whether the body states a why",
				wouldSettleIt: "The linked issue's body",
			},
		},
	});
	assert.equal(ok.presence, "INCONCLUSIVE");
	assert.equal(ok.assessment, undefined);
	assert.equal(ok.evidence.undecidability?.wouldSettleIt, "The linked issue's body");
});
