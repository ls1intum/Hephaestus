import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MOD = path.resolve(__dirname, "../../../main/resources/agent/pi-observation-normalize.mjs");
const {
    ASSESSMENT_DESCRIPTIONS,
    ASSESSMENT_VALUES,
    PRESENCE_DESCRIPTIONS,
    PRESENCE_VALUES,
    SEVERITY_DESCRIPTIONS,
    SEVERITY_VALUES,
    carriesValence,
    citationMatchesArtifact,
    describeVocabulary,
    normalizeObservation: normalizeFinalObservation,
    dedupeKeyForObservation,
    validateEvidenceSources,
    validateSearchScope,
    validateInapplicabilityScope,
} = await import(MOD);

function baseObservation(overrides = {}) {
    const presence = String(overrides.presence ?? "PRESENT").toUpperCase();
    const assessment = String(overrides.assessment ?? "BAD").toUpperCase();
    const severity = String(overrides.severity ?? "MAJOR").toUpperCase();
    const rawEvidence = overrides.evidence ?? {};
    const evidence = {
        citations: rawEvidence.citations ?? [
            {
                sourceKind: "scm.pull-request.diff",
                artifactPath: "inputs/context/diff.patch",
                path: "src/Auth.java",
                side: "NEW",
                startLine: 10,
                endLine: 10,
                quote: "+ insecure();",
            },
        ],
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
    const translated = {
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

function normalizeObservation(input) {
    if (
        input &&
        ("presence" in input ||
            "assessment" in input ||
            "severity" in input ||
            "title" in input ||
            "reasoning" in input)
    ) {
        return normalizeFinalObservation(baseObservation(input));
    }
    if (input?.evidence && (input.evidence.search || input.evidence.inapplicability || input.evidence.undecidability)) {
        return normalizeFinalObservation({
            ...input,
            evidence: {
                citations: input.evidence.citations,
                ...(input.evidence.search == null ? {} : { exhaustiveSearch: input.evidence.search }),
                ...(input.evidence.inapplicability == null ? {} : { exclusion: input.evidence.inapplicability }),
                ...(input.evidence.undecidability == null ? {} : { missingEvidence: input.evidence.undecidability }),
            },
        });
    }
    return normalizeFinalObservation(input);
}

/** The ground an INCONCLUSIVE observation owes, mirroring `search` for ABSENT. */
const UNDECIDABLE = {
    openQuestion: "Whether the body states a why, or only restates the title",
    wouldSettleIt: "The body of the issue the description defers to",
};

test("lowercase enums + underscored slug normalize and are accepted (not dropped)", () => {
    const out = normalizeObservation(baseObservation());
    assert.equal(out.practiceSlug, "writes-focused-pull-requests");
    assert.equal(out.presence, "PRESENT");
    assert.equal(out.assessment, "BAD");
    assert.equal(out.severity, "MAJOR");
});

test("mixed-case enums up-case", () => {
    const out = normalizeObservation(baseObservation({ presence: "Present", assessment: "Good", severity: "Minor" }));
    assert.equal(out.presence, "PRESENT");
    assert.equal(out.assessment, "GOOD");
    assert.equal(out.severity, "INFO");
});

// ── No confidence ────────────────────────────────────────────────────────────
// Measured over 580 live observations, confidence never fell below 0.90 and was exactly 1.00 in 55% of
// them. A field with no usable range is not a measurement, and every consumer that ranked on it was
// ranking on noise. It is gone from the final schema, so emitting it is a contract error rather than a
// silently accepted alternate payload.

test("an observation carries no confidence, and one offered is rejected", () => {
    const out = normalizeObservation(baseObservation());
    assert.equal("confidence" in out, false);
    for (const confidence of [-1, 4200, "very", null]) {
        assert.throws(
            () => normalizeObservation(baseObservation({ confidence })),
            /unknown observation field.*confidence/,
        );
    }
});

test("NOT_APPLICABLE (lowercase) nulls assessment", () => {
    const out = normalizeObservation(
        notApplicableObservation(goodInapplicability, { presence: "not_applicable", assessment: "bad" }),
    );
    assert.equal(out.presence, "NOT_APPLICABLE");
    assert.equal(out.assessment, undefined);
});

test("dedupe key uses the normalized hyphenated slug", () => {
    const a = dedupeKeyForObservation(
        normalizeObservation(baseObservation({ practiceSlug: "writes_focused_pull_requests" })),
    );
    const b = dedupeKeyForObservation(
        normalizeObservation(baseObservation({ practiceSlug: "WRITES-FOCUSED-PULL-REQUESTS" })),
    );
    assert.equal(a, b, "underscored and upper-hyphenated slugs must dedupe to the same key");
});

test("genuinely invalid enum still rejected after normalization", () => {
    const invalid = baseObservation();
    invalid.outcome = "MAYBE";
    assert.throws(() => normalizeObservation(invalid), /invalid outcome/);
});

test("missing evidence-source attribution is rejected", () => {
    assert.throws(
        () => normalizeObservation(baseObservation({ evidence: { citations: [] } })),
        /citations are required/,
    );
});

test("citation requires an exact artifact path and quote", () => {
    const missingPath = baseObservation();
    delete missingPath.evidence.citations[0].artifactPath;
    assert.throws(() => normalizeObservation(missingPath), /artifactPath is required/);

    const missingQuote = baseObservation();
    delete missingQuote.evidence.citations[0].quote;
    assert.throws(() => normalizeObservation(missingQuote), /quote is required/);
});

test("citation side is present exactly for pull-request diffs", () => {
    const missingDiffSide = baseObservation();
    delete missingDiffSide.evidence.citations[0].side;
    assert.throws(() => normalizeObservation(missingDiffSide), /side must be OLD or NEW/);

    const nonDiffSide = baseObservation();
    nonDiffSide.evidence.citations[0].sourceKind = "scm.pull-request.core";
    assert.throws(() => normalizeObservation(nonDiffSide), /must not specify side/);
});

test("a citation must name a source this run staged, and the artifact that source produced", () => {
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
    assert.throws(() => validateEvidenceSources(observation, new Set(), new Map()), /was not available/);
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

test("diff citations bind the quote to the claimed file and line", () => {
    const citation = normalizeObservation(baseObservation()).evidence.citations[0];
    const diff =
        "diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] + insecure();\n";
    assert.equal(citationMatchesArtifact(citation, diff), true);
    assert.equal(citationMatchesArtifact({ ...citation, path: "src/Other.java" }, diff), false);
    assert.equal(citationMatchesArtifact({ ...citation, startLine: 11 }, diff), false);
    assert.equal(citationMatchesArtifact({ ...citation, endLine: 12 }, diff), false);
});

test("removed-line citations use old-side coordinates", () => {
    const citation = {
        ...normalizeObservation(baseObservation()).evidence.citations[0],
        side: "OLD",
        startLine: 8,
        endLine: 8,
        quote: "- requireAdmin();",
    };
    const diff = "--- a/src/Auth.java\n+++ b/src/Auth.java\n@@ -8 +8 @@\n[L8] - requireAdmin();\n[L8] + allowAll();\n";
    assert.equal(citationMatchesArtifact(citation, diff), true);
    assert.equal(citationMatchesArtifact({ ...citation, side: "NEW" }, diff), false);
});

// ── INCONCLUSIVE ────────────────────────────────────────────────────────────
// The orchestrator asks for this value in seventeen places. It used to be rejected here, so a model
// that obeyed got an error back and refiled the observation as NOT_APPLICABLE — writing "nothing to
// see here" into a person's record on a change where there was something to see.

test("INCONCLUSIVE is accepted and carries no assessment", () => {
    const out = normalizeObservation({
        ...baseObservation(),
        presence: "INCONCLUSIVE",
        assessment: undefined,
        evidence: { ...baseObservation().evidence, undecidability: UNDECIDABLE },
    });
    assert.equal(out.presence, "INCONCLUSIVE");
    assert.equal("assessment" in out, false);
});

test("an assessment attached to a valence-free presence is dropped, not rejected", () => {
    const observation = (presence) =>
        presence === "NOT_APPLICABLE"
            ? notApplicableObservation(goodInapplicability, { assessment: "GOOD" })
            : {
                  ...baseObservation(),
                  presence,
                  assessment: "GOOD",
                  evidence: { ...baseObservation().evidence, undecidability: UNDECIDABLE },
              };
    for (const presence of ["NOT_APPLICABLE", "INCONCLUSIVE"]) {
        // each of the two grounds its own claim; supply whichever this presence owes
        const out = normalizeObservation(observation(presence));
        assert.equal("assessment" in out, false, `${presence} must not carry an assessment`);
    }
});

test("carriesValence agrees with the presence/assessment coupling", () => {
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

function absentObservation(search, overrides = {}) {
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

test("an ABSENT observation must record where it searched", () => {
    assert.throws(() => normalizeObservation(absentObservation(undefined)), /exactly exhaustiveSearch/);
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
    assert.deepEqual(out.evidence.search.consulted, ["scm.review-threads"]);
});

test("a non-ABSENT observation rejects an exhaustive-search branch", () => {
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

test("ABSENT is refused unless the search covered every source the practice asserts absence over", () => {
    const observation = normalizeObservation(absentObservation(goodSearch));
    const available = new Set(["scm.review-threads", "scm.linked-work-items"]);

    // Searched exactly the exhaustive domain.
    assert.doesNotThrow(() => validateSearchScope(observation, new Set(["scm.review-threads"]), available));
    // A source the practice asserts absence over that the search never opened: the claim ranges over a
    // corpus that was not read, which is the one thing an absence may never do.
    assert.throws(
        () => validateSearchScope(observation, new Set(["scm.review-threads", "scm.linked-work-items"]), available),
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

test("ABSENT + GOOD needs a practice that bounded its corpus; ABSENT + BAD does not", () => {
    const strength = normalizeObservation(absentObservation(goodSearch, { assessment: "GOOD", severity: undefined }));
    const gap = normalizeObservation(absentObservation(goodSearch));
    const available = new Set(["scm.review-threads"]);

    // Declared exhaustive over the corpus it searched → the clean result is assertable.
    assert.doesNotThrow(() => validateSearchScope(strength, new Set(["scm.review-threads"]), available));
    // Declared nothing exhaustive → "the harmful behaviour is nowhere" ranges past anything it read.
    assert.throws(() => validateSearchScope(strength, new Set(), available), /ABSENT \+ GOOD/);
    assert.throws(() => validateSearchScope(strength, new Set(), available), /INCONCLUSIVE/);
    // A gap is anchored to the locus it cites, so it never needed a declared corpus and still does not.
    assert.doesNotThrow(() => validateSearchScope(gap, new Set(), available));
});

test("a bounded corpus does not excuse a partial search, in either direction", () => {
    const strength = normalizeObservation(absentObservation(goodSearch, { assessment: "GOOD", severity: undefined }));
    const available = new Set(["scm.review-threads", "scm.linked-work-items"]);
    assert.throws(
        () => validateSearchScope(strength, new Set(["scm.review-threads", "scm.linked-work-items"]), available),
        /without searching scm.linked-work-items/,
    );
});

test("the search scope rule applies to ABSENT only", () => {
    const present = normalizeObservation(baseObservation());
    assert.doesNotThrow(() => validateSearchScope(present, new Set(["scm.review-threads"]), new Set()));
});

test("a claim about an earlier review is bound to the staged history like any other citation", () => {
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
    const artifacts = new Map([["inputs/history/observations.json", "hephaestus.observation-history"]]);
    const staged = new Set(["scm.pull-request.diff", "hephaestus.observation-history"]);

    assert.doesNotThrow(() => validateEvidenceSources(observation, staged, artifacts));
    const bytes = '{"observations":[{"recurrenceKey": "rec-1","title":"Caught and ignored"}]}';
    assert.equal(citationMatchesArtifact(observation.evidence.citations[0], bytes), true);
    // An earlier observation that was never staged cannot be quoted into existence.
    assert.equal(
        citationMatchesArtifact({ ...observation.evidence.citations[0], quote: '"recurrenceKey": "invented"' }, bytes),
        false,
    );
});

test("the history is never an exhaustive source, so it can never carry an absence", () => {
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
    assert.doesNotThrow(() => validateSearchScope(observation, new Set(["scm.review-threads"]), staged));
});

// ── Stated inapplicability ───────────────────────────────────────────────────
// NOT_APPLICABLE was the one presence that cost nothing to say: PRESENT is warranted by its citation and
// ABSENT has to record its search, but a citation attached to NOT_APPLICABLE proves nothing about a
// practice having no subject. So it became where uncertainty drained to — 160 of them in live data against
// zero of the value that means "I looked and could not tell", a fifth of them phrased in their own
// reasoning as could-not-tell. Naming the ground is what makes the two answers cost the same.

function notApplicableObservation(inapplicability, overrides = {}) {
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

test("a NOT_APPLICABLE observation must say what rules the practice out", () => {
    assert.throws(() => normalizeObservation(notApplicableObservation(undefined)), /exactly exclusion/);
    assert.throws(
        () => normalizeObservation(notApplicableObservation({ ...goodInapplicability, consulted: [] })),
        /at least one source/,
    );
    assert.throws(
        () => normalizeObservation(notApplicableObservation({ ...goodInapplicability, subject: " " })),
        /subject is required/,
    );
    assert.throws(
        () => normalizeObservation(notApplicableObservation({ ...goodInapplicability, ruledOutBy: "" })),
        /ruledOutBy is required/,
    );

    const out = normalizeObservation(notApplicableObservation(goodInapplicability));
    assert.deepEqual(out.evidence.inapplicability.consulted, ["scm.pull-request.diff"]);
    assert.equal(out.evidence.inapplicability.subject, goodInapplicability.subject);
});

test("the refusal points at INCONCLUSIVE, because that is the answer it is asking for", () => {
    // The whole point of the rule: a model that cannot name the ground has not found an inapplicable
    // practice, it has found one it could not call. If the error did not say so it would just teach the
    // model to invent a ruledOutBy.
    assert.throws(() => normalizeObservation(notApplicableObservation(undefined)), /exclusion/);
    assert.throws(
        () => normalizeObservation(notApplicableObservation({ ...goodInapplicability, ruledOutBy: "" })),
        /ruledOutBy/,
    );
});

test("INCONCLUSIVE needs no inapplicability block — it is not claiming anything about the work", () => {
    const out = normalizeObservation({
        ...baseObservation(),
        presence: "INCONCLUSIVE",
        assessment: undefined,
        evidence: { ...baseObservation().evidence, undecidability: UNDECIDABLE },
    });
    assert.equal("inapplicability" in out.evidence, false);
});

test("a NOT_APPLICABLE claim may only rest on sources this run staged", () => {
    const observation = normalizeObservation(notApplicableObservation(goodInapplicability));
    assert.doesNotThrow(() => validateInapplicabilityScope(observation, new Set(["scm.pull-request.diff"])));
    assert.throws(
        () => validateInapplicabilityScope(observation, new Set(["scm.review-threads"])),
        /was not available/,
    );

    // Every other presence is none of this validator's business.
    const present = normalizeObservation(baseObservation());
    assert.doesNotThrow(() => validateInapplicabilityScope(present, new Set()));
});

test("removed measurement fields are rejected rather than silently accepted", () => {
    assert.throws(
        () => normalizeObservation(baseObservation({ guidance: "Split into two PRs." })),
        /unknown observation field.*guidance/,
    );
    assert.throws(
        () => normalizeObservation(baseObservation({ suggestedDiffNotes: [] })),
        /unknown observation field.*suggestedDiffNotes/,
    );
});

test("final discriminated outcomes map to the persisted vocabulary", () => {
    const assessed = baseObservation();
    assert.deepEqual(
        {
            presence: normalizeFinalObservation(assessed).presence,
            assessment: normalizeFinalObservation(assessed).assessment,
        },
        { presence: "PRESENT", assessment: "BAD" },
    );

    const declined = baseObservation({ presence: "INCONCLUSIVE", evidence: { undecidability: UNDECIDABLE } });
    const normalized = normalizeFinalObservation(declined);
    assert.equal(normalized.presence, "INCONCLUSIVE");
    assert.equal(normalized.assessment, undefined);
});

test("outcome is one exact semantic value rather than nullable peer fields", () => {
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

test("every vocabulary value carries a description", () => {
    for (const [values, descriptions, label] of [
        [PRESENCE_VALUES, PRESENCE_DESCRIPTIONS, "presence"],
        [ASSESSMENT_VALUES, ASSESSMENT_DESCRIPTIONS, "assessment"],
        [SEVERITY_VALUES, SEVERITY_DESCRIPTIONS, "severity"],
    ]) {
        assert.deepEqual(
            Object.keys(descriptions).sort(),
            [...values].sort(),
            `${label} descriptions must cover exactly ${label} values`,
        );
    }
});

test("describeVocabulary refuses a value it cannot describe", () => {
    // The guard that makes the coverage above structural: adding a presence without describing it fails
    // when the schema is built, rather than shipping a word the model has no way to choose.
    assert.throws(
        () => describeVocabulary([...PRESENCE_VALUES, "UNDECIDED"], PRESENCE_DESCRIPTIONS),
        /'UNDECIDED' has no description/,
    );
});

test("each presence description discriminates it from its nearest neighbour", () => {
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
test("a citation survives the typographic substitutions a model makes while transcribing", () => {
    const content = 'Resolve "Connect data between screens" — see the plan';
    const cite = (quote) => ({ sourceKind: "scm.pull-request.core", quote });

    assert.equal(citationMatchesArtifact(cite('Resolve "Connect data between screens"'), content), true);
    assert.equal(citationMatchesArtifact(cite("Resolve “Connect data between screens”"), content), true);
    assert.equal(citationMatchesArtifact(cite("see the plan"), content), true);
});

test("folding glyphs never makes a quote the artifact does not contain match", () => {
    const content = 'Resolve "Connect data between screens"';
    const cite = (quote) => ({ sourceKind: "scm.pull-request.core", quote });

    assert.equal(citationMatchesArtifact(cite("Resolve “Disconnect data between screens”"), content), false);
    assert.equal(citationMatchesArtifact(cite("a rationale the author never wrote"), content), false);
});

// INCONCLUSIVE was the one presence with no ground, and the bench says that mattered in both
// directions: it made the value cheap to write, and — because it appeared in no schema — hard to find.
// Moving evidence ahead of the verdict dropped it from 6/6 of the undecidable cases to 1/6; adding this
// block restored 6/6.
test("an INCONCLUSIVE observation must say what it could not settle", () => {
    const base = {
        practiceSlug: "describe-what-and-why",
        title: "Rationale lives somewhere this review cannot read",
        presence: "INCONCLUSIVE",
        reasoning: "The body points at an issue for the why, and that issue was not staged.",
        evidence: { citations: baseObservation().evidence.citations },
    };

    assert.throws(() => normalizeObservation(base), /missingEvidence/);
    assert.throws(
        () => normalizeObservation({ ...base, evidence: { ...base.evidence, undecidability: { openQuestion: "x" } } }),
        /wouldSettleIt/,
    );

    const ok = normalizeObservation({
        ...base,
        evidence: {
            ...base.evidence,
            undecidability: { openQuestion: "Whether the body states a why", wouldSettleIt: "The linked issue's body" },
        },
    });
    assert.equal(ok.presence, "INCONCLUSIVE");
    assert.equal(ok.assessment, undefined);
    assert.equal(ok.evidence.undecidability.wouldSettleIt, "The linked issue's body");
});
