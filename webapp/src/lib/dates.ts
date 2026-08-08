/**
 * The generated client *types* date fields as `Date` but its response transformers are not wired
 * into `sdk.gen.ts`, so at runtime they arrive as ISO strings. Every read goes through this.
 */
export function asDate(value: Date | string | undefined | null): Date | undefined {
	if (value == null) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date;
}

/**
 * A generated view as it actually arrives: every `Date` in it, however deeply nested, is a string.
 *
 * Type fixtures with this rather than with the generated view: a fixture built from `new Date(…)`
 * type-checks and then tests a shape production never produces, so `value.toLocaleDateString()`
 * stays green in the story and throws on the first real response.
 */
export type Wire<T> = T extends Date
	? string
	: T extends readonly (infer Element)[]
		? Wire<Element>[]
		: T extends object
			? { [Key in keyof T]: Wire<T[Key]> }
			: T;
