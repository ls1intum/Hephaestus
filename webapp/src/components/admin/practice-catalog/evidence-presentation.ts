import type {
	PracticeAutomatedAssessmentPolicy,
	PracticeEvidenceSourceOption,
} from "@/api/types.gen";

const ASSESSMENT_MODE_LABELS: Record<
	PracticeAutomatedAssessmentPolicy["automatedAssessment"]["mode"],
	string
> = {
	LANGUAGE_MODEL: "Language model",
	NONE: "No automated assessment",
};

const EVIDENCE_SUFFICIENCY_LABELS: Record<
	PracticeAutomatedAssessmentPolicy["automatedAssessment"]["evidenceSufficiency"],
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
	requirement: PracticeAutomatedAssessmentPolicy["requiredEvidence"][number],
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

export function assessmentModeLabel(
	mode: PracticeAutomatedAssessmentPolicy["automatedAssessment"]["mode"],
) {
	return ASSESSMENT_MODE_LABELS[mode];
}

export function evidenceSufficiencyLabel(
	sufficiency: PracticeAutomatedAssessmentPolicy["automatedAssessment"]["evidenceSufficiency"],
) {
	return EVIDENCE_SUFFICIENCY_LABELS[sufficiency];
}

export function canAttemptAutomatedAssessment(
	requirements: PracticeAutomatedAssessmentPolicy,
	supportedModes: readonly PracticeAutomatedAssessmentPolicy["automatedAssessment"]["mode"][],
) {
	return (
		supportedModes.includes(requirements.automatedAssessment.mode) &&
		requirements.automatedAssessment.evidenceSufficiency === "SUFFICIENT_WHEN_REQUIREMENTS_MET"
	);
}

export function automatedAssessmentUnavailableLabel(
	requirements: PracticeAutomatedAssessmentPolicy,
	supportedModes: readonly PracticeAutomatedAssessmentPolicy["automatedAssessment"]["mode"][],
) {
	if (requirements.automatedAssessment.mode === "NONE") return "No automated assessment";
	if (!supportedModes.includes(requirements.automatedAssessment.mode)) {
		return "Assessment mode not supported";
	}
	if (requirements.automatedAssessment.evidenceSufficiency === "DECLARED_EVIDENCE_INSUFFICIENT") {
		return "Additional context required";
	}
	return null;
}
