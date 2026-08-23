/**
 * Both of the extra clauses carry weight, because `typeof value === "object"` alone is true of more
 * than records:
 *
 * - An **array** is an object, and the domain field names a caller goes on to read off one are all
 *   `undefined`, so a decoded JSON array would pass as a record with every field missing. The keys an
 *   array does carry — `length`, `constructor`, its prototype's methods — are never the ones asked for.
 * - **`null`** is an object too, and every property read on it throws.
 *
 * Properties stay `unknown`, so each field a caller cares about still has to be checked on its own.
 */
export function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}
