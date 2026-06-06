import type { QueryClient } from "@tanstack/react-query";
import { getCurrentUserQueryKey } from "@/api/@tanstack/react-query.gen";
import { safeReturnTo } from "@/integrations/auth/guard";
import { refreshAccessToken } from "@/integrations/auth/sessionRefresh";

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

async function recoverOrLogout(queryClient: QueryClient, currentPath: string): Promise<void> {
	if (await refreshAccessToken()) {
		// Session restored under a fresh cookie — refetch active queries so the UI recovers in place
		// (including the failed request's data) without a navigation.
		void queryClient.invalidateQueries();
		return;
	}
	// Genuinely signed out: drop the cached identity so useAuth()/guards observe "logged out", then
	// redirect to /login carrying a sanitised returnTo.
	void queryClient.invalidateQueries({ queryKey: getCurrentUserQueryKey() });
	const target = new URL("/login", window.location.origin);
	target.searchParams.set("returnTo", safeReturnTo(currentPath));
	window.location.assign(target.toString());
}
