import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { AuthProvider } from "@/integrations/auth/AuthContext";
import { server } from "@/mocks/server";
import { useFeatureFlags } from "./hooks";

function wrapper(queryClient: QueryClient) {
	return ({ children }: { children: ReactNode }) => (
		<QueryClientProvider client={queryClient}>
			<AuthProvider>{children}</AuthProvider>
		</QueryClientProvider>
	);
}

describe("useFeatureFlags", () => {
	// A flag the server spells with something other than `true`/`false` is one flag going wrong, not
	// the flag service going down: the gate it guards closes, and every other flag still answers.
	it("reads a flag the server did not send as a boolean as off, and keeps the rest of the map", async () => {
		server.use(
			http.get("*/user/features", () =>
				HttpResponse.json({ ADMIN: "true", MENTOR_ACCESS: true, NOTIFICATION_ACCESS: false }),
			),
		);

		const { result } = renderHook(() => useFeatureFlags(), {
			wrapper: wrapper(new QueryClient({ defaultOptions: { queries: { retry: false } } })),
		});

		await waitFor(() => expect(result.current.flags?.ADMIN).toBe(false));
		expect(result.current.flags?.MENTOR_ACCESS).toBe(true);
	});
});
