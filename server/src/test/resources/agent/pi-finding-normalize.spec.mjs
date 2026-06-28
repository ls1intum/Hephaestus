// pi-finding-normalize.spec.mjs — node:test suite proving the report_finding tool boundary
// normalizes findings exactly the way the Java consumer PracticeDetectionResultParser does.
//
// Regression guard for SYSTEMIC #2 (runner<->parser case asymmetry): lowercase enums and
// underscored / non-canonical slugs that the Java parser up-cases were previously REJECTED at
// this JS boundary, silently dropping real findings. Run locally with:
//   node --test server/src/test/resources/agent/pi-finding-normalize.spec.mjs

import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MOD = path.resolve(__dirname, "../../../main/resources/agent/pi-finding-normalize.mjs");
const { normalizeFinding, dedupeKeyForFinding } = await import(MOD);

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
        evidence: { locations: [{ path: "src/Auth.java", startLine: 10, endLine: 12 }], snippets: [] },
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
