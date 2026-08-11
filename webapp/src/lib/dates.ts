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
 * Hands a wire-shaped fixture to a prop typed with the generated view.
 *
 * A fixture that reaches the component through MSW needs none of this — it is serialised on the way
 * and the generated type is the only one in play. One passed straight to a prop has to cross the gap
 * `Wire` describes, and the two types do not overlap, so something has to assert it. Doing it here
 * keeps the literal checked against `Wire<T>` by the parameter type, and keeps `as unknown as` out of
 * the fixture files, where it would read as a fixture nobody type-checked.
 */
export function asWire<T>(value: Wire<T>): T {
	return value as unknown as T;
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
