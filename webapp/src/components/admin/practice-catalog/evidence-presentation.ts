import type { PracticeAutomatedReviewPolicy, PracticeEvidenceSourceOption } from "@/api/types.gen";

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

export function mentoringSupportLabel(
	automatedReview: PracticeAutomatedReviewPolicy["automatedReview"],
) {
	if (automatedReview.mode === "NONE") return "Guidance only";
	if (automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT") {
		return "Human review needed";
	}
	return "AI-supported mentoring";
}
