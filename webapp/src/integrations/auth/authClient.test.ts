import { afterEach, describe, expect, it } from "vitest";
import {
	applyStateChangingHeaders,
	authClient,
	type CurrentUser,
	toUserProfile,
} from "./authClient";

// Object-mother for a CurrentUser (server CurrentUserView). Tests override only what they assert.
function makeCurrentUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
	return {
		id: 7,
		displayName: "Ada Lovelace",
		appRole: "USER",
		status: "ACTIVE",
		roles: ["user"],
		hasGitLabIdentity: false,
		impersonating: false,
		...overrides,
	};
}

// `toUserProfile` adapts the server CurrentUserView into the legacy UserProfile shape the
// useAuth() consumers still read. The name-splitting and provider-id branches are the parts
// most likely to regress.
describe("toUserProfile", () => {
	it("maps the core CurrentUserView fields onto UserProfile", () => {
		const profile = toUserProfile(
			makeCurrentUser({
				id: 42,
				displayName: "Ada Lovelace",
				username: "ada",
				primaryEmail: "ada@example.com",
				roles: ["user", "admin"],
				identityProvider: "GITHUB",
				gitProviderId: "1001",
			}),
		);

		expect(profile.id).toBe("42");
		expect(profile.username).toBe("ada");
		expect(profile.email).toBe("ada@example.com");
		expect(profile.name).toBe("Ada Lovelace");
		expect(profile.firstName).toBe("Ada");
		expect(profile.lastName).toBe("Lovelace");
		expect(profile.roles).toEqual(["user", "admin"]);
		expect(profile.identityProvider).toBe("GITHUB");
	});

	it("maps gitProviderId to githubId only for GITHUB", () => {
		const profile = toUserProfile(
			makeCurrentUser({ identityProvider: "GITHUB", gitProviderId: "1001" }),
		);
		expect(profile.githubId).toBe("1001");
		expect(profile.gitlabId).toBeUndefined();
	});

	it("maps gitProviderId to gitlabId only for GITLAB", () => {
		const profile = toUserProfile(
			makeCurrentUser({ identityProvider: "GITLAB", gitProviderId: "2002" }),
		);
		expect(profile.gitlabId).toBe("2002");
		expect(profile.githubId).toBeUndefined();
	});

	it("falls back to username for the name when displayName is missing, and defaults missing fields", () => {
		// `displayName` is typed required on CurrentUser, but the server CurrentUserView types it
		// optional; exercise the `?? username` fallback by omitting it as the server can.
		const profile = toUserProfile(
			makeCurrentUser({
				displayName: undefined,
				username: "solo",
				primaryEmail: undefined,
				roles: undefined,
				identityProvider: undefined,
				gitProviderId: undefined,
			}),
		);
		expect(profile.name).toBe("solo");
		expect(profile.firstName).toBe("solo");
		expect(profile.lastName).toBe("");
		expect(profile.email).toBe("");
		expect(profile.roles).toEqual([]);
		expect(profile.identityProvider).toBeUndefined();
		expect(profile.githubId).toBeUndefined();
		expect(profile.gitlabId).toBeUndefined();
	});

	it("treats a single-word display name as the first name with empty last name", () => {
		const profile = toUserProfile(makeCurrentUser({ displayName: "Cher" }));
		expect(profile.firstName).toBe("Cher");
		expect(profile.lastName).toBe("");
	});
});

describe("authClient.login — returnTo forwarding (safeReturnTo guard)", () => {
	const realLocation = window.location;
	afterEach(() => {
		Object.defineProperty(window, "location", { configurable: true, value: realLocation });
	});

	// jsdom's window.location is not assignable directly; replace it with a stub exposing
	// `assign` so we can capture the redirect target without a real navigation.
	function stubLocation(): { assigned: string[] } {
		const assigned: string[] = [];
		const stub = { assign: (url: string) => assigned.push(url) } as unknown as Location;
		Object.defineProperty(window, "location", { configurable: true, value: stub });
		return { assigned };
	}

	it("redirects to the server kickoff carrying a safe same-origin returnTo", () => {
		const { assigned } = stubLocation();
		authClient.login("gitlab", "/settings/account");
		expect(assigned).toHaveLength(1);
		const url = new URL(assigned[0]);
		expect(`${url.origin}${url.pathname}`).toBe("http://localhost:8080/auth/login");
		expect(url.searchParams.get("provider")).toBe("gitlab");
		expect(url.searchParams.get("returnTo")).toBe("/settings/account");
	});

	it("drops an unsafe (open-redirect) returnTo down to '/'", () => {
		const { assigned } = stubLocation();
		authClient.login("github", "//evil.example.com/phish");
		const url = new URL(assigned[0]);
		expect(url.searchParams.get("returnTo")).toBe("/");
	});

	it("defaults the provider to github when no idpHint is given", () => {
		const { assigned } = stubLocation();
		authClient.login(undefined, "/dashboard");
		const url = new URL(assigned[0]);
		expect(url.searchParams.get("provider")).toBe("github");
		expect(url.searchParams.get("returnTo")).toBe("/dashboard");
	});
});

describe("applyStateChangingHeaders (app-wide CSRF + impersonation guard)", () => {
	// jsdom over http refuses to store a __Host- (Secure) cookie, so stub document.cookie's getter —
	// the helper only reads it via csrfHeaders().
	function setCookie(raw: string) {
		Object.defineProperty(document, "cookie", { configurable: true, get: () => raw });
	}
	function req(method: string): Request {
		return { method, headers: new Headers() } as unknown as Request;
	}

	afterEach(() => setCookie(""));

	it("adds the CSRF double-submit header on state-changing methods", () => {
		setCookie("__Host-XSRF-TOKEN=tok-123");
		const r = applyStateChangingHeaders(req("POST"), false);
		expect(r.headers.get("X-XSRF-TOKEN")).toBe("tok-123");
		expect(r.headers.get("X-Impersonation-Allow-Writes")).toBeNull();
	});

	it("sends NO CSRF header on safe methods", () => {
		setCookie("__Host-XSRF-TOKEN=tok-123");
		expect(applyStateChangingHeaders(req("GET"), true).headers.get("X-XSRF-TOKEN")).toBeNull();
		expect(applyStateChangingHeaders(req("HEAD"), true).headers.get("X-XSRF-TOKEN")).toBeNull();
	});

	it("adds X-Impersonation-Allow-Writes only when write-mode is on, and only on writes", () => {
		setCookie("__Host-XSRF-TOKEN=tok-123");
		expect(
			applyStateChangingHeaders(req("DELETE"), true).headers.get("X-Impersonation-Allow-Writes"),
		).toBe("true");
		expect(
			applyStateChangingHeaders(req("DELETE"), false).headers.get("X-Impersonation-Allow-Writes"),
		).toBeNull();
		expect(
			applyStateChangingHeaders(req("GET"), true).headers.get("X-Impersonation-Allow-Writes"),
		).toBeNull();
	});

	it("omits the CSRF header (fail-safe) when the token cookie is absent", () => {
		setCookie("");
		expect(applyStateChangingHeaders(req("POST"), false).headers.get("X-XSRF-TOKEN")).toBeNull();
	});
});
