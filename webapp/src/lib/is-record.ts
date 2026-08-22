/**
 * Whether a value can be probed for arbitrary string keys.
 *
 * The claim is deliberately weak — every property still reads as `unknown`, so each field a caller
 * cares about has to be checked on its own. That is what makes it safe to point at decoded JSON, a
 * thrown request error, or anything else whose shape is a guess.
 */
export function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}
