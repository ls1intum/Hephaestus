import type { Practice, PracticeGroup, UpdatePracticeGroupRequest } from "@/api/types.gen";

export function practiceCatalogStructureScope(workspaceSlug: string) {
	return { id: `practice-catalog:${workspaceSlug}:structure` };
}

export function byPosition<T extends { position: number; name: string }>(a: T, b: T) {
	return a.position - b.position || a.name.localeCompare(b.name);
}

export function applyPositions<T extends { position: number; slug: string }>(
	items: T[],
	orderedSlugs: string[],
): T[] {
	const positionBySlug = new Map(orderedSlugs.map((slug, index) => [slug, index]));
	return items.map((item) => {
		const position = positionBySlug.get(item.slug);
		return position === undefined ? item : { ...item, position };
	});
}

export function upsertGroup(groups: PracticeGroup[], updated: PracticeGroup): PracticeGroup[] {
	return groups.some((group) => group.slug === updated.slug)
		? groups.map((group) => (group.slug === updated.slug ? updated : group))
		: [...groups, updated];
}

export function withGroupDetails(
	groups: PracticeGroup[],
	slug: string,
	details: UpdatePracticeGroupRequest,
): PracticeGroup[] {
	return groups.map((group) => (group.slug === slug ? { ...group, ...details } : group));
}

export function removeGroup(groups: PracticeGroup[], slug: string): PracticeGroup[] {
	return groups.filter((group) => group.slug !== slug);
}

export function upsertPractice(practices: Practice[], updated: Practice): Practice[] {
	return practices.some((practice) => practice.slug === updated.slug)
		? practices.map((practice) => (practice.slug === updated.slug ? updated : practice))
		: [...practices, updated];
}

export type PracticePosition = Pick<Practice, "groupSlug" | "position" | "slug">;

const byPracticePosition = (a: Practice, b: Practice) =>
	a.position - b.position || a.slug.localeCompare(b.slug);

export function movePractice(
	practices: Practice[],
	slug: string,
	groupSlug: string | null,
	position: number,
): Practice[] {
	const moving = practices.find((practice) => practice.slug === slug);
	if (!moving) {
		return practices;
	}

	const sourceGroupSlug = moving.groupSlug;
	const inGroup = (candidate: Practice, candidateGroupSlug: string | null) =>
		candidate.slug !== slug && candidate.groupSlug === candidateGroupSlug;
	const source = practices
		.filter((practice) => inGroup(practice, sourceGroupSlug))
		.sort(byPracticePosition);
	const destination =
		sourceGroupSlug === groupSlug
			? source
			: practices.filter((practice) => inGroup(practice, groupSlug)).sort(byPracticePosition);
	destination.splice(Math.min(position, destination.length), 0, { ...moving, groupSlug });

	const positions = [
		...(sourceGroupSlug === groupSlug
			? []
			: source.map((practice, index) => ({
					slug: practice.slug,
					groupSlug: sourceGroupSlug,
					position: index,
				}))),
		...destination.map((practice, index) => ({
			slug: practice.slug,
			groupSlug,
			position: index,
		})),
	];
	return applyPracticePositions(practices, positions);
}

export function practicePositionSnapshot(
	practices: Practice[],
	practiceSlug: string,
	destinationGroupSlug: string | null,
): PracticePosition[] {
	const sourceGroupSlug =
		practices.find((practice) => practice.slug === practiceSlug)?.groupSlug ?? null;
	return practices
		.filter(({ groupSlug }) => groupSlug === sourceGroupSlug || groupSlug === destinationGroupSlug)
		.map(({ slug, groupSlug, position }) => ({ slug, groupSlug, position }));
}

export function applyPracticePositions(
	practices: Practice[],
	positions: PracticePosition[],
): Practice[] {
	const bySlug = new Map(positions.map((position) => [position.slug, position]));
	return practices.map((practice) => {
		const moved = bySlug.get(practice.slug);
		return moved ? { ...practice, groupSlug: moved.groupSlug, position: moved.position } : practice;
	});
}

/** Deleting a group keeps its practices, unassigned and after the ones already there. */
export function unassignPractices(practices: Practice[], groupSlug: string): Practice[] {
	const firstPosition =
		Math.max(
			-1,
			...practices
				.filter((practice) => practice.groupSlug === null)
				.map((practice) => practice.position),
		) + 1;
	let offset = 0;
	return practices.map((practice) => {
		if (practice.groupSlug !== groupSlug) {
			return practice;
		}
		const position = firstPosition + offset;
		offset += 1;
		return { ...practice, groupSlug: null, position };
	});
}
