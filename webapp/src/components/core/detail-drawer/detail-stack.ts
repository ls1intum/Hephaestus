import { z } from "zod";
import { multiValue } from "@/lib/search-params";

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
 * Each level mounts a drawer, a portal and a focus trap, and the URL is untrusted input by design —
 * it is meant to be shared and hand-edited. No surface stacks this deep.
 */
export const DETAIL_STACK_MAX_DEPTH = 4;

/**
 * Validated against the kinds a route can render, so a surface never receives a level it has no
 * component for. `multiValue` dedupes: the same entry twice is never a stack, and appending is one
 * double-click away from producing it.
 */
export function detailStackSchema<TKind extends string>(kinds: readonly TKind[]) {
	const known = new Set<string>(kinds);
	return z.object({
		detail: multiValue.transform((values) =>
			values
				?.filter((value) => {
					const separator = value.indexOf(":");
					return (
						separator > 0 && separator < value.length - 1 && known.has(value.slice(0, separator))
					);
				})
				.slice(0, DETAIL_STACK_MAX_DEPTH),
		),
	});
}

export function parseDetailStack<TKind extends string>(
	raw: string[] | undefined,
): DetailStackEntry<TKind>[] {
	return (raw ?? []).map((value) => {
		const separator = value.indexOf(":");
		return { kind: value.slice(0, separator) as TKind, id: value.slice(separator + 1) };
	});
}

export function encodeDetailStack(entries: DetailStackEntry[]): string[] | undefined {
	return entries.length > 0 ? entries.map(detailStackKey) : undefined;
}

export function detailStackKey(entry: DetailStackEntry): string {
	return `${entry.kind}:${entry.id}`;
}
