import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MOD = path.resolve(__dirname, "../../../main/resources/agent/pi-finding-normalize.mjs");
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
    normalizeFinding,
    dedupeKeyForFinding,
    validateEvidenceSources,
    validateSearchScope,
    validateInapplicabilityScope,
} = await import(MOD);

function baseFinding(overrides = {}) {
    return {
        practiceSlug: "writes_focused_pull_requests",
        title: "PR mixes unrelated changes",
        presence: "present",
        assessment: "bad",
        severity: "major",
        confidence: 0.8,
        reasoning: "The diff touches auth and billing in one PR.",
        guidance: "Split into two PRs.",
        evidence: {
            citations: [
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
        },
        ...overrides,
    };
}

test("lowercase enums + underscored slug normalize and are accepted (not dropped)", () => {
    const out = normalizeFinding(baseFinding());
    assert.equal(out.practiceSlug, "writes-focused-pull-requests");
    assert.equal(out.presence, "PRESENT");
    assert.equal(out.assessment, "BAD");
    assert.equal(out.severity, "MAJOR");
});

test("mixed-case enums up-case", () => {
    const out = normalizeFinding(baseFinding({ presence: "Present", assessment: "Good", severity: "Minor" }));
    assert.equal(out.presence, "PRESENT");
    assert.equal(out.assessment, "GOOD");
    assert.equal(out.severity, "MINOR");
});

test("NOT_APPLICABLE (lowercase) nulls assessment", () => {
    const out = normalizeFinding(notApplicableFinding(goodInapplicability, { presence: "not_applicable", assessment: "bad" }));
    assert.equal(out.presence, "NOT_APPLICABLE");
    assert.equal(out.assessment, undefined);
});

test("dedupe key uses the normalized hyphenated slug", () => {
    const a = dedupeKeyForFinding(normalizeFinding(baseFinding({ practiceSlug: "writes_focused_pull_requests" })));
    const b = dedupeKeyForFinding(normalizeFinding(baseFinding({ practiceSlug: "WRITES-FOCUSED-PULL-REQUESTS" })));
    assert.equal(a, b, "underscored and upper-hyphenated slugs must dedupe to the same key");
});

test("genuinely invalid enum still rejected after normalization", () => {
    assert.throws(() => normalizeFinding(baseFinding({ presence: "maybe" })), /invalid presence/);
});

test("missing evidence-source attribution is rejected", () => {
    assert.throws(() => normalizeFinding(baseFinding({ evidence: { citations: [] } })), /citations are required/);
});

test("citation requires an exact artifact path and quote", () => {
    const missingPath = baseFinding();
    delete missingPath.evidence.citations[0].artifactPath;
    assert.throws(() => normalizeFinding(missingPath), /artifactPath is required/);

    const missingQuote = baseFinding();
    delete missingQuote.evidence.citations[0].quote;
    assert.throws(() => normalizeFinding(missingQuote), /quote is required/);
});

test("citation side is present exactly for pull-request diffs", () => {
    const missingDiffSide = baseFinding();
    delete missingDiffSide.evidence.citations[0].side;
    assert.throws(() => normalizeFinding(missingDiffSide), /side must be OLD or NEW/);

    const nonDiffSide = baseFinding();
    nonDiffSide.evidence.citations[0].sourceKind = "scm.pull-request.core";
    assert.throws(() => normalizeFinding(nonDiffSide), /must not specify side/);
});

test("a citation must name a source this run staged, and the artifact that source produced", () => {
    const finding = normalizeFinding(baseFinding());
    assert.doesNotThrow(() =>
        validateEvidenceSources(
            finding,
            new Set(["scm.pull-request.diff"]),
            new Map([["inputs/context/diff.patch", "scm.pull-request.diff"]]),
        ),
    );
    // The practice's own bindings no longer narrow this: every source that applies to the artifact is
    // staged, so a quote from one the practice did not name is still a quote from bytes that were there.
    assert.doesNotThrow(() =>
        validateEvidenceSources(
            finding,
            new Set(["scm.pull-request.diff", "workspace.project-inventory"]),
            new Map([["inputs/context/diff.patch", "scm.pull-request.diff"]]),
        ),
    );
    assert.throws(() => validateEvidenceSources(finding, new Set(), new Map()), /was not available/);
    assert.throws(
        () =>
            validateEvidenceSources(
                finding,
                new Set(["scm.pull-request.diff"]),
                new Map([["inputs/context/diff.patch", "scm.pull-request.core"]]),
            ),
        /does not belong/,
    );
});

test("diff citations bind the quote to the claimed file and line", () => {
    const citation = normalizeFinding(baseFinding()).evidence.citations[0];
    const diff =
        "diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] + insecure();\n";
    assert.equal(citationMatchesArtifact(citation, diff), true);
    assert.equal(citationMatchesArtifact({ ...citation, path: "src/Other.java" }, diff), false);
    assert.equal(citationMatchesArtifact({ ...citation, startLine: 11 }, diff), false);
    assert.equal(citationMatchesArtifact({ ...citation, endLine: 12 }, diff), false);
});

test("removed-line citations use old-side coordinates", () => {
    const citation = {
        ...normalizeFinding(baseFinding()).evidence.citations[0],
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
    const out = normalizeFinding({ ...baseFinding(), presence: "INCONCLUSIVE", assessment: undefined });
    assert.equal(out.presence, "INCONCLUSIVE");
    assert.equal("assessment" in out, false);
});

test("an assessment attached to a valence-free presence is dropped, not rejected", () => {
    const finding = (presence) =>
        presence === "NOT_APPLICABLE"
            ? notApplicableFinding(goodInapplicability, { assessment: "GOOD" })
            : { ...baseFinding(), presence, assessment: "GOOD" };
    for (const presence of ["NOT_APPLICABLE", "INCONCLUSIVE"]) {
        const out = normalizeFinding(finding(presence));
        assert.equal("assessment" in out, false, `${presence} must not carry an assessment`);
    }
});

test("carriesValence agrees with the presence/assessment coupling", () => {
    assert.equal(carriesValence("PRESENT"), true);
    assert.equal(carriesValence("ABSENT"), true);
    assert.equal(carriesValence("NOT_APPLICABLE"), false);
    assert.equal(carriesValence("INCONCLUSIVE"), false);
    // PRESENT and ABSENT still REQUIRE one.
    assert.throws(() => normalizeFinding({ ...baseFinding(), assessment: undefined }), /invalid assessment/);
});

// ── Recorded search scope ────────────────────────────────────────────────────

function absentFinding(search, overrides = {}) {
    return {
        ...baseFinding(),
        presence: "ABSENT",
        assessment: "BAD",
        evidence: { ...baseFinding().evidence, ...(search === undefined ? {} : { search }) },
        ...overrides,
    };
}

const goodSearch = {
    consulted: ["scm.review-threads"],
    lookedFor: "a review thread raising the migration",
    boundary: "only threads on this pull request; nothing in chat",
};

test("an ABSENT observation must record where it searched", () => {
    assert.throws(() => normalizeFinding(absentFinding(undefined)), /must record its search/);
    assert.throws(() => normalizeFinding(absentFinding({ ...goodSearch, consulted: [] })), /at least one source/);
    assert.throws(() => normalizeFinding(absentFinding({ ...goodSearch, lookedFor: " " })), /lookedFor is required/);
    assert.throws(() => normalizeFinding(absentFinding({ ...goodSearch, boundary: "" })), /boundary is required/);

    const out = normalizeFinding(absentFinding(goodSearch));
    assert.deepEqual(out.evidence.search.consulted, ["scm.review-threads"]);
});

test("a non-ABSENT observation needs no search block, but keeps one it offers", () => {
    assert.doesNotThrow(() => normalizeFinding(baseFinding()));
    assert.equal("search" in normalizeFinding(baseFinding()).evidence, false);
    const withSearch = normalizeFinding({ ...baseFinding(), evidence: { ...baseFinding().evidence, search: goodSearch } });
    assert.equal(withSearch.evidence.search.lookedFor, goodSearch.lookedFor);
});

test("ABSENT is refused unless the search covered every source the practice asserts absence over", () => {
    const finding = normalizeFinding(absentFinding(goodSearch));
    const available = new Set(["scm.review-threads", "scm.linked-work-items"]);

    // Searched exactly the exhaustive domain.
    assert.doesNotThrow(() => validateSearchScope(finding, new Set(["scm.review-threads"]), available));
    // A source the practice asserts absence over that the search never opened: the claim ranges over a
    // corpus that was not read, which is the one thing an absence may never do.
    assert.throws(
        () => validateSearchScope(finding, new Set(["scm.review-threads", "scm.linked-work-items"]), available),
        /without searching scm.linked-work-items/,
    );
    // Claiming to have searched something this run never staged.
    assert.throws(() => validateSearchScope(finding, new Set(), new Set()), /was not available/);
});

test("the search scope rule applies to ABSENT only", () => {
    const present = normalizeFinding(baseFinding());
    assert.doesNotThrow(() =>
        validateSearchScope(present, new Set(["scm.review-threads"]), new Set()),
    );
});

test("a claim about an earlier review is bound to the staged history like any other citation", () => {
    // The history is staged for every review without any binding declaring it, as every source now is.
    // The point of declaring it as a source rather than dropping it in as loose context is exactly this:
    // "we raised this before" becomes a quote that has to match staged bytes, instead of a plausible
    // sentence nothing can check.
    const finding = normalizeFinding(
        baseFinding({
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

    assert.doesNotThrow(() => validateEvidenceSources(finding, staged, artifacts));
    const bytes = '{"observations":[{"recurrenceKey": "rec-1","title":"Caught and ignored"}]}';
    assert.equal(citationMatchesArtifact(finding.evidence.citations[0], bytes), true);
    // An earlier observation that was never staged cannot be quoted into existence.
    assert.equal(
        citationMatchesArtifact({ ...finding.evidence.citations[0], quote: '"recurrenceKey": "invented"' }, bytes),
        false,
    );
});

test("the history is never an exhaustive source, so it can never carry an absence", () => {
    // It is a bounded window over a growing record: it can show that something recurred and can never
    // show that something never happened. No practice may hold it EXHAUSTIVE, so an ABSENT claim can
    // cite it as one place it looked but can never rest on it.
    const finding = normalizeFinding(
        absentFinding({
            consulted: ["scm.review-threads", "hephaestus.observation-history"],
            lookedFor: "a review thread raising the migration",
            boundary: "threads on this pull request, plus the earlier record for this person",
        }),
    );
    const staged = new Set(["scm.review-threads", "hephaestus.observation-history"]);

    // Consulting it is fine — it is a place the review genuinely looked.
    assert.doesNotThrow(() => validateSearchScope(finding, new Set(["scm.review-threads"]), staged));
});

// ── Stated inapplicability ───────────────────────────────────────────────────
// NOT_APPLICABLE was the one presence that cost nothing to say: PRESENT is warranted by its citation and
// ABSENT has to record its search, but a citation attached to NOT_APPLICABLE proves nothing about a
// practice having no subject. So it became where uncertainty drained to — 160 of them in live data against
// zero of the value that means "I looked and could not tell", a fifth of them phrased in their own
// reasoning as could-not-tell. Naming the ground is what makes the two answers cost the same.

function notApplicableFinding(inapplicability, overrides = {}) {
    const base = baseFinding();
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
    assert.throws(() => normalizeFinding(notApplicableFinding(undefined)), /must say why the practice does not apply/);
    assert.throws(
        () => normalizeFinding(notApplicableFinding({ ...goodInapplicability, consulted: [] })),
        /at least one source/,
    );
    assert.throws(
        () => normalizeFinding(notApplicableFinding({ ...goodInapplicability, subject: " " })),
        /subject is required/,
    );
    assert.throws(
        () => normalizeFinding(notApplicableFinding({ ...goodInapplicability, ruledOutBy: "" })),
        /ruledOutBy is required/,
    );

    const out = normalizeFinding(notApplicableFinding(goodInapplicability));
    assert.deepEqual(out.evidence.inapplicability.consulted, ["scm.pull-request.diff"]);
    assert.equal(out.evidence.inapplicability.subject, goodInapplicability.subject);
});

test("the refusal points at INCONCLUSIVE, because that is the answer it is asking for", () => {
    // The whole point of the rule: a model that cannot name the ground has not found an inapplicable
    // practice, it has found one it could not call. If the error did not say so it would just teach the
    // model to invent a ruledOutBy.
    assert.throws(() => normalizeFinding(notApplicableFinding(undefined)), /INCONCLUSIVE/);
    assert.throws(
        () => normalizeFinding(notApplicableFinding({ ...goodInapplicability, ruledOutBy: "" })),
        /INCONCLUSIVE/,
    );
});

test("INCONCLUSIVE needs no inapplicability block — it is not claiming anything about the work", () => {
    const out = normalizeFinding({ ...baseFinding(), presence: "INCONCLUSIVE", assessment: undefined });
    assert.equal("inapplicability" in out.evidence, false);
});

test("a NOT_APPLICABLE claim may only rest on sources this run staged", () => {
    const finding = normalizeFinding(notApplicableFinding(goodInapplicability));
    assert.doesNotThrow(() => validateInapplicabilityScope(finding, new Set(["scm.pull-request.diff"])));
    assert.throws(() => validateInapplicabilityScope(finding, new Set(["scm.review-threads"])), /was not available/);

    // Every other presence is none of this validator's business.
    const present = normalizeFinding(baseFinding());
    assert.doesNotThrow(() => validateInapplicabilityScope(present, new Set()));
});

// ── Optional guidance ────────────────────────────────────────────────────────
// guidance was REQUIRED with minLength 1 while composition still authored nothing. Now that the
// composition stage writes what the developer reads, guidance survives only as the fallback body of an
// in-context note — and demanding one on every finding made the measurement step invent a next step for
// a strength, for a practice with no subject here, and for a question it could not settle. Those are
// exactly the three answers that assert nothing is wrong.

test("a finding with no guidance is accepted, and the key is simply absent", () => {
    const finding = baseFinding();
    delete finding.guidance;
    const out = normalizeFinding(finding);
    assert.equal("guidance" in out, false);
});

test("blank guidance collapses to absent rather than to an empty string", () => {
    // One shape reaches Java, not two: DeliveryComposer falls back on guidance being null, and a "" that
    // survived would read as an authored-but-empty next step.
    for (const blank of ["", "   ", "\n"]) {
        const out = normalizeFinding(baseFinding({ guidance: blank }));
        assert.equal("guidance" in out, false, `guidance ${JSON.stringify(blank)} must not survive`);
    }
});

test("guidance is kept, trimmed, when there really is a next step", () => {
    const out = normalizeFinding(baseFinding({ guidance: "  Split into two PRs.  " }));
    assert.equal(out.guidance, "Split into two PRs.");
});

test("dropping guidance changes nothing else about the finding", () => {
    const withGuidance = normalizeFinding(baseFinding());
    const withoutGuidance = normalizeFinding(baseFinding({ guidance: undefined }));
    assert.equal(dedupeKeyForFinding(withGuidance), dedupeKeyForFinding(withoutGuidance));
    assert.deepEqual({ ...withGuidance, guidance: undefined }, { ...withoutGuidance, guidance: undefined });
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
