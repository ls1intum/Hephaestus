/** Facts of a lead or a result from values that a reader gives, in the shape that `lib/contract.ts` accepts. */
import type { Facts } from "./contract.ts";

/** The longest text of one fact. */
const FACT_CHARS = 200;

/**
 * The values as facts. A text becomes one line of at most 200 characters, and an empty text or an
 * undefined value is no fact.
 */
export function factsOf(values: Record<string, string | number | boolean | undefined>): Facts {
	const facts: Facts = {};
	for (const [key, value] of Object.entries(values)) {
		if (typeof value === "string") {
			const line = value.replaceAll(/\s+/gu, " ").trim().slice(0, FACT_CHARS);
			if (line !== "") {
				facts[key] = line;
			}
		} else if (value !== undefined) {
			facts[key] = value;
		}
	}
	return facts;
}
