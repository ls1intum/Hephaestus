/**
 * Arrays are ruled out because every named key on one reads as `undefined`: a decoded JSON array
 * would otherwise pass as a record with every field missing. Properties stay `unknown`, so each
 * field a caller cares about still has to be checked on its own.
 */
export function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}
