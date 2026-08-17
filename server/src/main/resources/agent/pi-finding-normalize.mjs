// ── Vocabularies shared with Java ────────────────────────────────────────────
// Each list mirrors an enum on the server. They are hand-maintained on both sides, so
// PresenceVocabularyParityTest parses these literals and asserts equality with the Java enum's
// values(); it exists because presence drifted once and silently laundered INCONCLUSIVE into
// NOT_APPLICABLE. Add a value here and to the Java enum in the same change, or the test fails.
export const PRESENCE_VALUES = ["PRESENT", "ABSENT", "NOT_APPLICABLE", "INCONCLUSIVE"];
export const ASSESSMENT_VALUES = ["GOOD", "BAD"];
export const SEVERITY_VALUES = ["CRITICAL", "MAJOR", "MINOR", "INFO"];

// ── What each value MEANS, at the moment of choosing between two of them ─────
//
// A bare enum hands the model four words and no way to tell them apart, and it will then reach for
// whichever one reads as the safe default — which for a four-valued presence is always the one that
// looks like N/A on a form. So each value is described by its DISCRIMINATOR against its nearest
// neighbour rather than by a definition that stands alone: the choice is only ever made in a pair.
//
// These are the text the tool schema carries (pi-runner.mjs builds `description` from them), so they
// are the last thing the model reads before it fills the field in.

/**
 * Presence answers ONE question for every practice in the catalogue: is the behaviour this practice
 * names in the work? That framing is what makes the four values decidable without knowing which
 * practice is being scored — a missing good behaviour is an ABSENT good behaviour, not a PRESENT bad
 * one, whatever the practice happens to be about.
 */
export const PRESENCE_DESCRIPTIONS = {
    PRESENT:
        "The behaviour this practice names is in the work and you can point at it — your citation shows the " +
        "thing itself. Pick this over ABSENT when the behaviour occurred, and over INCONCLUSIVE when the " +
        "evidence settles the question.",
    ABSENT:
        "The occasion for the behaviour arose in this work and the behaviour is not there. Pick this over " +
        "NOT_APPLICABLE when there WAS a place the behaviour belonged — NOT_APPLICABLE says no such place " +
        "existed. A missing good behaviour is ABSENT (a gap), never PRESENT with a BAD assessment. It is " +
        "equally the value for a harmful behaviour that could have appeared and did not, which is a strength " +
        "(assessment=GOOD) and not a gap — but only where the practice bounds the corpus it searches, because " +
        "a clean surface is provable only over a corpus you covered whole. Requires evidence.search: an " +
        "absence is a claim about a corpus, and it holds only over the corpus you searched.",
    NOT_APPLICABLE:
        "This work has no subject for this practice: the occasion for the behaviour never arose, so there was " +
        "nothing here to judge. Pick this over INCONCLUSIVE only when you can name the fact about THIS work " +
        "that rules the subject out (evidence.inapplicability.ruledOutBy); if the honest answer to 'why does " +
        "it not apply' is 'I could not tell', the presence is INCONCLUSIVE. This value enters the developer's " +
        "long-running record as 'there was nothing to see here', so it is an assertion about their work and " +
        "never a way to abstain.",
    INCONCLUSIVE:
        "There IS a subject here, you read the evidence this practice needs, and it does not settle the " +
        "question either way. Pick this over NOT_APPLICABLE whenever the practice's subject does occur in the " +
        "work but the evidence is not dispositive, and over a speculative ABSENT whenever you could not search " +
        "far enough to say the behaviour is really missing. It carries no assessment. It does require " +
        "evidence.undecidability — the question left open, and the evidence that would have settled it — " +
        "because uncertainty is a measurement only when it says what it could not measure.",
};

/** Valence, and only valence: whether what presence recorded reflects well or badly on the work. */
export const ASSESSMENT_DESCRIPTIONS = {
    GOOD:
        "What you saw reflects well and is worth acknowledging. With PRESENT: the good behaviour the practice " +
        "names is there. With ABSENT: a harmful behaviour that could have appeared did not.",
    BAD:
        "What you saw is a problem the developer should act on. With PRESENT: a harmful behaviour is in the " +
        "work (commission). With ABSENT: a good behaviour that belonged here is missing (omission).",
};

/**
 * Severity is read off the practice's own severity table, keyed to the fact that was quoted — these
 * descriptions calibrate the bands so the same fact lands in the same band every run. They are not an
 * invitation to grade by feel.
 */
export const SEVERITY_DESCRIPTIONS = {
    CRITICAL:
        "The consequence is expensive or impossible to undo once this merges — a leaked credential, data loss, " +
        "a security hole. Differs from MAJOR by whether the damage can still be taken back.",
    MAJOR:
        "A real defect to fix before merging, whose consequence is contained and correctable. Differs from " +
        "MINOR by whether a reader of this change would be wrong about how it behaves.",
    MINOR:
        "A craft-level improvement worth making that nobody would block a merge on. Differs from INFO by " +
        "whether there is a specific edit to make.",
    INFO: "An observation with no required edit. This is the band for anything that is not a defect.",
};

/**
 * Renders a vocabulary as the `description` of its enum field, one line per value.
 *
 * <p>Throws on a value with no description, so a value added to the vocabulary without being described
 * fails here rather than reaching the model as an undifferentiated word — the same structural guard, one
 * level down, that AgentVocabularySyncTest applies across the language boundary.
 */
export function describeVocabulary(values, descriptions) {
    return values
        .map((value) => {
            const description = descriptions[value];
            if (!description) throw new Error(`vocabulary value '${value}' has no description`);
            return `${value} — ${description}`;
        })
        .join("\n");
}

/**
 * Whether an observation with this presence carries a good/bad direction — the exact twin of
 * Presence.carriesValence() in Java and of the DB CHECK chk_observation_presence_assessment.
 * Asked rather than open-coding `!== "NOT_APPLICABLE"`, which silently demands an assessment on
 * INCONCLUSIVE and rejects the honest answer.
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

/**
 * The positive claim behind a NOT_APPLICABLE observation: what this practice looks for, and the fact about
 * this work that means it cannot be here.
 *
 * NOT_APPLICABLE is the one presence that costs nothing to say. A PRESENT observation is warranted by the
 * citation that shows the thing; an ABSENT one has to record the search that came up empty. NOT_APPLICABLE
 * needs a citation too, but any citation will do — quoting a line proves nothing about a practice having no
 * subject — so it is the cheapest answer available and it is where uncertainty drains to. Every form on
 * earth uses N/A for "no answer", and a model reaches for it the same way.
 *
 * That matters because NOT_APPLICABLE is not a shrug: it is a claim about the developer's work, entering a
 * long-running record of how a person works as "there was nothing here to see". The honest answer when you
 * cannot tell is INCONCLUSIVE. Naming the ground makes the difference visible to the model while it is
 * choosing, which is the only moment the choice can be made well.
 */
export function normalizeInapplicability(inapplicability) {
    if (!inapplicability || typeof inapplicability !== "object")
        throw new Error("inapplicability is required");
    const consulted = Array.isArray(inapplicability.consulted)
        ? inapplicability.consulted.map((kind) => String(kind ?? "").trim()).filter(Boolean)
        : [];
    const subject = String(inapplicability.subject ?? "").trim();
    const ruledOutBy = String(inapplicability.ruledOutBy ?? "").trim();
    if (consulted.length === 0)
        throw new Error("inapplicability.consulted must name at least one source you read to conclude this");
    if (!subject) throw new Error("inapplicability.subject is required: name what this practice looks for");
    if (!ruledOutBy)
        throw new Error(
            "inapplicability.ruledOutBy is required: state the fact about THIS work that means the subject " +
                "cannot occur in it. If you are merely unsure, the answer is INCONCLUSIVE, not NOT_APPLICABLE",
        );
    return { consulted: [...new Set(consulted)].sort(), subject, ruledOutBy };
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
    // The same shape one presence over: a claim that the practice has no subject here must say what the
    // subject is and what rules it out, or it is an abstention wearing a measurement's clothes.
    if (presence === "NOT_APPLICABLE") {
        if (evidence.inapplicability == null) {
            throw new Error(
                "a NOT_APPLICABLE observation must say why the practice does not apply: " +
                    "evidence.inapplicability with consulted, subject and ruledOutBy. If you looked and could " +
                    "not tell, say INCONCLUSIVE instead",
            );
        }
        return { citations, inapplicability: normalizeInapplicability(evidence.inapplicability) };
    }
    // And the same shape once more, for the last presence that had no ground at all. "I could not tell" is
    // only a measurement if it says what it could not tell and what would have told it; otherwise it is the
    // cheapest thing to write, which is how it becomes the next place uncertainty drains to.
    if (presence === "INCONCLUSIVE") {
        if (evidence.undecidability == null) {
            throw new Error(
                "an INCONCLUSIVE observation must say what it could not settle: evidence.undecidability with " +
                    "openQuestion and wouldSettleIt",
            );
        }
        return { citations, undecidability: normalizeUndecidability(evidence.undecidability) };
    }
    return evidence.search == null ? { citations } : { citations, search: normalizeSearch(evidence.search) };
}

/**
 * The recorded shape of a question the evidence left open. Sibling of {@link normalizeSearch} and
 * {@link normalizeInapplicability}: each presence that makes a claim beyond its citations has to ground it.
 */
export function normalizeUndecidability(undecidability) {
    if (!undecidability || typeof undecidability !== "object") throw new Error("undecidability is required");
    const openQuestion = String(undecidability.openQuestion ?? "").trim();
    const wouldSettleIt = String(undecidability.wouldSettleIt ?? "").trim();
    if (!openQuestion) throw new Error("undecidability.openQuestion is required");
    if (!wouldSettleIt) throw new Error("undecidability.wouldSettleIt is required");
    return { openQuestion, wouldSettleIt };
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
    // NOT_APPLICABLE and INCONCLUSIVE are both silence and carry no direction; the Java parser
    // nulls any assessment attached to either, so drop it here rather than demanding one.
    const hasValence = carriesValence(presence);
    const assessment = hasValence
        ? String(finding.assessment ?? "")
              .trim()
              .toUpperCase()
        : null;
    // severity is meaningful only for assessment=BAD; default INFO when absent (parser re-derives it).
    const severity = finding.severity == null ? "INFO" : String(finding.severity).trim().toUpperCase() || "INFO";
    const reasoning = String(finding.reasoning ?? "").trim();
    // OPTIONAL, and deliberately so. Since composition authors the developer-facing text, guidance
    // survives only as the fallback body of an in-context note (DeliveryComposer.appendBody), which is
    // null-safe. Demanding one on every finding made the measurement step invent a next step for
    // strengths, for NOT_APPLICABLE and for INCONCLUSIVE — a standing pull toward "something is wrong"
    // on exactly the three answers that assert nothing is. Blank collapses to absent so the Java side
    // sees one shape, not two.
    const guidance = String(finding.guidance ?? "").trim();
    if (!practiceSlug) throw new Error("practiceSlug is required");
    if (!title) throw new Error("title is required");
    if (!PRESENCE_VALUES.includes(presence)) throw new Error(`invalid presence '${presence}'`);
    if (hasValence && !ASSESSMENT_VALUES.includes(assessment)) throw new Error(`invalid assessment '${assessment}'`);
    if (!SEVERITY_VALUES.includes(severity)) throw new Error(`invalid severity '${severity}'`);
    if (!reasoning) throw new Error("reasoning is required");
    const evidence = normalizeEvidence(finding.evidence, presence);
    const suggestedDiffNotes = Array.isArray(finding.suggestedDiffNotes)
        ? finding.suggestedDiffNotes.map(normalizeDiffNote)
        : [];
    const out = {
        practiceSlug,
        title,
        presence,
        severity,
        evidence,
        reasoning,
        suggestedDiffNotes,
    };
    if (guidance) out.guidance = guidance;
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
 * corpus it never opened, and the honest answer is INCONCLUSIVE instead.
 *
 * <p>The two directions of an absence do not need the same proof, and the difference is what lets a
 * clean surface be recorded as a strength at all. An ABSENT/BAD says a good behaviour is missing from
 * the place the citation points at — the claim is anchored to that locus, so the search only has to
 * reach as far as the locus does. An ABSENT/GOOD says a harmful behaviour is nowhere in the work, which
 * ranges over the WHOLE corpus and is provable only if that corpus is closed and was covered whole.
 * A practice that has not declared an exhaustive stance has not closed a corpus, so it cannot make that
 * claim, and INCONCLUSIVE is the honest answer; one that has, can. This is what the eight defect
 * detectors used to buy by refusing GOOD outright and paying for it in false NOT_APPLICABLEs.
 *
 * <p>Consulting something this run never staged is the same error the citation check already rejects —
 * the bytes were not there, so they cannot have been searched.
 */
export function validateSearchScope(finding, exhaustiveSourceKinds, availableSourceKinds) {
    if (finding.presence !== "ABSENT") return;
    const search = finding.evidence.search;
    if (!search) throw new Error("an ABSENT observation must record its search");
    if (finding.assessment === "GOOD" && exhaustiveSourceKinds.size === 0) {
        throw new Error(
            `cannot conclude ABSENT + GOOD for '${finding.practiceSlug}': it declares no source it searches ` +
                `exhaustively, so "this is not anywhere in the work" ranges over a corpus it has not bounded — ` +
                `say INCONCLUSIVE instead`,
        );
    }
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
                `say INCONCLUSIVE instead, or record the search`,
        );
    }
}

/**
 * Holds a NOT_APPLICABLE claim to sources this run actually staged.
 *
 * The same boundary the citations and the recorded search answer to: bytes that were never there cannot
 * have been read, so claiming to have read them is the inapplicability-shaped version of citing evidence
 * we never had.
 */
export function validateInapplicabilityScope(finding, availableSourceKinds) {
    if (finding.presence !== "NOT_APPLICABLE") return;
    const inapplicability = finding.evidence.inapplicability;
    if (!inapplicability) throw new Error("a NOT_APPLICABLE observation must say why the practice does not apply");
    for (const sourceKind of inapplicability.consulted) {
        if (!availableSourceKinds.has(sourceKind)) {
            throw new Error(`consulted source '${sourceKind}' was not available to this invocation`);
        }
    }
}

/**
 * Typographic substitutions a model makes while transcribing, folded before comparison.
 *
 * Measured, not guessed: asked to quote the title `Resolve "Connect data between screens"` verbatim,
 * gpt-oss-120b returned it with curly quotes in 6 of 6 runs across three different tool schemas. The
 * text was faithful; only the glyphs moved. Without this fold the citation fails `includes`, the
 * finding throws, and a correct observation is lost — so the check was rejecting transcription rather
 * than fabrication, which is the opposite of its job.
 *
 * Deliberately narrow. Only characters with an unambiguous ASCII original are folded, so a quote that
 * says something the artifact does not still fails: this cannot turn a wrong quote into a right one.
 */
const CONFUSABLES = new Map(
    Object.entries({
        "‘": "'", "’": "'", "‚": "'", "‛": "'",
        "“": '"', "”": '"', "„": '"', "‟": '"',
        "‐": "-", "‑": "-", "‒": "-", "–": "-", "—": "-", "―": "-",
        " ": " ", " ": " ", " ": " ", " ": " ",
    })
);

/** Fold the substitutions above; everything else is compared as written. */
function foldConfusables(text) {
    let out = "";
    for (const ch of text) out += CONFUSABLES.get(ch) ?? ch;
    return out;
}

export function citationMatchesArtifact(citation, content) {
    if (citation.sourceKind !== "scm.pull-request.diff") {
        return content.includes(citation.quote) || foldConfusables(content).includes(foldConfusables(citation.quote));
    }
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
