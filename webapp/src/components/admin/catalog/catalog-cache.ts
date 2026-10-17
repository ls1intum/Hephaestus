import type { Catalog, CatalogPractice } from "@/api/types.gen";

export function reorderCatalogGroups(catalog: Catalog, orderedSlugs: readonly string[]): Catalog {
	const positions = new Map(orderedSlugs.map((slug, position) => [slug, position]));
	return {
		...catalog,
		groups: catalog.groups.map((group) => ({
			...group,
			position: positions.get(group.slug) ?? group.position,
		})),
	};
}

export function placeCatalogPractice(
	catalog: Catalog,
	practiceSlug: string,
	groupSlug: string | null,
	position: number,
): Catalog {
	const moved = catalog.practices.find((practice) => practice.slug === practiceSlug);
	if (!moved) {
		return catalog;
	}
	const sourceGroupSlug = moved.groupSlug;
	const source = orderedPractices(catalog, sourceGroupSlug).filter(
		(practice) => practice.slug !== practiceSlug,
	);
	const destination =
		sourceGroupSlug === groupSlug
			? source
			: orderedPractices(catalog, groupSlug).filter((practice) => practice.slug !== practiceSlug);
	destination.splice(Math.max(0, Math.min(position, destination.length)), 0, moved);

	const positions = new Map<string, { groupSlug: string | null; position: number }>();
	for (const [index, practice] of source.entries()) {
		positions.set(practice.slug, { groupSlug: sourceGroupSlug, position: index });
	}
	for (const [index, practice] of destination.entries()) {
		positions.set(practice.slug, { groupSlug, position: index });
	}
	const destinationGroupOffered =
		groupSlug === null ||
		catalog.groups.find((group) => group.slug === groupSlug)?.offered === true;

	return {
		...catalog,
		practices: catalog.practices.map((practice) => {
			const next = positions.get(practice.slug);
			if (!next) {
				return practice;
			}
			return {
				...practice,
				groupSlug: next.groupSlug,
				position: next.position,
				...(practice.slug === practiceSlug
					? { offered: !practice.excluded && destinationGroupOffered }
					: {}),
			};
		}),
	};
}

function orderedPractices(catalog: Catalog, groupSlug: string | null): CatalogPractice[] {
	return catalog.practices
		.filter((practice) => practice.groupSlug === groupSlug)
		.sort((a, b) => a.position - b.position || a.definition.name.localeCompare(b.definition.name));
}
