export const MODEL_CONCURRENCY = 4;

/**
 * Run `work` for each item, at most `limit` at a time, in the order of the items. The workers share
 * one iterator, so each item runs once.
 */
export async function eachBounded<T>(
	items: Iterable<T>,
	limit: number,
	work: (item: T) => Promise<void>,
): Promise<void> {
	const shared = items[Symbol.iterator]();
	await Promise.all(
		Array.from({ length: limit }, async () => {
			for (let next = shared.next(); next.done !== true; next = shared.next()) {
				await work(next.value);
			}
		}),
	);
}
