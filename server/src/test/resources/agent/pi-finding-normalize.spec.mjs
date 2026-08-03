// Keep the sandbox output boundary aligned with the Java consumer.

import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MOD = path.resolve(__dirname, "../../../main/resources/agent/pi-finding-normalize.mjs");
const { citationMatchesArtifact, normalizeFinding, dedupeKeyForFinding, validateEvidenceSources } = await import(MOD);

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
    const diff = "diff --git a/src/Auth.java b/src/Auth.java\n+++ b/src/Auth.java\n@@ -10 +10 @@\n[L10] + insecure();\n";
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
