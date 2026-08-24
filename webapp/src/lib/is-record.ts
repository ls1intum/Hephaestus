/**
 * Arrays are excluded deliberately: a decoded JSON array is an object, so admitting it would let one
 * through as a record whose every domain field reads `undefined`. Properties stay `unknown`, so each
 * field a caller wants still has to be checked on its own.
 */
export function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}
