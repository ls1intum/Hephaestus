/** Pretty-print a JSON string for a detail panel; returns the raw string if it is not valid JSON. */
export function prettyJson(value: string | undefined): string | null {
	if (!value) return null;
	try {
		return JSON.stringify(JSON.parse(value), null, 2);
	} catch {
		return value;
	}
}
