export interface FanoutPractice {
	slug: string;
	area?: string;
}

export interface PracticeFanout {
	areaCount: number;
	batches: string[][];
}

export function buildPracticeFanout(
	practices: readonly FanoutPractice[],
	batchSize: number,
): PracticeFanout {
	if (!Number.isInteger(batchSize) || batchSize <= 0) {
		throw new Error(`PI_PRACTICE_BATCH_SIZE must be a positive integer, got: ${batchSize}`);
	}

	const byArea = new Map<string, string[]>();
	for (const practice of practices) {
		if (!practice.slug) continue;
		const configuredArea = practice.area?.trim();
		const area =
			configuredArea === undefined || configuredArea === "" ? practice.slug : configuredArea;
		const slugs = byArea.get(area) ?? [];
		slugs.push(practice.slug);
		byArea.set(area, slugs);
	}

	const batches: string[][] = [];
	for (const slugs of byArea.values()) {
		for (let offset = 0; offset < slugs.length; offset += batchSize) {
			batches.push(slugs.slice(offset, offset + batchSize));
		}
	}
	return { areaCount: byArea.size, batches };
}
