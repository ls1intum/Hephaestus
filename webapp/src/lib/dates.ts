export type DateLike = Date | string | undefined | null;

/**
 * The generated client revives every `format: date-time` response field into a real `Date`
 * (`transformer: true` in `openapi-ts.config.ts`), so this is for the payloads outside that
 * guarantee — chiefly the Mentor SSE stream, parsed by hand in `use-mentor-chat.ts` because its
 * operation is excluded from generation, whose timestamps arrive as raw strings. An unusable value
 * comes back `undefined` rather than an Invalid Date that would render as "Invalid Date".
 */
export function asDate(value: DateLike): Date | undefined {
	if (value == null) return undefined;
	const date = value instanceof Date ? value : new Date(value);
	return Number.isNaN(date.getTime()) ? undefined : date;
}

/**
 * A generated view as it looks *on the wire*: every `Date` in it, however deeply nested, is a
 * string. Type MSW fixtures with this — they are serialised JSON, which has no date type.
 */
export type Wire<T> = T extends Date
	? string
	: T extends readonly (infer Element)[]
		? Wire<Element>[]
		: T extends object
			? { [Key in keyof T]: Wire<T[Key]> }
			: T;
