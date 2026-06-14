import { QueryClient } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { __resetSessionRecoveryForTests, handlePossibleSessionExpiry } from "./sessionExpiry";
import { refreshAccessToken } from "./sessionRefresh";

// The recovery path calls the shared single-flight refresh; mock it to drive both outcomes.
vi.mock("./sessionRefresh", () => ({ refreshAccessToken: vi.fn() }));
const refreshMock = vi.mocked(refreshAccessToken);

// Prod serves the API under /api (Traefik strips it); pin a base path so the exemptions are exercised
// the way they run in prod — the GET /api/user probe must be exempt exactly like /user is locally.
vi.mock("@/environment", () => ({ default: { serverUrl: "http://localhost/api" } }));

// jsdom's window.location is not directly assignable; replace it with a stub exposing `assign` plus
// the pathname/search/origin the handler reads.
function stubLocation(pathname: string, search = ""): { assigned: string[] } {
	const assigned: string[] = [];
	const stub = {
		assign: (url: string) => assigned.push(url),
		pathname,
		search,
		origin: "http://localhost",
	} as unknown as Location;
	Object.defineProperty(window, "location", { configurable: true, value: stub });
	return { assigned };
}

const realLocation = window.location;
beforeEach(() => {
	refreshMock.mockReset();
	__resetSessionRecoveryForTests();
});
afterEach(() => {
	Object.defineProperty(window, "location", { configurable: true, value: realLocation });
	vi.restoreAllMocks();
});

function makeQueryClient(): QueryClient {
	return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function res(status: number, url: string): Response {
	const r = new Response(null, { status });
	Object.defineProperty(r, "url", { value: url, configurable: true });
	return r;
}

// The 401 recovery is async (a silent refresh attempt); let its microtasks/timer settle.
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("handlePossibleSessionExpiry", () => {
	it("recovers a mid-session 401 via a silent refresh — no redirect", async () => {
		refreshMock.mockResolvedValue(true);
		const { assigned } = stubLocation("/w/acme/overview", "?tab=prs");
		const qc = makeQueryClient();
		const invalidate = vi.spyOn(qc, "invalidateQueries");

		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/workspaces/acme/practices"),
			qc,
		);

		expect(handled).toBe(true);
		await flush();
		expect(refreshMock).toHaveBeenCalledOnce();
		// Refresh succeeded → session restored, queries refetched, and the user is NOT bounced to /login.
		expect(assigned).toHaveLength(0);
		expect(invalidate).toHaveBeenCalled();
	});

	it("logs out to /login with sanitised returnTo when the 401 cannot be refreshed", async () => {
		refreshMock.mockResolvedValue(false);
		const { assigned } = stubLocation("/w/acme/overview", "?tab=prs");
		const qc = makeQueryClient();

		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/workspaces/acme/practices"),
			qc,
		);

		expect(handled).toBe(true);
		await flush();
		expect(refreshMock).toHaveBeenCalledOnce();
		expect(assigned).toHaveLength(1);
		const url = new URL(assigned[0]);
		expect(url.pathname).toBe("/login");
		expect(url.searchParams.get("returnTo")).toBe("/w/acme/overview?tab=prs");
	});

	it("logs out instead of refresh-looping when a 401 persists after a successful refresh", async () => {
		// Refresh always "succeeds" (cookie is valid), but the endpoint keeps 401ing — so the refresh is
		// not the cure. The loop-breaker must give up and log out rather than refresh+invalidate forever.
		refreshMock.mockResolvedValue(true);
		const { assigned } = stubLocation("/w/acme/overview");
		const qc = makeQueryClient();
		const url = "http://localhost:8080/workspaces/acme/practices";

		handlePossibleSessionExpiry(res(401, url), qc);
		await flush();
		expect(refreshMock).toHaveBeenCalledOnce();
		expect(assigned).toHaveLength(0); // first 401: refreshed, recovered in place

		// The same endpoint 401s again right after the "successful" refresh.
		handlePossibleSessionExpiry(res(401, url), qc);
		await flush();
		expect(refreshMock).toHaveBeenCalledOnce(); // NO second refresh — no storm
		expect(assigned).toHaveLength(1);
		expect(new URL(assigned[0]).pathname).toBe("/login");
	});

	it("collapses concurrent 401s into a single refresh", async () => {
		refreshMock.mockResolvedValue(true);
		stubLocation("/w/acme/overview");
		const qc = makeQueryClient();
		const url = "http://localhost:8080/workspaces/acme/practices";

		// Three requests 401 at once during a cookie rotation.
		handlePossibleSessionExpiry(res(401, url), qc);
		handlePossibleSessionExpiry(res(401, url), qc);
		handlePossibleSessionExpiry(res(401, url), qc);
		await flush();

		expect(refreshMock).toHaveBeenCalledOnce();
	});

	it("does NOT handle (or refresh) a 401 from the GET /user probe", () => {
		const { assigned } = stubLocation("/");
		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/user"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(refreshMock).not.toHaveBeenCalled();
		expect(assigned).toHaveLength(0);
	});

	it("does NOT handle a 401 from the GET /api/user probe (prod /api base path)", () => {
		// A logged-out visitor on the public landing (pathname "/") probes the session; the /api-prefixed
		// probe must be exempt, not drive the login redirect.
		const { assigned } = stubLocation("/");
		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost/api/user"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(refreshMock).not.toHaveBeenCalled();
		expect(assigned).toHaveLength(0);
	});

	it("does NOT handle a 401 from /api/auth/* endpoints (prod /api base path)", () => {
		const { assigned } = stubLocation("/");
		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost/api/auth/refresh"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(refreshMock).not.toHaveBeenCalled();
		expect(assigned).toHaveLength(0);
	});

	it("does NOT handle a 401 from /auth/* endpoints (refresh must not recurse)", () => {
		const { assigned } = stubLocation("/");
		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/auth/refresh"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(refreshMock).not.toHaveBeenCalled();
		expect(assigned).toHaveLength(0);
	});

	it("does NOT handle when already on /login (defence-in-depth against loops)", () => {
		const { assigned } = stubLocation("/login");
		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/workspaces/acme/practices"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(assigned).toHaveLength(0);
	});

	it("drops an open-redirect returnTo down to '/' when logging out", async () => {
		refreshMock.mockResolvedValue(false);
		const { assigned } = stubLocation("//evil.example.com");
		handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/workspaces/acme"),
			makeQueryClient(),
		);
		await flush();
		const url = new URL(assigned[0]);
		expect(url.searchParams.get("returnTo")).toBe("/");
	});

	it.each([200, 204, 403, 500])("ignores non-401 status %i", (status) => {
		const { assigned } = stubLocation("/dashboard");
		const handled = handlePossibleSessionExpiry(
			res(status, "http://localhost:8080/workspaces/acme"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(assigned).toHaveLength(0);
	});
});
