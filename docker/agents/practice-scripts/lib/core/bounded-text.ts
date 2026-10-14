/** Text that a bundled practice script returns, cut to the bounds that `contract.ts` validates. */
import { FACT_CHARS, type Facts } from "./contract.ts";

export { DIRECTION_CHARS } from "./contract.ts";

/** The text on one line, cut to `max` characters with an ellipsis. */
export function oneLine(text: string, max: number): string {
	const flat = text.replaceAll(/\s+/gu, " ").trim();
	return flat.length <= max ? flat : `${flat.slice(0, max - 1)}…`;
}

/**
 * The items joined with `separator`, as many as fit in `max` characters. The rest are counted, so
 * that a cut list does not read as the whole list.
 */
export function listWithin(items: readonly string[], max: number, separator = ", "): string {
	let list = "";
	for (const [index, item] of items.entries()) {
		const next = list === "" ? item : `${list}${separator}${item}`;
		const left = items.length - index - 1;
		const rest = left > 0 ? `${separator}and ${String(left)} more` : "";
		if (`${next}${rest}`.length > max) {
			const omitted = items.length - index;
			return list === ""
				? `${String(omitted)} items`
				: `${list}${separator}and ${String(omitted)} more`;
		}
		list = next;
	}
	return list;
}

/**
 * Facts from values that a scan computed. An empty string is not a fact, and a long string is cut to
 * one line of the fact bound.
 */
export function factsOf(values: Readonly<Record<string, string | number | boolean>>): Facts {
	const facts: Facts = {};
	for (const [key, value] of Object.entries(values)) {
		if (typeof value !== "string") {
			facts[key] = value;
		} else if (value.trim() !== "") {
			facts[key] = oneLine(value, FACT_CHARS);
		}
	}
	return facts;
}

/**
 * The directions for a line scan that matched nothing: what it scanned, and what a pattern of one
 * line cannot see. Without them, an empty list reads as a clean result.
 */
export function noMatchDirections(linesAdded: number, filesScanned: number): string[] {
	return [
		`Scanned ${String(linesAdded)} added lines in ${String(filesScanned)} files for this practice's line patterns; none matched.`,
		"A pattern sees one line: what spans lines or has no keyword, such as an early return, a discarded result or a missing else, is yours to enumerate from the diff.",
	];
}
