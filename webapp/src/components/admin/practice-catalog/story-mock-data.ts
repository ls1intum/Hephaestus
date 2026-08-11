import type { PracticeEvidenceOutcome } from "@/api/types.gen";

/**
 * Fixtures for the evidence surfaces, declared the way an author reads them rather than the way the
 * wire carries them.
 *
 * The API reports `consideredReviews` and `reviewedCount`, and every sentence on screen is about the
 * difference between the two. A hand-built outcome can therefore say "12 considered, 20 reviewed"
 * and look plausible while describing a state the server cannot produce. A story here declares how
 * many reviews were *skipped*; the count that reached the practice is derived, and an impossible
 * fixture throws instead of rendering.
 */
export type EvidenceBlocker = PracticeEvidenceOutcome["blockersObserved"][number];

export interface OutcomeSpec {
	practiceSlug: string;
	/** Reviews that got as far as asking this practice's requirements. */
	considered: number;
	/** How many of those the requirements turned away. Defaults to none. */
	skipped?: number;
	/**
	 * Counted per source, so one skipped review can appear in several. They therefore need not sum to
	 * `skipped`, and the copy above them never claims they do.
	 */
	blockers?: EvidenceBlocker[];
}

export function outcome({
	practiceSlug,
	considered,
	skipped = 0,
	blockers = [],
}: OutcomeSpec): PracticeEvidenceOutcome {
	if (skipped > considered) {
		throw new Error(
			`${practiceSlug}: ${skipped} skipped out of ${considered} considered is not a state the server can report.`,
		);
	}
	return {
		practiceSlug,
		consideredReviews: considered,
		reviewedCount: considered - skipped,
		blockersObserved: blockers,
	};
}
