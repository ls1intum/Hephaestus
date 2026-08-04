import type { PracticeAutomatedReviewPolicy, PracticeEvidenceSourceOption } from "@/api/types.gen";

const REVIEW_MODE_LABELS: Record<PracticeAutomatedReviewPolicy["automatedReview"]["mode"], string> =
	{
		LANGUAGE_MODEL: "Language model",
		NONE: "No automated review",
	};

const EVIDENCE_SUFFICIENCY_LABELS: Record<
	PracticeAutomatedReviewPolicy["automatedReview"]["evidenceSufficiency"],
	string
> = {
	SUFFICIENT_WHEN_REQUIREMENTS_MET: "Requirements are sufficient",
	DECLARED_EVIDENCE_INSUFFICIENT: "Available evidence is not enough",
	NONE: "No evidence check",
};

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

export function reviewModeLabel(mode: PracticeAutomatedReviewPolicy["automatedReview"]["mode"]) {
	return REVIEW_MODE_LABELS[mode];
}

export function evidenceSufficiencyLabel(
	sufficiency: PracticeAutomatedReviewPolicy["automatedReview"]["evidenceSufficiency"],
) {
	return EVIDENCE_SUFFICIENCY_LABELS[sufficiency];
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
	if (requirements.automatedReview.mode === "NONE") return "No automated review";
	if (!supportedModes.includes(requirements.automatedReview.mode)) {
		return "Review mode not supported";
	}
	if (requirements.automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT") {
		return "Additional context required";
	}
	return null;
}

export function automatedReviewStatusLabel(
	automatedReview: PracticeAutomatedReviewPolicy["automatedReview"],
) {
	if (automatedReview.mode === "NONE") return "No automated review";
	if (automatedReview.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT") {
		return "Additional context required";
	}
	return null;
}
