import type { PracticeDefinition } from "@/api/types.gen";

import bundledCatalog from "@bundled-practices";

const group = bundledCatalog.groups.find(
	(candidate) => candidate.slug === "decisions-and-documentation",
);
const practice = group?.practices.find(
	(candidate) => candidate.slug === "published-decisions-name-the-alternatives",
);
if (group === undefined || practice === undefined) {
	throw new Error("Bundled document preview practice is missing");
}
const { definition } = practice;

// Validate imported catalog JSON before it reaches typed prop fixtures.
export const realPracticeDefinition = {
	name: definition.name,
	reviewOn: { events: definition.reviewOn.events, state: {} },
	appliesWhen: null,
	about: "AUTHOR",
	reads: {
		contract: definition.reads.contract,
		sources: [{ source: "docs.document.core", need: "COMPLETE" }],
	},
	reviewedBy: { by: "HEPHAESTUS", humanReason: null, limits: [] },
	criteria: `${bundledCatalog.criteriaPreambles["docs.document"]}\n\n---\n\n${definition.criteria}`,
	script: null,
	scan: null,
	lineNotes: definition.lineNotes,
	guidance: {
		whyItMatters: definition.guidance.whyItMatters,
		whatGoodLooksLike: definition.guidance.whatGoodLooksLike,
		goingWell: definition.guidance.goingWell,
		visual: null,
		guide: null,
	},
} satisfies PracticeDefinition;

export const realPracticeSlug = practice.slug;
export const realPracticeKind = practice.kind;
