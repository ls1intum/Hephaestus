import type {
	AreaHealth,
	AreaStatusCell,
	PracticeReportCard,
	PracticeReportItem,
	PracticeReportSummary,
} from "@/api/types.gen";

/**
 * Narrow aliases over the generated API types so every practice-surface component speaks the
 * same vocabulary without re-declaring unions the server already owns.
 */

/** Where someone stands on a practice or area, criterion referenced, never a rank. */
export type PracticeStatus = AreaStatusCell["status"];

/** Direction versus the prior review cycle, criterion referenced, never a peer comparison. */
export type PracticeTrend = AreaStatusCell["trend"];

/** The kind of work an observation is about. */
export type ArtifactKind = PracticeReportItem["artifactType"];

/** State of the PR or issue behind an observation. */
export type ArtifactState = NonNullable<PracticeReportItem["artifactState"]>;

export type {
	AreaHealth,
	AreaStatusCell,
	PracticeReportCard,
	PracticeReportItem,
	PracticeReportSummary,
};
