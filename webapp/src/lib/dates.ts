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
 * <p>Type fixtures with this rather than with the generated view. A fixture built from `new Date(…)`
 * type-checks against the generated view and then tests a shape production never produces — the
 * component calls `value.toLocaleDateString()`, the story is green, and the screen throws
 * `TypeError: … is not a function` the first time a real response reaches it.
 */
export type Wire<T> = T extends Date
	? string
	: T extends readonly (infer Element)[]
		? Wire<Element>[]
		: T extends object
			? { [Key in keyof T]: Wire<T[Key]> }
			: T;
