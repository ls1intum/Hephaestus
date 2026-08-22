/**
 * What {@link asDate} accepts, and therefore the honest parameter type for anything that formats a
 * date through it: a real `Date`, the raw ISO string that appears in its place when the transformer
 * is not in the path, or nothing at all.
 */
export type DateLike = Date | string | undefined | null;

/**
 * Normalises a date field to a `Date`, or to `undefined` when there is nothing usable to show.
 *
 * The generated client revives every `format: date-time` field in a *response* into a real `Date`
 * (`openapi-ts.config.ts` sets `transformer: true` on the SDK plugin), so callers on a generated
 * query hook already hold a `Date`. This exists for the two cases that stay outside that guarantee:
 * a field the server may omit, and a payload that never went through the generated SDK — the Mentor
 * SSE stream is parsed by hand in `use-mentor-chat.ts` because its operation is excluded from
 * generation, so its timestamps arrive as raw strings.
 *
 * Returning `undefined` rather than an Invalid Date keeps a bad value from rendering as the string
 * "Invalid Date"; callers pick their own fallback.
 */
export function asDate(value: DateLike): Date | undefined {
	if (value == null) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date;
}

/**
 * A generated view as it looks *on the wire*: every `Date` in it, however deeply nested, is a string.
 *
 * Use this to type a fixture that a test or story serves through MSW, because that is literally what
 * such a fixture is — JSON, which has no date type. Typing it as the generated view instead would
 * force `new Date(…)` into a payload that is about to be `JSON.stringify`d anyway, and would let a
 * misspelled or missing field pass unchecked.
 *
 * A fixture passed *straight to a prop* needs the opposite: real `Date`s, because that is what the
 * transformer hands the component at runtime. There is deliberately no cast helper for that
 * direction — needing one would mean a component is being shown a shape production never sends.
 */
export type Wire<T> = T extends Date
	? string
	: T extends readonly (infer Element)[]
		? Wire<Element>[]
		: T extends object
			? { [Key in keyof T]: Wire<T[Key]> }
			: T;
