import environment from "@/environment";

/**
 * Cookie-session auth client (replaces the former keycloak-js singleton; ADR 0017).
 *
 * <p>The browser holds a {@code __Host-HEPHAESTUS_AT} HttpOnly cookie minted by the server
 * after the OAuth login dance — the SPA never sees a token. Identity is read from
 * {@code GET /user}; login/link are top-level redirects to the server's {@code /auth/login}
 * kickoff; logout is a CSRF-protected POST.
 */

/** Shape returned by the server's {@code GET /user} (CurrentUserView). */
export interface CurrentUser {
	id: number;
	displayName: string;
	primaryEmail?: string | null;
	appRole: string;
	status: string;
	impersonating: boolean;
	impersonatorId?: number | null;
	username?: string | null;
	avatarUrl?: string | null;
	profileUrl?: string | null;
	identityProvider?: string | null;
	gitProviderId?: string | null;
	hasGitLabIdentity: boolean;
	roles: string[];
}

/** Profile shape preserved for the {@code useAuth().userProfile} consumers. */
export interface UserProfile {
	id: string;
	username: string;
	firstName: string;
	lastName: string;
	email: string;
	name: string;
	roles: string[];
	githubId?: string;
	gitlabId?: string;
	identityProvider?: string;
}

const serverUrl = () => environment.serverUrl.replace(/\/$/, "");

/** Read a cookie value by name (used for the CSRF double-submit token). */
function readCookie(name: string): string | undefined {
	const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
	return match ? decodeURIComponent(match[1]) : undefined;
}

/** The CSRF header/cookie pair Spring Security's CookieCsrfTokenRepository expects. */
export function csrfHeaders(): Record<string, string> {
	const token = readCookie("XSRF-TOKEN");
	return token ? { "X-XSRF-TOKEN": token } : {};
}

function safeReturnTo(): string {
	const path = window.location.pathname + window.location.search;
	return path.startsWith("/") && !path.startsWith("//") ? path : "/";
}

export const authClient = {
	/** Fetch the current user, or null when unauthenticated (401). */
	async fetchCurrentUser(): Promise<CurrentUser | null> {
		try {
			const res = await fetch(`${serverUrl()}/user`, {
				method: "GET",
				credentials: "include",
				headers: { Accept: "application/json" },
			});
			if (res.status === 401 || res.status === 403) {
				return null;
			}
			if (!res.ok) {
				return null;
			}
			return (await res.json()) as CurrentUser;
		} catch {
			return null;
		}
	},

	/** Begin the OAuth login flow against the given provider (default: github). */
	login(idpHint?: string): void {
		const provider = idpHint && idpHint.length > 0 ? idpHint : "github";
		const url = new URL(`${serverUrl()}/auth/login`);
		url.searchParams.set("provider", provider);
		url.searchParams.set("returnTo", safeReturnTo());
		window.location.assign(url.toString());
	},

	/** Begin a link flow — attaches a new identity to the currently authenticated account. */
	linkAccount(providerAlias: string): void {
		const url = new URL(`${serverUrl()}/auth/login`);
		url.searchParams.set("provider", providerAlias);
		url.searchParams.set("mode", "link");
		url.searchParams.set("returnTo", safeReturnTo());
		window.location.assign(url.toString());
	},

	/** Revoke the session server-side, then return home. */
	async logout(): Promise<void> {
		try {
			await fetch(`${serverUrl()}/auth/logout`, {
				method: "POST",
				credentials: "include",
				headers: { ...csrfHeaders() },
			});
		} finally {
			window.location.assign("/");
		}
	},
};

/** Build the preserved {@link UserProfile} from a {@link CurrentUser}. */
export function toUserProfile(user: CurrentUser): UserProfile {
	const name = user.displayName ?? user.username ?? "";
	const [firstName, ...rest] = name.split(" ");
	return {
		id: String(user.id),
		username: user.username ?? "",
		firstName: firstName ?? name,
		lastName: rest.join(" "),
		email: user.primaryEmail ?? "",
		name,
		roles: user.roles ?? [],
		githubId: user.identityProvider === "GITHUB" ? (user.gitProviderId ?? undefined) : undefined,
		gitlabId: user.identityProvider === "GITLAB" ? (user.gitProviderId ?? undefined) : undefined,
		identityProvider: user.identityProvider ?? undefined,
	};
}
