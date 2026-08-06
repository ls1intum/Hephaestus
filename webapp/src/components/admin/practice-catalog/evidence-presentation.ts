import type {
	PracticeAutomatedReviewPolicy,
	PracticeEvidenceOutcome,
	PracticeEvidenceSourceOption,
} from "@/api/types.gen";

/** Every reason a review can record for skipping a practice, closed by the generated schema. */
type PracticeEvidenceReason = PracticeEvidenceOutcome["blockersObserved"][number]["reasonCode"];

export function evidenceSourceLabel(
	sourceKind: string,
	sources: readonly PracticeEvidenceSourceOption[],
) {
	return (
		sources.find((source) => source.sourceKind === sourceKind)?.displayName ?? "Unknown source"
	);
}

export function evidenceQualityLabel(
	requirement: PracticeAutomatedReviewPolicy["requiredEvidence"][number],
) {
	if (requirement.completeness === "COMPLETE" && requirement.freshness === "CURRENT") {
		return "Complete and current";
	}
	if (requirement.completeness === "COMPLETE") {
		return "Complete; no freshness requirement";
	}
	if (requirement.freshness === "CURRENT") {
		return "Current; partial or unknown completeness allowed";
	}
	return "Available; no completeness or freshness requirement";
}

export function canAttemptAutomatedReview(
	requirements: PracticeAutomatedReviewPolicy,
	supportedModes: readonly PracticeAutomatedReviewPolicy["automatedReview"]["mode"][],
) {
	return (
		supportedModes.includes(requirements.automatedReview.mode) &&
		requirements.automatedReview.evidenceSufficiency === "SUFFICIENT_WHEN_REQUIREMENTS_MET"
	);
}

export function automatedReviewUnavailableLabel(
	requirements: PracticeAutomatedReviewPolicy,
	supportedModes: readonly PracticeAutomatedReviewPolicy["automatedReview"]["mode"][],
) {
	if (requirements.automatedReview.mode === "NONE") return "Guidance only";
	if (requirements.automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT") {
		return "Human review needed";
	}
	if (!supportedModes.includes(requirements.automatedReview.mode)) {
		return "AI support unavailable";
	}
	return null;
}

/**
 * The label a list row needs, or null when the practice behaves the way every other one does.
 * AI-supported mentoring is the norm, so badging it says nothing and hides the two answers that do.
 */
export function automatedReviewLimitationLabel(
	automatedReview: PracticeAutomatedReviewPolicy["automatedReview"],
) {
	if (automatedReview.mode === "NONE") return "Guidance only";
	if (automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT") {
		return "Human review needed";
	}
	return null;
}

/** The full label for a detail view, where the reader is asking what this practice does. */
export function mentoringSupportLabel(
	automatedReview: PracticeAutomatedReviewPolicy["automatedReview"],
) {
	return automatedReviewLimitationLabel(automatedReview) ?? "AI-supported mentoring";
}

/**
 * A readiness reason, read back as the thing an author would change.
 *
 * Keyed on the generated union, so a reason added server-side fails the build here rather than
 * reaching an admin as a raw constant name.
 */
export function readinessReasonLabel(reasonCode: PracticeEvidenceReason): string {
	return READINESS_REASON_LABELS[reasonCode];
}

const READINESS_REASON_LABELS: Record<PracticeEvidenceReason, string> = {
	SOURCE_NOT_AVAILABLE: "was not available",
	SOURCE_INCOMPLETE: "was not fully captured",
	SOURCE_EMPTY: "was empty",
	NO_AUTOMATED_REVIEW: "this practice is not set up for automated review",
	DECLARED_EVIDENCE_INSUFFICIENT: "this practice declares its evidence insufficient",
};
