import { z } from "zod";

/**
 * One level of a detail-drawer stack, encoded in the URL as `kind:id` — so `?detail=area:code-review`
 * is a one-level stack and `?detail=area:code-review&detail=practice:describe-what` is a two-level
 * one over the same page.
 */
export interface DetailStackEntry<TKind extends string = string> {
	kind: TKind;
	id: string;
}

/**
 * Levels beyond this are dropped. The URL is the input, and it is shareable by design, so the depth
 * a hand-written link can reach has to be bounded: each level mounts a drawer, a portal and a focus
 * trap, and no surface has a reason to stack more than a handful.
 */
export const DETAIL_STACK_MAX_DEPTH = 4;

const detailParam = z
	.union([z.string().transform((value) => [value]), z.array(z.string())])
	.optional()
	.catch(undefined);

export const detailStackSchema = z.object({ detail: detailParam });

/**
 * Reads the stack a route's `detail` param describes, keeping only levels the surface knows how to
 * render. An unrecognised kind is dropped rather than passed on, because a surface that receives one
 * has no component for it and would otherwise render a level that can never resolve.
 *
 * Repeats are dropped too: the same entry twice is never a meaningful stack, and appending is one
 * double-click away from producing it.
 */
export function parseDetailStack<TKind extends string>(
	raw: string[] | undefined,
	kinds: readonly TKind[],
): DetailStackEntry<TKind>[] {
	if (!raw) return [];
	const entries: DetailStackEntry<TKind>[] = [];
	const seen = new Set<string>();
	for (const value of raw) {
		if (entries.length === DETAIL_STACK_MAX_DEPTH) break;
		const separator = value.indexOf(":");
		if (separator <= 0 || separator === value.length - 1) continue;
		const kind = value.slice(0, separator);
		if (!(kinds as readonly string[]).includes(kind)) continue;
		if (seen.has(value)) continue;
		seen.add(value);
		entries.push({ kind: kind as TKind, id: value.slice(separator + 1) });
	}
	return entries;
}

export function encodeDetailStack(entries: DetailStackEntry[]): string[] | undefined {
	return entries.length > 0 ? entries.map(detailStackKey) : undefined;
}

export function detailStackKey(entry: DetailStackEntry): string {
	return `${entry.kind}:${entry.id}`;
}
