/**
 * Design-local types for the practice-surface exploration. These mirror the shapes the
 * live-tested backend already serves (status, trend, observation counts, latest evidence,
 * workspace health) but stay deliberately decoupled from the generated API client so the
 * three candidates can be judged as pure design artifacts.
 */

export type PracticeStatus = "STRENGTH" | "MIXED" | "DEVELOPING" | "NO_ACTIVITY";

export type PracticeTrend = "IMPROVING" | "WORSENING" | "STEADY" | "NEW";

export type ArtifactKind = "PULL_REQUEST" | "ISSUE";

export type ArtifactState = "OPEN" | "MERGED" | "CLOSED";

export type AreaAvailability = "AVAILABLE" | "SUPPRESSED" | "NO_DATA";

/** Polarity of a single observation as it appears attached to an artifact. */
export type ObservationTone = "POSITIVE" | "ATTENTION";

export type PracticeAreaId =
	| "constructive-code-review"
	| "testing"
	| "security"
	| "error-handling"
	| "documentation"
	| "issue-craft"
	| "commit-hygiene"
	| "pr-craft"
	| "collaboration"
	| "code-clarity"
	| "performance-awareness"
	| "dependency-care";

/** A concrete PR or issue that a practice signal is anchored to. */
export interface ArtifactRef {
	kind: ArtifactKind;
	number: number;
	title: string;
	/** owner/name, e.g. "nimbus/payments-api" */
	repo: string;
	url: string;
	state: ArtifactState;
}

/** The latest concrete evidence behind a practice signal. */
export interface Evidence {
	artifact: ArtifactRef;
	/** ISO timestamp of when the observation was made. */
	observedAt: string;
	/** One plain sentence explaining what was seen. */
	reasoning: string;
}

/** One developer's standing on one practice, criterion referenced, never a rank. */
export interface PracticeSignal {
	practiceSlug: string;
	practiceName: string;
	areaId: PracticeAreaId;
	status: PracticeStatus;
	trend: PracticeTrend;
	observationCount: number;
	/** Observations per week, oldest first. Powers sparklines. */
	history: readonly number[];
	latestEvidence?: Evidence;
	/** One concrete, blame-free next step. */
	guidance?: string;
}

export interface DeveloperPracticeProfile {
	login: string;
	name: string;
	avatarUrl?: string;
	/** Triage flag for mentors. Never a rank, only "could use support". */
	needsAttention: boolean;
	/** One plain sentence a mentor can scan, present only when needsAttention. */
	attentionSummary?: string;
	signals: readonly PracticeSignal[];
}

/** A practice observation as it attaches to an artifact row in the activity feed. */
export interface FeedObservation {
	practiceName: string;
	areaId: PracticeAreaId;
	tone: ObservationTone;
	reasoning: string;
	guidance?: string;
}

/** One artifact (PR or issue) in a developer's chronological activity feed. */
export interface ActivityItem {
	id: string;
	artifact: ArtifactRef;
	/** ISO timestamp of the artifact activity (opened, merged, ...). */
	happenedAt: string;
	observations: readonly FeedObservation[];
}

/** Workspace-level health for one practice area. Counts are people per status, never named. */
export interface AreaHealth {
	areaId: PracticeAreaId;
	availability: AreaAvailability;
	developing?: number;
	mixed?: number;
	strength?: number;
}

/** Rolled-up status of one area for one developer, derived from their practice signals. */
export interface AreaSummary {
	areaId: PracticeAreaId;
	status: PracticeStatus;
	trend: PracticeTrend;
	observationCount: number;
}

const STATUS_ROLLUP_PRIORITY: Record<PracticeStatus, number> = {
	DEVELOPING: 0,
	MIXED: 1,
	STRENGTH: 2,
	NO_ACTIVITY: 3,
};

/**
 * Rolls a developer's practice signals up to one cell per area. The most attention-worthy
 * status wins the area (a single developing practice makes the whole area developing), and
 * the trend is taken from the signal that decided the status.
 */
export function summarizeAreas(signals: readonly PracticeSignal[]): AreaSummary[] {
	const byArea = new Map<PracticeAreaId, PracticeSignal[]>();
	for (const signal of signals) {
		const bucket = byArea.get(signal.areaId) ?? [];
		bucket.push(signal);
		byArea.set(signal.areaId, bucket);
	}
	return [...byArea.entries()].map(([areaId, areaSignals]) => {
		const leading = [...areaSignals].sort(
			(a, b) => STATUS_ROLLUP_PRIORITY[a.status] - STATUS_ROLLUP_PRIORITY[b.status],
		)[0];
		return {
			areaId,
			status: leading.status,
			trend: leading.trend,
			observationCount: areaSignals.reduce((sum, s) => sum + s.observationCount, 0),
		};
	});
}
