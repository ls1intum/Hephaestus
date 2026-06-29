// Pure normalization helpers for practice-detection findings.
//
// Kept in a side-effect-free module (separate from pi-runner.mjs, whose top-level env guards,
// envelope reads, and main() make it un-importable) so the report_finding tool boundary can be
// unit-tested in isolation. These functions MUST stay in lock-step with the Java consumer
// PracticeDetectionResultParser: the slug is lower-cased with underscores mapped to hyphens, and
// presence/assessment/severity are upper-cased BEFORE validation, so the JS boundary accepts
// exactly what the parser accepts (no silent recall loss).

export function normalizeDiffNote(note) {
    if (!note || typeof note !== "object") throw new Error("diff note must be an object");
    const filePath = String(note.filePath ?? "").trim();
    const startLine = Number(note.startLine);
    const endLine = note.endLine == null ? startLine : Number(note.endLine);
    const body = String(note.body ?? "").trim();
    if (!filePath) throw new Error("diff note filePath is required");
    if (!Number.isInteger(startLine) || startLine <= 0)
        throw new Error("diff note startLine must be a positive integer");
    if (!Number.isInteger(endLine) || endLine < startLine) throw new Error("diff note endLine must be >= startLine");
    if (!body) throw new Error("diff note body is required");
    return { filePath, startLine, endLine, body };
}

export function normalizeEvidence(evidence) {
    const locations = Array.isArray(evidence?.locations)
        ? evidence.locations.map((location) => {
              const path = String(location?.path ?? "").trim();
              const startLine = Number(location?.startLine);
              const endLine = location?.endLine == null ? startLine : Number(location.endLine);
              if (!path) throw new Error("evidence location path is required");
              if (!Number.isInteger(startLine) || startLine <= 0)
                  throw new Error("evidence startLine must be a positive integer");
              if (!Number.isInteger(endLine) || endLine < startLine)
                  throw new Error("evidence endLine must be >= startLine");
              return { path, startLine, endLine };
          })
        : [];
    const snippets = Array.isArray(evidence?.snippets)
        ? evidence.snippets.map((snippet) => String(snippet ?? "")).filter((snippet) => snippet.trim().length > 0)
        : [];
    return { locations, snippets };
}

export function normalizeFinding(finding) {
    if (!finding || typeof finding !== "object") throw new Error("finding must be an object");
    // Normalize exactly as the Java consumer PracticeDetectionResultParser does before validating:
    // slug is lower-cased with underscores -> hyphens, and the three enums are upper-cased. Otherwise a
    // lowercase enum / underscored slug the parser would accept is rejected (or mis-deduped) at this
    // tool boundary, silently losing the finding.
    const practiceSlug = String(finding.practiceSlug ?? "")
        .trim()
        .toLowerCase()
        .replace(/_/g, "-");
    const title = String(finding.title ?? "").trim();
    const presence = String(finding.presence ?? "")
        .trim()
        .toUpperCase();
    // assessment has no valence when presence=NOT_APPLICABLE; the parser ignores/nulls it there.
    const isNa = presence === "NOT_APPLICABLE";
    const assessment = isNa
        ? null
        : String(finding.assessment ?? "")
              .trim()
              .toUpperCase();
    // severity is meaningful only for assessment=BAD; default INFO when absent (parser re-derives it).
    const severity = finding.severity == null ? "INFO" : String(finding.severity).trim().toUpperCase() || "INFO";
    // Salvage percentage-style confidence (value in (1,100] -> /100), mirroring the Java consumer
    // PracticeDetectionResultParser.parseConfidence; weak models commonly emit e.g. 85 for 0.85.
    const rawConfidence = Number(finding.confidence);
    const confidence = rawConfidence > 1 && rawConfidence <= 100 ? rawConfidence / 100 : rawConfidence;
    const reasoning = String(finding.reasoning ?? "").trim();
    const guidance = String(finding.guidance ?? "").trim();
    if (!practiceSlug) throw new Error("practiceSlug is required");
    if (!title) throw new Error("title is required");
    if (!["PRESENT", "ABSENT", "NOT_APPLICABLE"].includes(presence)) throw new Error(`invalid presence '${presence}'`);
    if (!isNa && !["GOOD", "BAD"].includes(assessment)) throw new Error(`invalid assessment '${assessment}'`);
    if (!["CRITICAL", "MAJOR", "MINOR", "INFO"].includes(severity)) throw new Error(`invalid severity '${severity}'`);
    if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1)
        throw new Error("confidence must be between 0 and 1");
    if (!reasoning) throw new Error("reasoning is required");
    if (!guidance) throw new Error("guidance is required");
    const evidence = normalizeEvidence(finding.evidence);
    const suggestedDiffNotes = Array.isArray(finding.suggestedDiffNotes)
        ? finding.suggestedDiffNotes.map(normalizeDiffNote)
        : [];
    const out = { practiceSlug, title, presence, severity, confidence, evidence, reasoning, guidance, suggestedDiffNotes };
    if (!isNa) out.assessment = assessment;
    return out;
}

export function dedupeKeyForFinding(finding) {
    // Dedupe key: practice + title + locations.
    const locs = finding.evidence.locations.map((l) => `${l.path}:${l.startLine}-${l.endLine}`).join(",");
    return `${finding.practiceSlug}|${finding.title}|${locs}`;
}
