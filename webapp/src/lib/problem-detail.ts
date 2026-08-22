import { isRecord } from "@/lib/is-record";

/**
 * A human-readable message from a thrown request error: the generated client (with `throwOnError`)
 * throws the parsed response body on a non-2xx, so RFC 9457's `detail` and `title` and the
 * controllers' legacy `{ error }` are the shapes worth reading.
 *
 * **`message` is deliberately not among them**: a network rejection and a null-deref in our own code
 * are both a thrown `TypeError`, so only wording the server chose is ever shown.
 */
export function problemDetailOf(
	err: unknown,
	fallback = "An unexpected error occurred. Please try again.",
): string {
	if (typeof err === "string") {
		return err;
	}
	if (isRecord(err)) {
		for (const key of ["detail", "title", "error"] as const) {
			const value = err[key];
			if (typeof value === "string" && value.trim().length > 0) {
				return value;
			}
		}
	}
	return fallback;
}

/**
 * `undefined` is meaningful here and must not be collapsed to a number: it means the request never
 * got an HTTP answer (offline, DNS failure, CORS, an aborted fetch), which is a different situation
 * from any status the server could have returned.
 */
export function problemStatusOf(err: unknown): number | undefined {
	if (!isRecord(err)) {
		return undefined;
	}
	const direct = err.status;
	if (typeof direct === "number" && Number.isInteger(direct)) {
		return direct;
	}
	const response = err.response;
	if (isRecord(response)) {
		const nested = response.status;
		if (typeof nested === "number" && Number.isInteger(nested)) {
			return nested;
		}
	}
	return undefined;
}
