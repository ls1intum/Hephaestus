/**
 * Safely extract an error message from an unknown thrown value.
 * Use this instead of inline `err instanceof Error ? err.message : String(err)`.
 */
export function extractErrorMessage(err: unknown): string {
	if (err instanceof Error) {
		return err.message;
	}
	if (typeof err === "string") {
		return err;
	}
	return "Unknown error";
}

/**
 * Create a structured error object for logging.
 * Preserves stack trace in development, omits in production.
 */
export function toLoggableError(err: unknown): { message: string; stack?: string } {
	const message = extractErrorMessage(err);
	const isDev = process.env.NODE_ENV !== "production";

	if (isDev && err instanceof Error && err.stack) {
		return { message, stack: err.stack };
	}

	return { message };
}
