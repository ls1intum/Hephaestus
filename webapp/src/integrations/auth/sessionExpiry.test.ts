import { QueryClient } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { handlePossibleSessionExpiry } from "./sessionExpiry";
import { refreshAccessToken } from "./sessionRefresh";

// The recovery path calls the shared single-flight refresh; mock it to drive both outcomes.
vi.mock("./sessionRefresh", () => ({ refreshAccessToken: vi.fn() }));
const refreshMock = vi.mocked(refreshAccessToken);

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
beforeEach(() => refreshMock.mockReset());
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
