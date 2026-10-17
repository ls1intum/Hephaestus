import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { expect, it } from "vitest";

import { server } from "@/mocks/server";

import { usePracticeProfileReviews } from "./use-practice-profile-reviews";

it.each([401, 403, 404, 409, 428])(
	"settles a profile review refusal after the first HTTP %s response",
	async (status) => {
		const client = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } });
		let requests = 0;
		server.use(
			http.get("*/workspaces/demo/practice-profile/reviews/:reviewId", () => {
				requests += 1;
				return HttpResponse.json({ status }, { status });
			}),
		);
		const { result, unmount } = renderHook(
			() => usePracticeProfileReviews({ workspaceSlug: "demo", listOpen: false, reviewId: "7" }),
			{
				wrapper: ({ children }: { children: ReactNode }) => (
					<QueryClientProvider client={client}>{children}</QueryClientProvider>
				),
			},
		);
		try {
			await waitFor(() => expect(result.current.open.status).toBe("error"));
			expect(requests).toBe(1);
		} finally {
			unmount();
			client.clear();
		}
	},
);
