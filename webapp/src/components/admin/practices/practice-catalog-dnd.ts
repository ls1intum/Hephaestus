import type { Practice } from "@/api/types.gen";

export interface PracticeDropTarget {
	areaSlug: string | null;
	position: number;
}

export function getPracticeDropTarget(
	practices: Practice[],
	activeSlug: string,
	areaSlug: string | null,
	anchorSlug?: string,
	afterAnchor = false,
): PracticeDropTarget | null {
	if (anchorSlug === activeSlug) return null;
	const destination = practices
		.filter((practice) => practice.slug !== activeSlug && (practice.areaSlug ?? null) === areaSlug)
		.sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name));
	if (!anchorSlug) return { areaSlug, position: destination.length };
	const anchorIndex = destination.findIndex((practice) => practice.slug === anchorSlug);
	if (anchorIndex < 0) return null;
	return { areaSlug, position: anchorIndex + (afterAnchor ? 1 : 0) };
}
