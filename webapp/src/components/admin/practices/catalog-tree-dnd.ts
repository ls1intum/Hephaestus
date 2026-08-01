export interface CatalogDropTarget {
	areaSlug: string | null;
	position: number;
}

export function getCatalogDropTarget<
	T extends { areaSlug?: string; displayOrder: number; name: string; slug: string },
>(
	entries: readonly T[],
	activeSlug: string,
	areaSlug: string | null,
	anchorSlug?: string,
	afterAnchor = false,
): CatalogDropTarget | null {
	if (anchorSlug === activeSlug) return null;
	const destination = entries
		.filter((entry) => entry.slug !== activeSlug && (entry.areaSlug ?? null) === areaSlug)
		.sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name));
	if (!anchorSlug) return { areaSlug, position: destination.length };
	const anchorIndex = destination.findIndex((entry) => entry.slug === anchorSlug);
	if (anchorIndex < 0) return null;
	return { areaSlug, position: anchorIndex + (afterAnchor ? 1 : 0) };
}
