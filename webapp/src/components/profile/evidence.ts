import type { ObservationDetail } from "@/api/types.gen";

/** One cited place in the reviewed work, with the lines that place contains. */
export interface EvidenceLocation {
	path: string;
	startLine: number;
	endLine: number;
	/** The quoted source. Absent exactly when {@link redacted} — a citation always names its lines. */
	snippet?: string;
	/**
	 * The citation named a place but its quote was withheld. Distinguishes a deliberate omission from
	 * a detector that cited nothing, so the surface can say which silence it is.
	 */
	redacted: boolean;
}

/**
 * The citations behind one observation, in the order the reviewer recorded them.
 *
 * <p>`evidence` arrives typed and pre-validated: the server rejects a citation whose lines are absent or
 * inverted, and enforces that a quote is present unless it was explicitly redacted (see
 * {@link file://../../../../server/src/main/java/de/tum/cit/aet/hephaestus/practices/observation/dto/EvidenceCitationDTO.java EvidenceCitationDTO}).
 * Nothing is narrowed or guessed here — this only reshapes citations into what the rendering needs.
 */
export function toEvidenceLocations(evidence: ObservationDetail["evidence"]): EvidenceLocation[] {
	return (evidence?.citations ?? []).map((citation) => ({
		path: citation.path,
		startLine: citation.startLine,
		endLine: citation.endLine,
		snippet: citation.quote,
		redacted: citation.quoteRedacted,
	}));
}

/** `"src/main/java/"` + `"Foo.java"` — split so the directory can absorb truncation on its own. */
export function splitPath(path: string): { directory: string; fileName: string } {
	const lastSlash = path.lastIndexOf("/");
	if (lastSlash < 0) return { directory: "", fileName: path };
	return { directory: path.slice(0, lastSlash + 1), fileName: path.slice(lastSlash + 1) };
}

/** `"62"` for a single line, `"62–70"` for a range. */
export function evidenceLineRangeLabel(location: EvidenceLocation): string {
	if (location.endLine !== location.startLine) {
		return `${location.startLine}–${location.endLine}`;
	}
	return `${location.startLine}`;
}
