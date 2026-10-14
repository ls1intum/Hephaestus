/** Whether an optional string carries anything; `webapp/src/lib/text.ts`'s `hasText` says why there are copies. */
export function hasText(value: string | null | undefined): value is string {
	return value != null && value !== "";
}

/** Whether an optional string is empty once trimmed — the reading for a field a model wrote. */
export function isBlank(value: string | null | undefined): boolean {
	return !hasText(value?.trim());
}

/** A JSON object with keys. A list is not one. */
export function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}
