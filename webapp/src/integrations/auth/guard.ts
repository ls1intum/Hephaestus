import type { QueryClient } from "@tanstack/react-query";
import { getCurrentUserOptions } from "@/api/@tanstack/react-query.gen";
import type { CurrentUserView } from "@/api/types.gen";

/**
 * Resolve the current user for route `beforeLoad` guards (ADR 0017 cookie-session auth).
 *
 * Reads `GET /user` through the query client so the result is cached and shared
 * with the in-app `useAuth` surface — this makes the first paint correct (no auth
 * flash) because the route blocks on the same query the rest of the app reads.
 *
 * A 401/403 (or any fetch failure) resolves to `null` rather than throwing, so
 * guards can branch on authentication without try/catch noise at every call site.
 */
export async function resolveCurrentUser(
	queryClient: QueryClient,
): Promise<CurrentUserView | null> {
	try {
		return await queryClient.ensureQueryData(getCurrentUserOptions());
	} catch {
		return null;
	}
}

/**
 * Validate a `returnTo` redirect target. Accepts only same-origin absolute paths
 * (a single leading slash, no `//` protocol-relative escape, no scheme), otherwise
 * falls back to `/`. Prevents open-redirect via crafted `?returnTo=` params.
 */
export function safeReturnTo(value: string | undefined): string {
	if (!value) return "/";
	// Control chars (NUL, tab, newline, DEL, …) can defeat downstream parsers — reject outright.
	// biome-ignore lint/suspicious/noControlCharactersInRegex: rejecting control chars is the point
	if (/[\x00-\x1f\x7f]/.test(value)) return "/";
	if (!value.startsWith("/") || value.startsWith("//")) return "/";
	// Reject anything that smuggles a scheme (e.g. "/\evil" normalises oddly, "/javascript:")
	if (/^\/[\\]/.test(value) || /^\/+[a-z]+:/i.test(value)) return "/";
	return value;
}
