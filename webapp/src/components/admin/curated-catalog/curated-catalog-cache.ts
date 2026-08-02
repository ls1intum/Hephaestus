import type { CuratedCatalog } from "@/api/types.gen";

export function reorderCuratedAreas(
	catalog: CuratedCatalog,
	orderedSlugs: readonly string[],
): CuratedCatalog {
	const positions = new Map(orderedSlugs.map((slug, position) => [slug, position]));
	return {
		...catalog,
		areas: catalog.areas.map((area) => ({
			...area,
			position: positions.get(area.slug) ?? area.position,
		})),
	};
}

export function placeCuratedPractice(
	catalog: CuratedCatalog,
	practiceSlug: string,
	areaSlug: string | null,
	position: number,
): CuratedCatalog {
	const moved = catalog.practices.find((practice) => practice.slug === practiceSlug);
	if (!moved) return catalog;
	const sourceAreaSlug = moved.areaSlug ?? null;
	const source = orderedPractices(catalog, sourceAreaSlug).filter(
		(practice) => practice.slug !== practiceSlug,
	);
	const destination =
		sourceAreaSlug === areaSlug
			? source
			: orderedPractices(catalog, areaSlug).filter((practice) => practice.slug !== practiceSlug);
	destination.splice(Math.max(0, Math.min(position, destination.length)), 0, moved);

	const placements = new Map<string, { areaSlug: string | null; position: number }>();
	source.forEach((practice, index) =>
		placements.set(practice.slug, { areaSlug: sourceAreaSlug, position: index }),
	);
	destination.forEach((practice, index) =>
		placements.set(practice.slug, { areaSlug, position: index }),
	);
	const destinationAreaOffered =
		areaSlug === null ||
		catalog.areas.find((area) => area.slug === areaSlug)?.status.offered === true;

	return {
		...catalog,
		practices: catalog.practices.map((practice) => {
			const placement = placements.get(practice.slug);
			if (!placement) return practice;
			return {
				...practice,
				areaSlug: placement.areaSlug ?? undefined,
				position: placement.position,
				...(practice.slug === practiceSlug
					? { effectivelyOffered: practice.status.offered && destinationAreaOffered }
					: {}),
			};
		}),
	};
}

export function reorderCuratedPractices(
	catalog: CuratedCatalog,
	areaSlug: string | null,
	orderedSlugs: readonly string[],
): CuratedCatalog {
	const positions = new Map(orderedSlugs.map((slug, position) => [slug, position]));
	return {
		...catalog,
		practices: catalog.practices.map((practice) =>
			(practice.areaSlug ?? null) === areaSlug && positions.has(practice.slug)
				? { ...practice, position: positions.get(practice.slug) ?? practice.position }
				: practice,
		),
	};
}

export function orderedPracticeSlugs(catalog: CuratedCatalog, areaSlug: string | null): string[] {
	return orderedPractices(catalog, areaSlug).map((practice) => practice.slug);
}

function orderedPractices(catalog: CuratedCatalog, areaSlug: string | null) {
	return catalog.practices
		.filter((practice) => (practice.areaSlug ?? null) === areaSlug)
		.slice()
		.sort((a, b) => a.position - b.position || a.name.localeCompare(b.name));
}
