import type { QueryClient } from "@tanstack/react-query";
import { getCurrentUserQueryKey } from "@/api/@tanstack/react-query.gen";
import { safeReturnTo } from "@/integrations/auth/guard";

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

	// Invalidate the shared identity query so useAuth()/guards observe "logged out" even if the
	// reload is intercepted/slow.
	void queryClient.invalidateQueries({ queryKey: getCurrentUserQueryKey() });

	const returnTo = safeReturnTo(window.location.pathname + window.location.search);
	const target = new URL("/login", window.location.origin);
	target.searchParams.set("returnTo", returnTo);
	window.location.assign(target.toString());
	return true;
}
