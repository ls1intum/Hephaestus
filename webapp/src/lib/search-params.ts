import { z } from "zod";

export const multiValue = z
	.union([z.string().transform((value) => [value]), z.array(z.string())])
	.transform((values) => [...new Set(values)])
	.optional()
	.catch(undefined);

export function nonEmpty<T>(values: T[]): T[] | undefined {
	return values.length > 0 ? values : undefined;
}

/**
 * The page number as the URL should carry it. Page one is index `0`, which is also the default every
 * parser falls back to, so spelling it out would put `page=0` in every link a reader copies.
 */
export function pageParam(page: number | undefined): number | undefined {
	return page === 0 ? undefined : page;
}

export function narrowToEnum<T extends string>(
	values: string[] | undefined,
	allowed: readonly T[],
): T[] | undefined {
	if (!values?.length) return undefined;
	const kept = values.filter((value): value is T => (allowed as readonly string[]).includes(value));
	return kept.length > 0 ? kept : undefined;
}
