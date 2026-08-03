// Keep finding normalization aligned with PracticeDetectionResultParser.

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
    if (!Array.isArray(evidence?.citations) || evidence.citations.length === 0)
        throw new Error("evidence citations are required");
    const citations = evidence.citations.map((citation) => {
        const sourceKind = String(citation?.sourceKind ?? "").trim();
        const artifactPath = String(citation?.artifactPath ?? "").trim();
        const path = String(citation?.path ?? "").trim();
        const side = citation?.side == null ? null : String(citation.side).trim().toUpperCase();
        const startLine = Number(citation?.startLine);
        const endLine = citation?.endLine == null ? startLine : Number(citation.endLine);
        const quote = String(citation?.quote ?? "").trim();
        if (!sourceKind) throw new Error("evidence citation sourceKind is required");
        if (!artifactPath) throw new Error("evidence citation artifactPath is required");
        if (!path) throw new Error("evidence citation path is required");
        if (sourceKind === "scm.pull-request.diff" && !["OLD", "NEW"].includes(side))
            throw new Error("diff evidence citation side must be OLD or NEW");
        if (!Number.isInteger(startLine) || startLine <= 0)
            throw new Error("evidence citation startLine must be a positive integer");
        if (!Number.isInteger(endLine) || endLine < startLine)
            throw new Error("evidence citation endLine must be >= startLine");
        if (!quote) throw new Error("evidence citation quote is required");
        return { sourceKind, artifactPath, path, ...(side == null ? {} : { side }), startLine, endLine, quote };
    });
    const sourceKinds = [...new Set(citations.map((citation) => citation.sourceKind))].sort();
    const locations = citations.map(({ path, startLine, endLine }) => ({ path, startLine, endLine }));
    const snippets = citations.map((citation) => citation.quote);
    return { citations, sourceKinds, locations, snippets };
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

export function validateEvidenceSources(finding, allowedSources, availableSources, artifactSources = new Map()) {
    for (const citation of finding.evidence.citations) {
        const sourceKind = citation.sourceKind;
        if (!allowedSources.has(sourceKind)) {
            throw new Error(`practice '${finding.practiceSlug}' does not declare evidence source '${sourceKind}'`);
        }
        if (!availableSources.has(sourceKind)) {
            throw new Error(`evidence source '${sourceKind}' was not available to this invocation`);
        }
        if (artifactSources.get(citation.artifactPath) !== sourceKind) {
            throw new Error(`artifact '${citation.artifactPath}' does not belong to evidence source '${sourceKind}'`);
        }
    }
}

export function citationMatchesArtifact(citation, content) {
    if (citation.sourceKind !== "scm.pull-request.diff") return content.includes(citation.quote);
    let oldPath = null;
    let newPath = null;
    const citedLines = new Map();
    for (const storedLine of content.split("\n")) {
        const annotated = storedLine.match(/^\[L(\d+)] (.*)$/);
        const line = annotated ? annotated[2] : storedLine;
        if (line.startsWith("--- ")) {
            oldPath = diffPath(line.slice(4));
            continue;
        }
        if (line.startsWith("+++ ")) {
            newPath = diffPath(line.slice(4));
            continue;
        }
        if (annotated) {
            const lineNumber = Number(annotated[1]);
            const side = line.startsWith("-") ? "OLD" : "NEW";
            const path = side === "OLD" ? oldPath : newPath;
            if (side === citation.side && path === citation.path) citedLines.set(lineNumber, line);
        }
    }
    const quoteLines = citation.quote.split("\n");
    if (quoteLines.length !== citation.endLine - citation.startLine + 1) return false;
    return quoteLines.every((quoteLine, index) => {
        const diffLine = citedLines.get(citation.startLine + index);
        return diffLine === quoteLine || diffLine?.slice(1) === quoteLine;
    });
}

function diffPath(rawPath) {
    let value = rawPath.trim();
    if (value === "/dev/null") return null;
    if (value.startsWith('"') && value.endsWith('"')) value = value.slice(1, -1).replaceAll('\\"', '"');
    return value.startsWith("a/") || value.startsWith("b/") ? value.slice(2) : value;
}
