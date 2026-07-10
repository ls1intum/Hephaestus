// Pure normalization helpers for practice-detection observations.
//
// Kept in a side-effect-free module (separate from pi-runner.mjs, whose top-level env guards,
// envelope reads, and main() make it un-importable) so the report_observation tool boundary can be
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

export function normalizeObservation(observation) {
    if (!observation || typeof observation !== "object") throw new Error("observation must be an object");
    // Normalize exactly as the Java consumer PracticeDetectionResultParser does before validating:
    // slug is lower-cased with underscores -> hyphens, and the three enums are upper-cased. Otherwise a
    // lowercase enum / underscored slug the parser would accept is rejected (or mis-deduped) at this
    // tool boundary, silently losing the observation.
    const practiceSlug = String(observation.practiceSlug ?? "")
        .trim()
        .toLowerCase()
        .replace(/_/g, "-");
    const title = String(observation.title ?? "").trim();
    const presence = String(observation.presence ?? "")
        .trim()
        .toUpperCase();
    // assessment has no valence when presence=NOT_APPLICABLE; the parser ignores/nulls it there.
    const isNa = presence === "NOT_APPLICABLE";
    const assessment = isNa
        ? null
        : String(observation.assessment ?? "")
              .trim()
              .toUpperCase();
    // severity is meaningful only for assessment=BAD; default INFO when absent (parser re-derives it).
    const severity =
        observation.severity == null ? "INFO" : String(observation.severity).trim().toUpperCase() || "INFO";
    // Salvage percentage-style confidence (value in (1,100] -> /100), mirroring the Java consumer
    // PracticeDetectionResultParser.parseConfidence; weak models commonly emit e.g. 85 for 0.85.
    const rawConfidence = Number(observation.confidence);
    const confidence = rawConfidence > 1 && rawConfidence <= 100 ? rawConfidence / 100 : rawConfidence;
    const reasoning = String(observation.reasoning ?? "").trim();
    const guidance = String(observation.guidance ?? "").trim();
    if (!practiceSlug) throw new Error("practiceSlug is required");
    if (!title) throw new Error("title is required");
    if (!["PRESENT", "ABSENT", "NOT_APPLICABLE"].includes(presence)) throw new Error(`invalid presence '${presence}'`);
    if (!isNa && !["GOOD", "BAD"].includes(assessment)) throw new Error(`invalid assessment '${assessment}'`);
    if (!["CRITICAL", "MAJOR", "MINOR", "INFO"].includes(severity)) throw new Error(`invalid severity '${severity}'`);
    if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1)
        throw new Error("confidence must be between 0 and 1");
    if (!reasoning) throw new Error("reasoning is required");
    if (!guidance) throw new Error("guidance is required");
    const evidence = normalizeEvidence(observation.evidence);
    const suggestedDiffNotes = Array.isArray(observation.suggestedDiffNotes)
        ? observation.suggestedDiffNotes.map(normalizeDiffNote)
        : [];
    const out = { practiceSlug, title, presence, severity, confidence, evidence, reasoning, guidance, suggestedDiffNotes };
    if (!isNa) out.assessment = assessment;
    // Optional subjectLogin: for a reviewer-audience practice the model names WHICH reviewer this observation is
    // about. The server VALIDATES it against the real reviewer set (it is never trusted for identity beyond a
    // login lookup); pass it through trimmed when present, omit it otherwise so author-audience observations stay clean.
    const subjectLogin = observation.subjectLogin == null ? "" : String(observation.subjectLogin).trim();
    if (subjectLogin) out.subjectLogin = subjectLogin;
    return out;
}

export function dedupeKeyForObservation(observation) {
    // Dedupe key: practice + title + locations.
    const locs = observation.evidence.locations.map((l) => `${l.path}:${l.startLine}-${l.endLine}`).join(",");
    return `${observation.practiceSlug}|${observation.title}|${locs}`;
}
