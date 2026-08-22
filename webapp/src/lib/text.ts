/**
 * Text that reaches the screen, where a blank string is absence rather than a value.
 *
 * <p>A name, label or URL the server stored as `""` is not a name, label or URL: rendering it leaves
 * a gap where the reader expects a word, and `<img src="">` re-requests the current page. That is a
 * different question from `null` versus `undefined`, so it is asked here by name instead of being
 * implied by the falsiness of `||` — which would answer it the same way for `0` and `false`, where
 * it is wrong.
 */

/** Whether `value` is present and carries at least one character. */
export function hasText(value: string | null | undefined): value is string {
	return value != null && value !== "";
}

/**
 * The first of `values` that carries text, or `undefined` when none does — the shape of a display
 * name assembled from progressively weaker sources, each of which the server may have left blank.
 */
export function firstNonBlank(...values: (string | null | undefined)[]): string | undefined {
	return values.find((value) => hasText(value));
}
