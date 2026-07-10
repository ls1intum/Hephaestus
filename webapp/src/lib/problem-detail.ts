/**
 * Extract a human-readable message from a thrown request error.
 *
 * The generated client (with `throwOnError`) throws the parsed response body on a
 * non-2xx. For RFC 9457 problem+json failures the server returns
 * `{ type, title, status, detail }`; we prefer `detail`, then `title`,
 * then the controller's legacy `{ error }` shape, then the caller's `fallback`.
 */
export function problemDetailOf(
	err: unknown,
	fallback = "An unexpected error occurred. Please try again.",
): string {
	if (typeof err === "string") {
		return err;
	}
	if (err && typeof err === "object") {
		const record = err as Record<string, unknown>;
		for (const key of ["detail", "title", "error", "message"] as const) {
			const value = record[key];
			if (typeof value === "string" && value.trim().length > 0) {
				return value;
			}
		}
	}
	if (err instanceof Error && err.message) {
		return err.message;
	}
	return fallback;
}

/**
 * Extract the HTTP status code from a thrown request error.
 *
 * With `throwOnError`, the generated client throws the parsed response body. RFC 9457
 * problem+json failures carry the numeric `status` (e.g. `403`, `500`); network failures
 * and non-JSON gateway errors have none, so this returns `undefined` for them. Callers use
 * it to distinguish an authorization refusal from a transient error.
 */
export function httpStatusOf(err: unknown): number | undefined {
	if (err && typeof err === "object") {
		const status = (err as Record<string, unknown>).status;
		if (typeof status === "number") {
			return status;
		}
	}
	return undefined;
}
