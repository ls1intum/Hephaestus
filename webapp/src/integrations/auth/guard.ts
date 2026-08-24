import type { QueryClient } from "@tanstack/react-query";
import {
	getCurrentUserMembershipOptions,
	getCurrentUserOptions,
} from "@/api/@tanstack/react-query.gen";
import type { CurrentUserView, WorkspaceMembership } from "@/api/types.gen";
import { isRecord } from "@/lib/is-record";

/**
 * Shared by the route guards and `AuthContext` so both read one cache entry on one schedule. A 401
 * is a definitive "not signed in", not a transient error, so it is never retried.
 */
export function currentUserQueryOptions() {
	return { ...getCurrentUserOptions(), retry: false, staleTime: 30_000 };
}

/**
 * Resolve the current user for route `beforeLoad` guards (ADR 0017 cookie-session auth).
 *
 * Reads `GET /user` through the query client so the result is cached and shared
 * with the in-app `useAuth` surface — this makes the first paint correct (no auth
 * flash) because the route blocks on the same query the rest of the app reads.
 *
 * `revalidateIfStale` refreshes in the background rather than blocking, because this guard runs on
 * EVERY authenticated navigation and one flaky request would bounce a signed-in user to /login.
 * Without it `ensureQueryData` would serve a revoked `appRole` for the life of the tab.
 *
 * A 401/403 (or any fetch failure) resolves to `null` rather than throwing, so
 * guards can branch on authentication without try/catch noise at every call site.
 */
export async function resolveCurrentUser(
	queryClient: QueryClient,
): Promise<CurrentUserView | null> {
	try {
		return await queryClient.ensureQueryData({
			...currentUserQueryOptions(),
			revalidateIfStale: true,
		});
	} catch {
		return null;
	}
}

/**
 * Whether an account is an instance admin. The authoritative source is the `appRole` field returned
 * by `GET /user` (ADR 0017); the client is not a security boundary (every admin endpoint is enforced
 * server-side by `hasAuthority('app_admin')`), so a single appRole check is both correct and simplest.
 * Accepts the raw generated `CurrentUserView` (or null) so route guards and `useAuth()` share one
 * definition.
 */
export function isAppAdmin(
	user: Partial<Pick<CurrentUserView, "appRole">> | null | undefined,
): boolean {
	return user?.appRole === "APP_ADMIN";
}

/** A "not a member" answer, as opposed to a transport failure, which carries no status at all. */
function isServerRefusal(error: unknown): boolean {
	if (!isRecord(error)) return false;
	const status = error.status;
	return typeof status === "number" && status >= 400 && status < 500;
}

/**
 * Shared by the route guard and `useWorkspaceAccess` so both read one cache entry on one schedule —
 * the hook's default `staleTime` of 0 would otherwise refetch the moment the guard's fetch landed.
 * `staleTime` is therefore also the bound on how long a role change takes to reach the UI.
 */
export function workspaceMembershipQueryOptions(workspaceSlug: string) {
	return {
		...getCurrentUserMembershipOptions({ path: { workspaceSlug } }),
		staleTime: 30_000,
		// Retrying a refusal would stall the redirect of everyone who is legitimately not a member.
		retry: (failureCount: number, error: unknown) => !isServerRefusal(error) && failureCount < 2,
	};
}

/**
 * `fetchQuery`, not `ensureQueryData`: the latter ignores `staleTime`, so a revoked admin would keep
 * the admin UI for the life of the tab. Any failure resolves to `null` — unverifiable is not allowed.
 */
export async function resolveWorkspaceMembership(
	queryClient: QueryClient,
	workspaceSlug: string,
): Promise<WorkspaceMembership | null> {
	try {
		return await queryClient.fetchQuery(workspaceMembershipQueryOptions(workspaceSlug));
	} catch {
		return null;
	}
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
	if (/[\s\p{Cc}]/u.test(decoded)) return "/";
	// Must be a rooted path and not a protocol-relative `//host` escape.
	if (!decoded.startsWith("/") || decoded.startsWith("//")) return "/";
	// Reject a leading-segment that smuggles a scheme ("/javascript:"), a backslash escape
	// ("/\evil", which browsers normalise to "//evil"), or a userinfo `@` host trick ("/@evil").
	if (/^\/[\\]/.test(decoded) || /^\/+[a-z]+:/i.test(decoded) || decoded.startsWith("/@"))
		return "/";
	// Validation ran on the fully-decoded value; return the ORIGINAL so legitimate encoded
	// segments (e.g. a `%26` in a query string) are preserved exactly as the caller intended.
	return value;
}
