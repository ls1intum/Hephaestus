/**
 * Extract a human-readable message from a thrown request error.
 *
 * The generated client (with `throwOnError`) throws the parsed response body on a non-2xx. For
 * RFC 9457 problem+json failures the server returns `{ type, title, status, detail }`; we prefer
 * `detail`, then `title`, then the controller's legacy `{ error }` shape, then the caller's
 * `fallback`.
 *
 * **`message` is deliberately not in that list**: a network rejection and a null-deref in our own
 * code are both a thrown `TypeError`, so reading it would put runtime internals under a toast about
 * saving a model. Only wording the server chose is ever shown. A caller that needs to say "no HTTP
 * answer at all" gets that from {@link problemStatusOf} returning `undefined`.
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

/** The server's step-up challenge body (`StepUpRequiredException`). */
export interface StepUpProblem {
	code: "step_up_required";
	/** How recent the sign-in must be, per `hephaestus.auth.step-up-max-age`. */
	maxAgeSeconds?: number;
}

/**
 * Whether the error is the server's step-up challenge (403, `code = step_up_required`): the session
 * is valid, but its last interactive sign-in is too old for this action. Callers show
 * ConfirmAccessDialog instead of the generic error.
 */
export function isStepUpRequired(err: unknown): err is StepUpProblem {
	return (
		typeof err === "object" &&
		err !== null &&
		"code" in err &&
		(err as { code: unknown }).code === "step_up_required"
	);
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
