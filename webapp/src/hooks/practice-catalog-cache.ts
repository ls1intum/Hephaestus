import type {
	Practice,
	PracticeArea,
	UpdatePracticeAreaRequest,
	UpdatePracticeRequest,
} from "@/api/types.gen";

export function practiceCatalogStructureScope(workspaceSlug: string) {
	return { id: `practice-catalog:${workspaceSlug}:structure` };
}

export function applyDisplayOrder<T extends { displayOrder: number; slug: string }>(
	items: T[],
	orderedSlugs: string[],
): T[] {
	const orderBySlug = new Map(orderedSlugs.map((slug, index) => [slug, index]));
	return items.map((item) => {
		const displayOrder = orderBySlug.get(item.slug);
		return displayOrder === undefined ? item : { ...item, displayOrder };
	});
}

function replaceArea(areas: PracticeArea[], updated: PracticeArea): PracticeArea[] {
	return areas.map((area) => (area.slug === updated.slug ? updated : area));
}

export function upsertArea(areas: PracticeArea[], updated: PracticeArea): PracticeArea[] {
	return areas.some((area) => area.slug === updated.slug)
		? replaceArea(areas, updated)
		: [...areas, updated];
}

export function patchArea(
	areas: PracticeArea[],
	slug: string,
	patch: UpdatePracticeAreaRequest,
): PracticeArea[] {
	return areas.map((area) => (area.slug === slug ? { ...area, ...patch } : area));
}

export function selectAreaPatch(
	area: PracticeArea,
	request: UpdatePracticeAreaRequest,
): UpdatePracticeAreaRequest {
	return {
		...(request.visibleInPracticeDashboards !== undefined
			? { visibleInPracticeDashboards: area.visibleInPracticeDashboards }
			: {}),
		...("color" in request ? { color: area.color } : {}),
		...("description" in request ? { description: area.description } : {}),
		...("displayOrder" in request ? { displayOrder: area.displayOrder } : {}),
		...("icon" in request ? { icon: area.icon } : {}),
		...("name" in request ? { name: area.name } : {}),
	};
}

function replacePractice(practices: Practice[], updated: Practice): Practice[] {
	return practices.map((practice) => (practice.slug === updated.slug ? updated : practice));
}

export function upsertPractice(practices: Practice[], updated: Practice): Practice[] {
	return practices.some((practice) => practice.slug === updated.slug)
		? replacePractice(practices, updated)
		: [...practices, updated];
}

export function patchPractice(
	practices: Practice[],
	slug: string,
	patch: Partial<Practice>,
): Practice[] {
	return practices.map((practice) =>
		practice.slug === slug ? { ...practice, ...patch } : practice,
	);
}

export function selectPracticePatch(
	practice: Practice,
	request: UpdatePracticeRequest,
): Partial<Practice> {
	const clear = new Set(request.clear);
	return {
		...("artifactType" in request ? { artifactType: practice.artifactType } : {}),
		...("criteria" in request ? { criteria: practice.criteria } : {}),
		...("automatedAssessmentPolicy" in request || "artifactType" in request
			? {
					automatedAssessmentPolicy: practice.automatedAssessmentPolicy,
					automatedAssessmentValidation: practice.automatedAssessmentValidation,
				}
			: {}),
		...("name" in request ? { name: practice.name } : {}),
		...("precomputeScript" in request || clear.has("PRECOMPUTE_SCRIPT")
			? { precomputeScript: practice.precomputeScript }
			: {}),
		...("triggerEvents" in request ? { triggerEvents: practice.triggerEvents } : {}),
		...("whatGoodLooksLike" in request || clear.has("WHAT_GOOD_LOOKS_LIKE")
			? { whatGoodLooksLike: practice.whatGoodLooksLike }
			: {}),
		...("whyItMatters" in request || clear.has("WHY_IT_MATTERS")
			? { whyItMatters: practice.whyItMatters }
			: {}),
		...("area" in request
			? { areaSlug: practice.areaSlug, displayOrder: practice.displayOrder }
			: {}),
	};
}

export type PracticePlacement = Pick<Practice, "areaSlug" | "displayOrder" | "slug">;

export function placePractice(
	practices: Practice[],
	slug: string,
	areaSlug: string | null,
	position: number,
): Practice[] {
	const moving = practices.find((practice) => practice.slug === slug);
	if (!moving) return practices;

	const sourceAreaSlug = moving.areaSlug ?? null;
	const inArea = (candidate: Practice, candidateAreaSlug: string | null) =>
		candidate.slug !== slug && (candidate.areaSlug ?? null) === candidateAreaSlug;
	const byOrder = (a: Practice, b: Practice) =>
		a.displayOrder - b.displayOrder || a.name.localeCompare(b.name);
	const source = practices.filter((practice) => inArea(practice, sourceAreaSlug)).sort(byOrder);
	const destination =
		sourceAreaSlug === areaSlug
			? source
			: practices.filter((practice) => inArea(practice, areaSlug)).sort(byOrder);
	destination.splice(Math.min(position, destination.length), 0, {
		...moving,
		areaSlug: areaSlug ?? undefined,
	});

	const placements = [
		...(sourceAreaSlug === areaSlug
			? []
			: source.map((practice, displayOrder) => ({
					slug: practice.slug,
					areaSlug: sourceAreaSlug ?? undefined,
					displayOrder,
				}))),
		...destination.map((practice, displayOrder) => ({
			slug: practice.slug,
			areaSlug: areaSlug ?? undefined,
			displayOrder,
		})),
	];
	return applyPracticePlacements(practices, placements);
}

export function practicePlacementSnapshot(
	practices: Practice[],
	practiceSlug: string,
	destinationAreaSlug: string | null,
): PracticePlacement[] {
	const sourceAreaSlug =
		practices.find((practice) => practice.slug === practiceSlug)?.areaSlug ?? null;
	return practices
		.filter((practice) => {
			const areaSlug = practice.areaSlug ?? null;
			return areaSlug === sourceAreaSlug || areaSlug === destinationAreaSlug;
		})
		.map(({ slug, areaSlug, displayOrder }) => ({ slug, areaSlug, displayOrder }));
}

export function applyPracticePlacements(
	practices: Practice[],
	placements: PracticePlacement[],
): Practice[] {
	const bySlug = new Map(placements.map((placement) => [placement.slug, placement]));
	return practices.map((practice) => {
		const placement = bySlug.get(practice.slug);
		return placement
			? {
					...practice,
					areaSlug: placement.areaSlug,
					displayOrder: placement.displayOrder,
				}
			: practice;
	});
}

export function removeArea(areas: PracticeArea[], slug: string): PracticeArea[] {
	return areas.filter((area) => area.slug !== slug);
}

export function unassignPractices(practices: Practice[], areaSlug: string): Practice[] {
	const firstDisplayOrder =
		Math.max(
			-1,
			...practices
				.filter((practice) => practice.areaSlug == null)
				.map((practice) => practice.displayOrder),
		) + 1;
	let offset = 0;
	return practices.map((practice) =>
		practice.areaSlug === areaSlug
			? {
					...practice,
					areaSlug: undefined,
					displayOrder: firstDisplayOrder + offset++,
				}
			: practice,
	);
}
