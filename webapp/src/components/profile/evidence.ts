import type { EvidenceCitation, ResultEvidence } from "@/api/types.gen";
import { evidenceSourceDef } from "@/components/practice-vocabulary/evidence-source-defs";
import { andList, hasText } from "@/lib/text";

export interface EvidenceLocation {
	/** Absent on a historical citation that recorded no file. */
	path?: string;
	/** Absent on a historical citation that recorded no lines. */
	startLine?: number;
	endLine?: number;
	sourceKind: string;
	side?: "OLD" | "NEW";
	/** The commit a quote from the repository's history was verified against. */
	commit?: string;
	/** Absent where the citation recorded only its place. */
	snippet?: string;
	/**
	 * One changed passage, folded from the diff's two citations of it: what the lines read before
	 * the change and what they read after.
	 */
	change?: { before: string; after: string };
}
/**
 * Citations as the blocks the profile draws: a result's own, one problem's, or an example's. Each
 * list folds on its own, so a quote never joins a block of another problem.
 */
export function toEvidenceLocations(citations: EvidenceCitation[]): EvidenceLocation[] {
	return foldObjectSources(foldDiffSides(citations));
}

/** One block as the fold sees it: the citation it is built from, and the change it grew into. */
interface DiffBlock {
	citation: EvidenceCitation;
	change?: { before: string; after: string };
}

/**
 * Two sides of one change: the same passage of the same file, quoted from either side of the diff.
 * The wire carries no hunk identity, so every other fact of a citation has to agree before two
 * blocks may become one — the source they were read from, the source kind, the file, and the
 * lines. The commit is deliberately not one of them: on a diff citation it names the commit of
 * the side it was read from, so the two sides of one change carry two different ones.
 */
const pairsWith = (kept: DiffBlock, next: EvidenceCitation) =>
	kept.change === undefined &&
	kept.citation.side !== null &&
	next.side !== null &&
	kept.citation.side !== next.side &&
	kept.citation.sourceKind === next.sourceKind &&
	kept.citation.sourcePath === next.sourcePath &&
	kept.citation.path === next.path &&
	kept.citation.startLine === next.startLine &&
	kept.citation.endLine === next.endLine;

/** Before and after of one change, or nothing at all when either side recorded no quote. */
function changeBetween(
	kept: EvidenceCitation,
	next: EvidenceCitation,
): { before: string; after: string } | undefined {
	const [before, after] = kept.side === "OLD" ? [kept.quote, next.quote] : [next.quote, kept.quote];
	if (before === null || after === null) {
		return undefined;
	}
	return { before, after };
}

/**
 * The OLD and the NEW quote of one passage folded into one location. A changed line is cited from
 * both sides of the diff, and two blocks over one change read as two pieces of evidence; the pair
 * becomes the one block that says what the lines were and what they became. A side that recorded no
 * quote never folds: its block shows a place its counterpart does not quote.
 */
function foldDiffSides(citations: EvidenceCitation[]): EvidenceLocation[] {
	const blocks: DiffBlock[] = [];
	for (const citation of citations) {
		const index = blocks.findIndex((kept) => pairsWith(kept, citation));
		const kept = blocks[index];
		const change = kept === undefined ? undefined : changeBetween(kept.citation, citation);
		if (kept === undefined || change === undefined) {
			blocks.push({ citation });
			continue;
		}
		blocks[index] = { citation: kept.citation, change };
	}
	return blocks.map(toLocation);
}

/**
 * One citation as the profile reads it. A folded block keeps neither the side, the commit nor the
 * quote of the citation it grew from, and is captioned with no line range: each side was read at
 * its own commit, so any one of them would be true of one side only, and the block shows both.
 */
function toLocation({ citation, change }: DiffBlock): EvidenceLocation {
	if (change !== undefined) {
		return {
			path: citation.path ?? undefined,
			startLine: citation.startLine ?? undefined,
			endLine: citation.endLine ?? undefined,
			sourceKind: citation.sourceKind,
			change,
		};
	}
	return {
		path: citation.path ?? undefined,
		startLine: citation.startLine ?? undefined,
		endLine: citation.endLine ?? undefined,
		sourceKind: citation.sourceKind,
		side: citation.side ?? undefined,
		commit: citation.revision ?? undefined,
		snippet: citation.quote ?? undefined,
	};
}

/**
 * Two quotes of the same object source: not a place the reader could open, so the same registry
 * label over each of them reads as two pieces of evidence instead of one thing quoted twice. Only
 * quotes fold: a citation that recorded no quote keeps its own block.
 */
const quotesTheSameSourceAs = (kept: EvidenceLocation, next: EvidenceLocation) =>
	kept.sourceKind === next.sourceKind &&
	evidenceSourceDef(kept.sourceKind).locator === "object" &&
	kept.snippet !== undefined &&
	next.snippet !== undefined;

/**
 * Every quote of one object source folded into the block that first named it, each on its own
 * line. The block keeps the position of that first citation, so the order the reviewer recorded
 * still decides which source is read first.
 */
function foldObjectSources(locations: EvidenceLocation[]): EvidenceLocation[] {
	const folded: EvidenceLocation[] = [];
	for (const location of locations) {
		const index = folded.findIndex((kept) => quotesTheSameSourceAs(kept, location));
		const kept = folded[index];
		if (kept?.snippet === undefined || location.snippet === undefined) {
			folded.push(location);
			continue;
		}
		folded[index] = { ...kept, snippet: `${kept.snippet}\n${location.snippet}` };
	}
	return folded;
}

/** One pair of the "What was checked" list: the muted term, and the reviewer's own words for it. */
export interface EvidenceCheck {
	term: string;
	detail: string;
}

/**
 * What the review checked, in the warrant it wrote: what it looked for and how far it reached before
 * turning up nothing (a search), or why this practice had nothing to judge here (an inapplicability).
 * A review writes at most one of the two about one result, so the first one present is the whole
 * list. What it could not settle is not a check: the row lists those as open questions.
 *
 * Nothing here is inferred: a pair exists only where the reviewer wrote the sentence behind it,
 * and the sources it consulted are named in the registry's words rather than by their wire kind.
 */
export function toEvidenceCheck(evidence: ResultEvidence | null): EvidenceCheck[] {
	const search = evidence?.search;
	if (search) {
		return [
			...writtenCheck("Looked for", search.lookedFor),
			...consultedCheck(search.consulted),
			...writtenCheck("How far it reached", search.boundary),
		];
	}
	const inapplicability = evidence?.inapplicability;
	if (inapplicability) {
		return [
			...writtenCheck("Applies when", inapplicability.prerequisite),
			...consultedCheck(inapplicability.consulted),
			...writtenCheck("Nothing to judge because", inapplicability.ruledOutBy),
		];
	}
	return [];
}

/** One sentence the reviewer wrote under its term; one it did not write is no pair at all. */
function writtenCheck(term: string, detail: string | undefined): EvidenceCheck[] {
	return hasText(detail) ? [{ term, detail }] : [];
}

/** The sources a warrant names, in the registry's words; nothing consulted is no pair at all. */
function consultedCheck(consulted: string[] | undefined): EvidenceCheck[] {
	if (consulted === undefined || consulted.length === 0) {
		return [];
	}
	return [{ term: "Read", detail: andList.format(consulted.map(sourceLabel)) }];
}

const sourceLabel = (sourceKind: string) => evidenceSourceDef(sourceKind).label;

export function splitPath(path: string): { directory: string; fileName: string } {
	const lastSlash = path.lastIndexOf("/");
	if (lastSlash === -1) {
		return { directory: "", fileName: path };
	}
	return { directory: path.slice(0, lastSlash + 1), fileName: path.slice(lastSlash + 1) };
}
/** "line 4" or "lines 41 to 46"; nothing for a citation that recorded no lines. */
export function evidenceLineRangeLabel({
	startLine,
	endLine,
}: EvidenceLocation): string | undefined {
	if (startLine === undefined) {
		return undefined;
	}
	if (endLine !== undefined && endLine !== startLine) {
		return `lines ${startLine} to ${endLine}`;
	}
	return `line ${startLine}`;
}
