// ── Vocabularies shared with Java ────────────────────────────────────────────
// Each list mirrors an enum on the server. They are hand-maintained on both sides, so
// PresenceVocabularyParityTest parses these literals and asserts equality with the Java enum's
// values(); it exists because presence drifted once and silently laundered INDETERMINATE into
// NOT_APPLICABLE. Add a value here and to the Java enum in the same change, or the test fails.
export const PRESENCE_VALUES = ["PRESENT", "ABSENT", "NOT_APPLICABLE", "INDETERMINATE"];
export const ASSESSMENT_VALUES = ["GOOD", "BAD"];
export const SEVERITY_VALUES = ["CRITICAL", "MAJOR", "MINOR", "INFO"];

/**
 * Whether an observation with this presence carries a good/bad direction — the exact twin of
 * Presence.carriesValence() in Java and of the DB CHECK chk_observation_presence_assessment.
 * Asked rather than open-coding `!== "NOT_APPLICABLE"`, which silently demands an assessment on
 * INDETERMINATE and rejects the honest answer.
 */
export function carriesValence(presence) {
    return presence === "PRESENT" || presence === "ABSENT";
}

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

/**
 * The recorded scope of a search that came up empty. An ABSENT observation is a universal claim —
 * "it is not there" — and the one thing a fragment of a corpus can never support. Narrating the
 * search in `reasoning` is the honour-system version of this; a structured block is what a validator
 * can actually hold against the domain the practice declared.
 */
export function normalizeSearch(search) {
    if (!search || typeof search !== "object") throw new Error("search is required");
    const consulted = Array.isArray(search.consulted)
        ? search.consulted.map((kind) => String(kind ?? "").trim()).filter(Boolean)
        : [];
    const lookedFor = String(search.lookedFor ?? "").trim();
    const boundary = String(search.boundary ?? "").trim();
    if (consulted.length === 0) throw new Error("search.consulted must name at least one source you searched");
    if (!lookedFor) throw new Error("search.lookedFor is required");
    if (!boundary) throw new Error("search.boundary is required");
    return { consulted: [...new Set(consulted)].sort(), lookedFor, boundary };
}

export function normalizeEvidence(evidence, presence) {
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
        if (sourceKind !== "scm.pull-request.diff" && side !== null)
            throw new Error("non-diff evidence citation must not specify side");
        if (!Number.isInteger(startLine) || startLine <= 0)
            throw new Error("evidence citation startLine must be a positive integer");
        if (!Number.isInteger(endLine) || endLine < startLine)
            throw new Error("evidence citation endLine must be >= startLine");
        if (!quote) throw new Error("evidence citation quote is required");
        return { sourceKind, artifactPath, path, ...(side == null ? {} : { side }), startLine, endLine, quote };
    });
    // Required for ABSENT and kept whenever it is offered: on a PRESENT observation the citations are
    // already the warrant, but a model that recorded where it looked has said something true and there
    // is no reason to discard it.
    if (presence === "ABSENT") {
        if (evidence.search == null) {
            throw new Error(
                "an ABSENT observation must record its search: evidence.search with consulted, lookedFor and boundary",
            );
        }
        return { citations, search: normalizeSearch(evidence.search) };
    }
    return evidence.search == null ? { citations } : { citations, search: normalizeSearch(evidence.search) };
}

export function normalizeFinding(finding) {
    if (!finding || typeof finding !== "object") throw new Error("finding must be an object");
    const practiceSlug = String(finding.practiceSlug ?? "")
        .trim()
        .toLowerCase()
        .replace(/_/g, "-");
    const title = String(finding.title ?? "").trim();
    const presence = String(finding.presence ?? "")
        .trim()
        .toUpperCase();
    // NOT_APPLICABLE and INDETERMINATE are both silence and carry no direction; the Java parser
    // nulls any assessment attached to either, so drop it here rather than demanding one.
    const hasValence = carriesValence(presence);
    const assessment = hasValence
        ? String(finding.assessment ?? "")
              .trim()
              .toUpperCase()
        : null;
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
    if (!PRESENCE_VALUES.includes(presence)) throw new Error(`invalid presence '${presence}'`);
    if (hasValence && !ASSESSMENT_VALUES.includes(assessment)) throw new Error(`invalid assessment '${assessment}'`);
    if (!SEVERITY_VALUES.includes(severity)) throw new Error(`invalid severity '${severity}'`);
    if (!Number.isFinite(confidence) || confidence < 0 || confidence > 1)
        throw new Error("confidence must be between 0 and 1");
    if (!reasoning) throw new Error("reasoning is required");
    if (!guidance) throw new Error("guidance is required");
    const evidence = normalizeEvidence(finding.evidence, presence);
    const suggestedDiffNotes = Array.isArray(finding.suggestedDiffNotes)
        ? finding.suggestedDiffNotes.map(normalizeDiffNote)
        : [];
    const out = {
        practiceSlug,
        title,
        presence,
        severity,
        confidence,
        evidence,
        reasoning,
        guidance,
        suggestedDiffNotes,
    };
    if (hasValence) out.assessment = assessment;
    return out;
}

export function dedupeKeyForFinding(finding) {
    const citations = finding.evidence.citations
        .map((citation) => `${citation.path}:${citation.startLine}-${citation.endLine}`)
        .join(",");
    return `${finding.practiceSlug}|${finding.title}|${citations}`;
}

/**
 * Holds every citation to bytes this run actually staged, under the source that produced them.
 *
 * <p>The run stages every source that applies to the artifact, so the question is no longer whether
 * the practice predicted it would read this one — it is whether the source was there and the quote
 * really came out of it. A practice whose subject turns out to live in a source its author did not
 * name is exactly the case full context exists to catch, and rejecting its citation would have thrown
 * away the finding for being observant.
 */
export function validateEvidenceSources(finding, availableSourceKinds, artifactSources = new Map()) {
    for (const citation of finding.evidence.citations) {
        const sourceKind = citation.sourceKind;
        if (!availableSourceKinds.has(sourceKind)) {
            throw new Error(`evidence source '${sourceKind}' was not available to this invocation`);
        }
        if (artifactSources.get(citation.artifactPath) !== sourceKind) {
            throw new Error(`artifact '${citation.artifactPath}' does not belong to evidence source '${sourceKind}'`);
        }
    }
}

/**
 * Holds a recorded search against the domain the practice declared it would search.
 *
 * <p>`EXHAUSTIVE` is the practice saying "this claim asserts something is NOT in this source", which
 * is the only stance under which absence is assertable at all. So the sources held that way ARE the
 * domain: an ABSENT observation that did not consult one of them is asserting a universal over a
 * corpus it never opened, and the honest answer is INDETERMINATE instead.
 *
 * <p>Consulting something this run never staged is the same error the citation check already rejects —
 * the bytes were not there, so they cannot have been searched.
 */
export function validateSearchScope(finding, exhaustiveSourceKinds, availableSourceKinds) {
    if (finding.presence !== "ABSENT") return;
    const search = finding.evidence.search;
    if (!search) throw new Error("an ABSENT observation must record its search");
    const consulted = new Set(search.consulted);
    for (const sourceKind of consulted) {
        if (!availableSourceKinds.has(sourceKind)) {
            throw new Error(`searched source '${sourceKind}' was not available to this invocation`);
        }
    }
    const unsearched = [...exhaustiveSourceKinds].filter((sourceKind) => !consulted.has(sourceKind)).sort();
    if (unsearched.length > 0) {
        throw new Error(
            `cannot conclude ABSENT for '${finding.practiceSlug}' without searching ${unsearched.join(", ")} — ` +
                `say INDETERMINATE instead, or record the search`,
        );
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
