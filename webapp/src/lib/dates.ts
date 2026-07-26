/**
 * Coerce a timestamp as it arrives from the generated client to a usable `Date`.
 *
 * The generated client *types* date fields as `Date`, but its response transformers are not wired
 * into the SDK calls, so at runtime they arrive as ISO strings. Every read goes through this one
 * coercion rather than each call site inventing its own: a malformed or absent value degrades to
 * `undefined` — never a fabricated "now", never the string `Invalid Date` rendered at the user.
 */
export function asDate(value: Date | string | undefined | null): Date | undefined {
	if (value == null) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date;
}
