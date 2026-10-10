import type { PracticeDefinition, PracticeReleaseField } from "@/api/types.gen";

/** A part of a practice's definition that can change on its own. */
export type PracticeDefinitionField = PracticeReleaseField["field"];

/**
 * The name of each part of a practice's definition, and where it sits on the definition: one list
 * for a catalog release's review and a practice's version history.
 */
export const PRACTICE_DEFINITION_FIELDS = {
	NAME: { label: "Name", key: "name" },
	SIGNALS: { label: "When to review", key: "signals" },
	EVIDENCE_REQUIREMENTS: { label: "Evidence to read", key: "evidenceRequirements" },
	REVIEW_WHEN: { label: "Review conditions", key: "reviewWhen" },
	SUBJECT: { label: "Person judged", key: "subject" },
	PRECONDITION: { label: "Only review when", key: "precondition" },
	CRITERIA: { label: "Review criteria", key: "criteria" },
	PRECOMPUTE_SCRIPT: { label: "Precompute script", key: "precomputeScript" },
	AUTOMATED_REVIEW_POLICY: { label: "Automated review settings", key: "automatedReviewPolicy" },
	WHY_IT_MATTERS: { label: "Why it matters", key: "whyItMatters" },
	WHAT_GOOD_LOOKS_LIKE: { label: "What good looks like", key: "whatGoodLooksLike" },
	GROUP_SLUG: { label: "Group", key: "groupSlug" },
	DELIVERY_BEHAVIOR: { label: "Feedback delivery", key: "deliveryBehavior" },
	VISUAL: { label: "Visual", key: "visual" },
	GUIDE: { label: "Guide", key: "guide" },
} satisfies Record<PracticeDefinitionField, { label: string; key: keyof PracticeDefinition }>;
