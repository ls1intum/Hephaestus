// node:test suite proving the report_observation tool boundary normalizes observations exactly the way
// the Java consumer PracticeDetectionResultParser does. The JS boundary must up-case enums and
// canonicalize underscored / non-canonical slugs the same way the Java parser does, or valid
// observations get dropped at this seam. Run locally with:
//   node --test server/src/test/resources/agent/pi-observation-normalize.spec.mjs

import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const MOD = path.resolve(__dirname, "../../../main/resources/agent/pi-observation-normalize.mjs");
const { normalizeObservation, dedupeKeyForObservation } = await import(MOD);

function baseObservation(overrides = {}) {
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
    assert.equal(out.severity, "MINOR");
});

test("NOT_APPLICABLE (lowercase) nulls assessment", () => {
    const out = normalizeObservation(baseObservation({ presence: "not_applicable", assessment: "bad" }));
    assert.equal(out.presence, "NOT_APPLICABLE");
    assert.equal(out.assessment, undefined);
});

test("subjectLogin is passed through trimmed when present", () => {
    const out = normalizeObservation(baseObservation({ subjectLogin: "  reviewer-bob  " }));
    assert.equal(out.subjectLogin, "reviewer-bob");
});

test("subjectLogin is omitted when absent or blank", () => {
    assert.equal(normalizeObservation(baseObservation()).subjectLogin, undefined);
    assert.equal(normalizeObservation(baseObservation({ subjectLogin: "   " })).subjectLogin, undefined);
});

test("dedupe key uses the normalized hyphenated slug", () => {
    const a = dedupeKeyForObservation(normalizeObservation(baseObservation({ practiceSlug: "writes_focused_pull_requests" })));
    const b = dedupeKeyForObservation(normalizeObservation(baseObservation({ practiceSlug: "WRITES-FOCUSED-PULL-REQUESTS" })));
    assert.equal(a, b, "underscored and upper-hyphenated slugs must dedupe to the same key");
});

test("genuinely invalid enum still rejected after normalization", () => {
    assert.throws(() => normalizeObservation(baseObservation({ presence: "maybe" })), /invalid presence/);
});
