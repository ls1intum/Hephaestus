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

/**
 * A real bundled practice as the server serves it: the catalog's own words, with the document
 * preamble composed into the criteria. JSON widens the enum values to `string`, so the settings
 * that are not text are spelled out here.
 */
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
export const realGroupName = group.name;
export const realGroupSlug = group.slug;
