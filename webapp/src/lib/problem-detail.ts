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
