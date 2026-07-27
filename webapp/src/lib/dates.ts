/**
 * The generated client *types* date fields as `Date` but its response transformers are not wired
 * into `sdk.gen.ts`, so at runtime they arrive as ISO strings. Every read goes through this.
 */
export function asDate(value: Date | string | undefined | null): Date | undefined {
	if (value == null) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date;
}
