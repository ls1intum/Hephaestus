import { z } from "zod";

/**
 * One level of a detail-drawer stack. Encoded in the URL as `kind:id`, so `?detail=area:code-review`
 * and `?detail=area:code-review&detail=practice:describe-what-and-why` are a one- and a two-level
 * stack over the same page.
 */
export interface DetailStackEntry {
	kind: string;
	id: string;
}

/**
 * Order is the stack order, so this deliberately does not deduplicate the way
 * {@link import("@/lib/search-params").multiValue} does — two levels may legitimately show the same
 * kind, and reordering them would reorder the drawers.
 */
const detailParam = z
	.union([z.string().transform((value) => [value]), z.array(z.string())])
	.optional()
	.catch(undefined);

export const detailStackSchema = z.object({ detail: detailParam });

export type DetailStackSearch = z.infer<typeof detailStackSchema>;

export const DETAIL_STACK_SEARCH_PARAMS: ["detail"] = ["detail"];

export function parseDetailStack(raw: string[] | undefined): DetailStackEntry[] {
	if (!raw) return [];
	const entries: DetailStackEntry[] = [];
	for (const value of raw) {
		const separator = value.indexOf(":");
		if (separator <= 0 || separator === value.length - 1) continue;
		entries.push({ kind: value.slice(0, separator), id: value.slice(separator + 1) });
	}
	return entries;
}

export function encodeDetailStack(entries: DetailStackEntry[]): string[] | undefined {
	return entries.length > 0 ? entries.map((entry) => `${entry.kind}:${entry.id}`) : undefined;
}

export function detailStackKey(entry: DetailStackEntry): string {
	return `${entry.kind}:${entry.id}`;
}
