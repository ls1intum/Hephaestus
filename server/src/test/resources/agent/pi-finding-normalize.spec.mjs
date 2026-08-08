import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MOD = path.resolve(__dirname, "../../../main/resources/agent/pi-finding-normalize.mjs");
const {
    carriesValence,
    citationMatchesArtifact,
    normalizeFinding,
    dedupeKeyForFinding,
    validateEvidenceSources,
    validateSearchScope,
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
    const out = normalizeFinding(baseFinding({ presence: "not_applicable", assessment: "bad" }));
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

test("evidence sources must be declared and available", () => {
    const finding = normalizeFinding(baseFinding());
    assert.doesNotThrow(() =>
        validateEvidenceSources(
            finding,
            new Set(["scm.pull-request.diff"]),
            new Set(["scm.pull-request.diff"]),
            new Map([["inputs/context/diff.patch", "scm.pull-request.diff"]]),
        ),
    );
    assert.throws(
        () => validateEvidenceSources(finding, new Set(["scm.pull-request.core"]), new Set(["scm.pull-request.diff"])),
        /does not declare/,
    );
    assert.throws(
        () => validateEvidenceSources(finding, new Set(["scm.pull-request.diff"]), new Set(), new Map()),
        /was not available/,
    );
    assert.throws(
        () =>
            validateEvidenceSources(
                finding,
                new Set(["scm.pull-request.diff"]),
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

// ── INDETERMINATE ────────────────────────────────────────────────────────────
// The orchestrator asks for this value in seventeen places. It used to be rejected here, so a model
// that obeyed got an error back and refiled the observation as NOT_APPLICABLE — writing "nothing to
// see here" into a person's record on a change where there was something to see.

test("INDETERMINATE is accepted and carries no assessment", () => {
    const out = normalizeFinding({ ...baseFinding(), presence: "INDETERMINATE", assessment: undefined });
    assert.equal(out.presence, "INDETERMINATE");
    assert.equal("assessment" in out, false);
});

test("an assessment attached to a valence-free presence is dropped, not rejected", () => {
    for (const presence of ["NOT_APPLICABLE", "INDETERMINATE"]) {
        const out = normalizeFinding({ ...baseFinding(), presence, assessment: "GOOD" });
        assert.equal("assessment" in out, false, `${presence} must not carry an assessment`);
    }
});

test("carriesValence agrees with the presence/assessment coupling", () => {
    assert.equal(carriesValence("PRESENT"), true);
    assert.equal(carriesValence("ABSENT"), true);
    assert.equal(carriesValence("NOT_APPLICABLE"), false);
    assert.equal(carriesValence("INDETERMINATE"), false);
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
    const declared = new Set(["scm.review-threads", "scm.linked-work-items"]);
    const available = new Set(["scm.review-threads", "scm.linked-work-items"]);

    // Searched exactly the exhaustive domain.
    assert.doesNotThrow(() =>
        validateSearchScope(finding, new Set(["scm.review-threads"]), declared, available),
    );
    // A source the practice asserts absence over that the search never opened: the claim ranges over a
    // corpus that was not read, which is the one thing an absence may never do.
    assert.throws(
        () => validateSearchScope(finding, new Set(["scm.review-threads", "scm.linked-work-items"]), declared, available),
        /without searching scm.linked-work-items/,
    );
    // Claiming to have searched something this run never staged.
    assert.throws(
        () => validateSearchScope(finding, new Set(), new Set(["scm.linked-work-items"]), available),
        /does not declare evidence source/,
    );
    assert.throws(() => validateSearchScope(finding, new Set(), declared, new Set()), /was not available/);
});

test("the search scope rule applies to ABSENT only", () => {
    const present = normalizeFinding(baseFinding());
    assert.doesNotThrow(() =>
        validateSearchScope(present, new Set(["scm.review-threads"]), new Set(), new Set()),
    );
});
