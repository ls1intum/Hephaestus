import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it } from "vitest";
import { currentUser } from "@/mocks/fixtures/auth";
import { server } from "@/mocks/server";
import { authClient, type CurrentUser, toUserProfile } from "./authClient";

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

// HTTP is intercepted at the MSW boundary (src/mocks/handlers.ts), not by stubbing fetch or
// internal modules, so these exercise the real fetch + boundary-narrowing path. Closes part of
// the F7 gating gap: the auth state machine had no behavioral coverage. `environment.serverUrl`
// defaults to http://localhost:8080; the MSW `*/user` wildcard matches regardless of host.
describe("authClient.fetchCurrentUser", () => {
	it("returns the narrowed user on a 200", async () => {
		const user = await authClient.fetchCurrentUser();
		expect(user).not.toBeNull();
		expect(user?.id).toBe(currentUser.id);
		expect(user?.displayName).toBe(currentUser.displayName);
		expect(user?.appRole).toBe("APP_ADMIN");
	});

	it("returns null (no throw) on a 401 — unauthenticated", async () => {
		server.use(http.get("*/user", () => new HttpResponse(null, { status: 401 })));
		await expect(authClient.fetchCurrentUser()).resolves.toBeNull();
	});

	it("returns null (no throw) on a 403", async () => {
		server.use(http.get("*/user", () => new HttpResponse(null, { status: 403 })));
		await expect(authClient.fetchCurrentUser()).resolves.toBeNull();
	});

	it("returns null on a 500 server error rather than surfacing the failure", async () => {
		server.use(http.get("*/user", () => new HttpResponse(null, { status: 500 })));
		await expect(authClient.fetchCurrentUser()).resolves.toBeNull();
	});

	it("returns null on a network error (fetch rejects)", async () => {
		server.use(http.get("*/user", () => HttpResponse.error()));
		await expect(authClient.fetchCurrentUser()).resolves.toBeNull();
	});

	it("returns null when a 200 body is malformed (boundary narrowing rejects partial shapes)", async () => {
		server.use(http.get("*/user", () => HttpResponse.json({ id: "not-a-number" })));
		await expect(authClient.fetchCurrentUser()).resolves.toBeNull();
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
		authClient.login("gitlab-lrz", "/settings/account");
		expect(assigned).toHaveLength(1);
		const url = new URL(assigned[0]);
		expect(`${url.origin}${url.pathname}`).toBe("http://localhost:8080/auth/login");
		expect(url.searchParams.get("provider")).toBe("gitlab-lrz");
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
