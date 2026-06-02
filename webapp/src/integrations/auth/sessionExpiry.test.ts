import { QueryClient } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { handlePossibleSessionExpiry } from "./sessionExpiry";

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

describe("handlePossibleSessionExpiry", () => {
	it("redirects to /login with sanitised returnTo on a 401 from an in-app endpoint", () => {
		const { assigned } = stubLocation("/w/acme/overview", "?tab=prs");
		const qc = makeQueryClient();
		const invalidate = vi.spyOn(qc, "invalidateQueries");

		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/workspaces/acme/practices"),
			qc,
		);

		expect(handled).toBe(true);
		expect(invalidate).toHaveBeenCalledOnce();
		expect(assigned).toHaveLength(1);
		const url = new URL(assigned[0]);
		expect(url.pathname).toBe("/login");
		expect(url.searchParams.get("returnTo")).toBe("/w/acme/overview?tab=prs");
	});

	it("does NOT redirect (no loop) on a 401 from the GET /user probe", () => {
		const { assigned } = stubLocation("/");
		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/user"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(assigned).toHaveLength(0);
	});

	it("does NOT redirect on a 401 from /auth/* endpoints", () => {
		const { assigned } = stubLocation("/");
		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/auth/refresh"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(assigned).toHaveLength(0);
	});

	it("does NOT redirect when already on /login (defence-in-depth against loops)", () => {
		const { assigned } = stubLocation("/login");
		const handled = handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/workspaces/acme/practices"),
			makeQueryClient(),
		);
		expect(handled).toBe(false);
		expect(assigned).toHaveLength(0);
	});

	it("drops an open-redirect returnTo down to '/' via safeReturnTo", () => {
		const { assigned } = stubLocation("//evil.example.com");
		handlePossibleSessionExpiry(
			res(401, "http://localhost:8080/workspaces/acme"),
			makeQueryClient(),
		);
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
