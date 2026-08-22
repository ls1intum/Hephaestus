/**
 * Whether a value is a keyed object that can be probed for arbitrary string names.
 *
 * Arrays are objects, so `typeof` alone admits them, and every named key on one reads as
 * `undefined` — a decoded JSON array would pass as a record with every field missing rather than be
 * turned away. Ruling them out here rather than at each call site is what keeps the name true.
 *
 * Past that the claim is deliberately weak — every property still reads as `unknown`, so each field
 * a caller cares about has to be checked on its own. That is what makes it safe to point at decoded
 * JSON, a thrown request error, or anything else whose shape is a guess.
 */
export function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}
