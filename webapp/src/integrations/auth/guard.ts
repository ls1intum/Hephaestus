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
		return await queryClient.ensureQueryData({ ...getCurrentUserOptions(), retry: false });
	} catch {
		return null;
	}
}

/**
 * Whether an account is an instance admin: `appRole === "APP_ADMIN"` OR the `app_admin` authority is
 * present (the legacy `admin` string is also accepted while pre-rename tokens drain — ADR 0017).
 * `appRole` is the authoritative source; we tolerate role-string differences. Accepts the raw
 * generated `CurrentUserView` (or null) so route guards and `useAuth()` share one definition.
 */
export function isAppAdmin(
	user: Partial<Pick<CurrentUserView, "appRole" | "roles">> | null | undefined,
): boolean {
	const roles = user?.roles ?? [];
	return user?.appRole === "APP_ADMIN" || roles.includes("app_admin") || roles.includes("admin");
}

/**
 * Fully percent-decode a value, bounded against malicious double/triple-encoding.
 *
 * A single `decodeURIComponent` leaves `%252f` as `%2f`, so a payload like `/%252f%252fevil`
 * would pass a one-shot decode-then-check yet decode again in a downstream parser. We decode
 * in a bounded loop until the value is stable (or the cap is hit), so the checks below run on
 * the value a browser/router would ultimately see. The cap also stops a decode bomb.
 */
function fullyDecode(value: string): string {
	let current = value;
	for (let i = 0; i < 5; i++) {
		let decoded: string;
		try {
			decoded = decodeURIComponent(current);
		} catch {
			// Malformed percent-encoding (e.g. a lone "%") — treat as already fully decoded.
			return current;
		}
		if (decoded === current) {
			return current;
		}
		current = decoded;
	}
	return current;
}

/**
 * Validate a `returnTo` redirect target. Accepts only same-origin absolute paths
 * (a single leading slash, no `//` protocol-relative escape, no scheme), otherwise
 * falls back to `/`. Prevents open-redirect via crafted `?returnTo=` params.
 *
 * Checks run on the FULLY DECODED value (decode-then-check), so percent-encoded escapes —
 * `/%2f%2fevil.com`, `/%09/evil`, `/%20/evil`, `/%5cevil`, `/@evil` — cannot smuggle a
 * protocol-relative URL, control char, whitespace, or backslash past the same-origin gate.
 */
export function safeReturnTo(value: string | undefined): string {
	if (!value) return "/";
	const decoded = fullyDecode(value);
	// Whitespace (incl. space/tab) and control chars (NUL, newline, DEL, …) can defeat downstream
	// parsers or hide an escape — reject outright on the decoded value.
	// biome-ignore lint/suspicious/noControlCharactersInRegex: rejecting control chars is the point
	if (/[\s\x00-\x1f\x7f]/.test(decoded)) return "/";
	// Must be a rooted path and not a protocol-relative `//host` escape.
	if (!decoded.startsWith("/") || decoded.startsWith("//")) return "/";
	// Reject a leading-segment that smuggles a scheme ("/javascript:"), a backslash escape
	// ("/\evil", which browsers normalise to "//evil"), or a userinfo `@` host trick ("/@evil").
	if (/^\/[\\]/.test(decoded) || /^\/+[a-z]+:/i.test(decoded) || /^\/@/.test(decoded)) return "/";
	// Validation ran on the fully-decoded value; return the ORIGINAL so legitimate encoded
	// segments (e.g. a `%26` in a query string) are preserved exactly as the caller intended.
	return value;
}
