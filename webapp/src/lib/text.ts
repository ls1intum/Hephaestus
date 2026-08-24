export function hasText(value: string | null | undefined): value is string {
	return value != null && value !== "";
}

/** The first of `values` that carries text — `""` counts as absent, which is how names arrive. */
export function firstNonBlank(...values: (string | null | undefined)[]): string | undefined {
	return values.find((value) => hasText(value));
}
