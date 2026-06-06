import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { server } from "@/mocks/server";
import { useSessionKeepAlive } from "./useSessionKeepAlive";

// Real behaviour, no mocks of our own code: the hook fetches /user through the actual client + query
// layer, schedules a real setTimeout against the returned expiry, and POSTs /auth/refresh — we just
// observe the requests the keep-alive makes. REFRESH_SKEW_MS is 60s, so a token that expires ~61s out is
// due for renewal in ~1s; real timers keep the test honest while staying quick.

function wrapper(queryClient: QueryClient) {
	return ({ children }: { children: ReactNode }) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);
}

function userPayload(expiresInSec: number) {
	return {
		id: 1,
		displayName: "Active Annie",
		appRole: "USER",
		status: "ACTIVE",
		impersonating: false,
		linkedProviders: [],
		roles: [],
		accessTokenExpiresAt: Math.floor(Date.now() / 1000) + expiresInSec,
	};
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

describe("useSessionKeepAlive", () => {
	it("proactively rotates the access cookie before it expires while the user is active", async () => {
		let userCalls = 0;
		let refreshCalls = 0;
		server.use(
			http.get("*/user", () => {
				userCalls += 1;
				// First load: expiry ~61s out → renewal due in ~1s. After the first rotation the refetched
				// /user reports a far-future expiry, so the scheduler settles and the test sees exactly one
				// proactive refresh (proving it renews early — and doesn't storm).
				return HttpResponse.json(userPayload(userCalls <= 1 ? 61 : 3600));
			}),
			http.post("*/auth/refresh", () => {
				refreshCalls += 1;
				return new HttpResponse(null, { status: 204 });
			}),
		);

		const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		renderHook(() => useSessionKeepAlive(), { wrapper: wrapper(queryClient) });

		await waitFor(() => expect(refreshCalls).toBe(1), { timeout: 3000 });
		// Re-read /user afterwards to pick up the new expiry (initial load + post-refresh refetch).
		expect(userCalls).toBeGreaterThanOrEqual(2);
	});

	it("does NOT keep renewing an idle session — once the user stops interacting, the token lapses", async () => {
		let refreshCalls = 0;
		server.use(
			// Every load reports the same short expiry, so a renewal is due ~1s into every cycle.
			http.get("*/user", () => HttpResponse.json(userPayload(61))),
			http.post("*/auth/refresh", () => {
				refreshCalls += 1;
				return new HttpResponse(null, { status: 204 });
			}),
		);

		const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		// No interaction is ever dispatched. Mounting counts as activity (we never drop a just-loaded
		// session), so cycle 1 renews once — then, with no further interaction, cycle 2 must be skipped
		// and the session left to expire. This is the OWASP idle timeout.
		renderHook(() => useSessionKeepAlive(), { wrapper: wrapper(queryClient) });

		// Cycle 1 (seeded by mount-activity) renews exactly once.
		await waitFor(() => expect(refreshCalls).toBe(1), { timeout: 3000 });
		// Wait well past cycle 2's due time (~1s after the cycle-1 renewal): with zero interaction it must
		// NOT renew again.
		await sleep(1800);
		expect(refreshCalls).toBe(1);
	});
});
