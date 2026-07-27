/**
 * Extract a human-readable message from a thrown request error.
 *
 * The generated client (with `throwOnError`) throws the parsed response body on a non-2xx. For
 * RFC 9457 problem+json failures the server returns `{ type, title, status, detail }`; we prefer
 * `detail`, then `title`, then the controller's legacy `{ error }` shape, then the caller's
 * `fallback`.
 *
 * **`message` is deliberately not in that list**, which means only wording the server chose is ever
 * shown to a user. `message` would read every thrown `Error`, and nothing distinguishes a network
 * rejection's "Failed to fetch" from a null-deref inside our own code: `fetch` rejects with a
 * `TypeError`, and so does a bug. Reading it puts runtime internals in front of an admin under a
 * toast about saving a model, and at its best contributes browser wording that names no operation
 * and suggests no action.
 *
 * "The request never got an HTTP answer" is still available, from {@link problemStatusOf} returning
 * `undefined` — so a caller that wants to say so can, in its own copy, naming its own operation.
 * `QueryErrorAlert` does ("Check your connection, then try again").
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
		for (const key of ["detail", "title", "error"] as const) {
			const value = record[key];
			if (typeof value === "string" && value.trim().length > 0) {
				return value;
			}
		}
	}
	return fallback;
}

/**
 * Extract the HTTP status from a thrown request error, or `undefined` when there isn't one.
 *
 * RFC 9457 puts `status` in the problem body, which is what the generated client throws, so that is
 * the primary source; `response.status` is read as a fallback for the shapes that carry the raw
 * `Response` instead. `undefined` is meaningful and must not be collapsed to a number: it means the
 * request never got an HTTP answer (offline, DNS failure, CORS, an aborted fetch), which is a
 * different situation from any status the server could have returned.
 */
export function problemStatusOf(err: unknown): number | undefined {
	if (!err || typeof err !== "object") {
		return undefined;
	}
	const record = err as Record<string, unknown>;
	const direct = record.status;
	if (typeof direct === "number" && Number.isInteger(direct)) {
		return direct;
	}
	const response = record.response;
	if (response && typeof response === "object") {
		const nested = (response as Record<string, unknown>).status;
		if (typeof nested === "number" && Number.isInteger(nested)) {
			return nested;
		}
	}
	return undefined;
}
