export function hasText(value: string | null | undefined): value is string {
	return value != null && value !== "";
}

/**
 * The first of `values` that carries text — a display name assembled from progressively weaker
 * sources, any of which the server may have stored as `""` rather than left null.
 */
export function firstNonBlank(...values: (string | null | undefined)[]): string | undefined {
	return values.find((value) => hasText(value));
}
