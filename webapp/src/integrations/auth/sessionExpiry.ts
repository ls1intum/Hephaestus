import type { QueryClient } from "@tanstack/react-query";
import { getCurrentUserQueryKey } from "@/api/@tanstack/react-query.gen";
import environment from "@/environment";
import { safeReturnTo } from "@/integrations/auth/guard";
import { refreshAccessToken } from "@/integrations/auth/sessionRefresh";

/**
 * The API client prefixes every request with `environment.serverUrl` (`/api` in prod where Traefik
 * strips it, empty in local dev). Returns that base path so the endpoint exemptions below match across
 * deploys, not just the local unprefixed shape.
 */
function apiBasePath(): string {
	try {
		return new URL(environment.serverUrl, window.location.origin).pathname.replace(/\/$/, "");
	} catch {
		return "";
	}
}

/**
 * Paths whose own 401 must NOT trigger a redirect-to-login. The `GET /user` identity probe
 * legitimately 401s while logged out (and is the query the login page itself reads), so reacting
 * to it would loop. `/auth/*` (login kickoff, logout, refresh) and the login page are likewise
 * excluded.
 */
function isExemptFromSessionExpiry(pathname: string, url: string): boolean {
	// `url` is the request URL (may be absolute, e.g. http://localhost:8080/user); match on its
	// pathname so the host/baseUrl is irrelevant.
	let requestPath = url;
	try {
		requestPath = new URL(url, window.location.origin).pathname;
	} catch {
		// Leave requestPath as-is for an unparseable URL.
	}
	// Strip the API base path so `/api/user` (prod) matches the same exemptions as `/user` (local dev).
	const base = apiBasePath();
	if (base && requestPath.startsWith(`${base}/`)) {
		requestPath = requestPath.slice(base.length);
	}
	if (requestPath === "/user") return true;
	if (requestPath.startsWith("/auth/")) return true;
	// Don't redirect when we're already on (or heading to) the login page.
	if (pathname === "/login" || pathname.startsWith("/auth/")) return true;
	return false;
}

/**
 * React to a mid-session cookie expiry: when an authenticated in-app request 401s, drop the cached
 * identity and bounce to /login carrying the current path as a (sanitised) returnTo. The `GET /user`
 * probe is exempt (it 401s when logged out by design), as are /auth/* and the login page, so this
 * never loops.
 *
 * Returns true when it handled the response (redirect scheduled), false otherwise. Exported for unit
 * testing; wired as a hey-api response interceptor in main.tsx.
 */
export function handlePossibleSessionExpiry(response: Response, queryClient: QueryClient): boolean {
	if (response.status !== 401) return false;

	const pathname = window.location.pathname;
	if (isExemptFromSessionExpiry(pathname, response.url)) return false;

	// A mid-session 401 is usually a just-lapsed or just-ROTATED cookie (an in-flight request can race
	// the proactive renewal). Try ONE silent refresh before logging the user out, so the common case
	// heals transparently instead of bouncing them to /login. Capture the path now (the recovery is
	// async). /auth/* is exempt above, so refresh's own response can't recurse here.
	void recoverOrLogout(queryClient, window.location.pathname + window.location.search);
	return true;
}

/**
 * If a refresh healed a 401 less than this ago and a non-exempt 401 is STILL happening, the cookie was
 * never the problem — another refresh won't help. Stop refreshing and log out instead, so a persistently
 * 401-ing request can't drive an endless refresh+invalidate loop. A genuine re-expiry this soon after a
 * successful rotation is implausible (the fresh cookie is good for far longer).
 */
const REFRESH_RECOVERY_COOLDOWN_MS = 10_000;

let recovery: Promise<void> | null = null;
let lastRefreshRecoveryAt = 0;

/**
 * Single-flight recovery: concurrent 401s (several requests racing one cookie rotation) collapse into
 * ONE refresh + ONE recovery side effect, instead of a per-request fan-out of refreshes and full-cache
 * invalidations.
 */
function recoverOrLogout(queryClient: QueryClient, currentPath: string): Promise<void> {
	if (!recovery) {
		recovery = doRecoverOrLogout(queryClient, currentPath).finally(() => {
			recovery = null;
		});
	}
	return recovery;
}

async function doRecoverOrLogout(queryClient: QueryClient, currentPath: string): Promise<void> {
	const recentlyRecovered = Date.now() - lastRefreshRecoveryAt < REFRESH_RECOVERY_COOLDOWN_MS;
	if (!recentlyRecovered && (await refreshAccessToken())) {
		// Session restored under a fresh cookie — refetch active queries so the UI recovers in place
		// (including the failed request's data) without a navigation.
		lastRefreshRecoveryAt = Date.now();
		void queryClient.invalidateQueries();
		return;
	}
	// Either genuinely signed out, or a 401 that survived a just-succeeded refresh (loop-breaker): drop
	// the cached identity so useAuth()/guards observe "logged out", then redirect to /login with a
	// sanitised returnTo.
	void queryClient.invalidateQueries({ queryKey: getCurrentUserQueryKey() });
	const target = new URL("/login", window.location.origin);
	target.searchParams.set("returnTo", safeReturnTo(currentPath));
	window.location.assign(target.toString());
}

/** Reset the module-level recovery state. Test-only (the singletons otherwise leak across test cases). */
export function __resetSessionRecoveryForTests(): void {
	recovery = null;
	lastRefreshRecoveryAt = 0;
}
