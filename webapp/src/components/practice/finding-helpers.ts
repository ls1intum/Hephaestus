import type { PracticeFindingDetail } from "@/api/types.gen";

type GuidanceMethod = NonNullable<PracticeFindingDetail["guidanceMethod"]>;

export interface EvidenceLocation {
	path: string;
	startLine?: number;
	endLine?: number;
}

export interface ParsedEvidence {
	locations: EvidenceLocation[];
	snippets: string[];
	references: string[];
}

const EMPTY_EVIDENCE: ParsedEvidence = { locations: [], snippets: [], references: [] };

/** Safely parse the `evidence` field from a finding detail. */
export function parseEvidence(evidence: PracticeFindingDetail["evidence"]): ParsedEvidence {
	if (!evidence || typeof evidence !== "object") return EMPTY_EVIDENCE;

	const raw = evidence as Record<string, unknown>;
	return {
		locations: Array.isArray(raw.locations)
			? (raw.locations as EvidenceLocation[]).filter((l) => typeof l.path === "string")
			: [],
		snippets: Array.isArray(raw.snippets)
			? (raw.snippets as string[]).filter((s) => typeof s === "string")
			: [],
		references: Array.isArray(raw.references)
			? (raw.references as string[]).filter((r) => typeof r === "string")
			: [],
	};
}

/** Human-readable label for guidance methods (Cognitive Apprenticeship). */
export const GUIDANCE_METHOD_LABELS: Record<GuidanceMethod, string> = {
	MODELING: "Modeling",
	COACHING: "Coaching",
	SCAFFOLDING: "Scaffolding",
	ARTICULATION: "Articulation",
	REFLECTION: "Reflection",
	EXPLORATION: "Exploration",
};

/** Human-readable label for the target type (e.g., "Pull Request"). */
export function formatTargetLabel(targetType: string): string {
	switch (targetType) {
		case "PULL_REQUEST":
			return "Pull Request";
		default:
			return targetType.replace(/_/g, " ");
	}
}
