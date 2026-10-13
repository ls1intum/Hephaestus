import { focusManager, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@/mocks/server";
import { USER_VIEW_STORAGE_KEY } from "@/runtime/user-view/session";

import { useReviewTabCounts } from "./use-review-tab-counts";

const MINUTE = 60_000;
const initial = { runs: 10, results: 20, observations: 25, feedback: 30 };

function countReads(status = 200) {
	let totals = initial;
	const reads: URL[] = [];
	const endpoints = [
		["runs", ""],
		["results", "/results"],
		["observations", "/results/observations"],
		["feedback", "/feedback"],
	] as const;
	server.use(
		...endpoints.map(([kind, suffix]) =>
			http.get(`*/workspaces/:workspaceSlug/practices/reviews${suffix}`, ({ request }) => {
				reads.push(new URL(request.url));
				return HttpResponse.json(
					{ content: [], page: { size: 1, number: 0, totalElements: totals[kind], totalPages: 1 } },
					{ status },
				);
			}),
		),
	);
	return {
		reads,
		change: () => {
			totals = { runs: 11, results: 22, observations: 27, feedback: 33 };
		},
	};
}

function mountCounts() {
	const client = new QueryClient({ defaultOptions: { queries: { refetchOnWindowFocus: false } } });
	const hook = renderHook(
		() => useReviewTabCounts("demo", { subjectUserId: 7, practiceSlug: ["testing"] }),
		{
			wrapper: ({ children }: { children: ReactNode }) => (
				<QueryClientProvider client={client}>{children}</QueryClientProvider>
			),
		},
	);
	return { ...hook, client };
}

beforeEach(() => {
	vi.useFakeTimers({ shouldAdvanceTime: true });
	focusManager.setFocused(true);
});

afterEach(() => {
	cleanup();
	focusManager.setFocused(undefined);
	vi.useRealTimers();
});

describe("practice review tab totals", () => {
	it("refreshes successful totals while mounted and preserves each endpoint's shared scope", async () => {
		const wire = countReads();
		const { result, client } = mountCounts();
		try {
			await waitFor(() => expect(result.current).toStrictEqual(initial));
			wire.change();
			await act(async () => {
				await vi.advanceTimersByTimeAsync(MINUTE);
			});
			await waitFor(() =>
				expect(result.current).toStrictEqual({
					runs: 11,
					results: 22,
					observations: 27,
					feedback: 33,
				}),
			);
			expect(wire.reads).toHaveLength(8);
			for (const url of wire.reads) {
				expect(url.pathname).toContain("/workspaces/demo/practices/reviews");
				expect(url.searchParams.get("size")).toBe("1");
				expect(url.searchParams.get("practiceSlug")).toBe("testing");
			}
			expect(
				wire.reads
					.filter((url) => url.pathname.endsWith("/feedback"))
					.map((url) => url.searchParams.get("recipientUserId")),
			).toStrictEqual(["7", "7"]);
			expect(
				wire.reads
					.filter((url) => !url.pathname.endsWith("/feedback"))
					.map((url) => url.searchParams.get("subjectUserId")),
			).toStrictEqual(["7", "7", "7", "7", "7", "7"]);
		} finally {
			cleanup();
			client.clear();
		}
	});

	it("keeps existing totals without polling a hidden page and refreshes once visible", async () => {
		const wire = countReads();
		const { result, client } = mountCounts();
		try {
			await waitFor(() => expect(result.current).toStrictEqual(initial));
			wire.change();
			focusManager.setFocused(false);
			await act(async () => {
				await vi.advanceTimersByTimeAsync(MINUTE);
			});
			expect(wire.reads).toHaveLength(4);
			expect(result.current).toStrictEqual(initial);
			focusManager.setFocused(true);
			await act(async () => {
				await vi.advanceTimersByTimeAsync(MINUTE);
			});
			await waitFor(() => expect(result.current.feedback).toBe(33));
		} finally {
			cleanup();
			client.clear();
		}
	});

	it.each([
		{ status: 200, expected: initial },
		{
			status: 500,
			expected: {
				runs: undefined,
				results: undefined,
				observations: undefined,
				feedback: undefined,
			},
		},
	])(
		"does not repeat audited user-view reads after a $status response",
		async ({ status, expected }) => {
			sessionStorage.setItem(
				USER_VIEW_STORAGE_KEY,
				JSON.stringify({
					operatorAccountId: 1,
					workspaceSlug: "demo",
					workspaceName: "Demo",
					userId: 7,
					login: "developer",
					name: "Developer",
					hasAccount: true,
					reason: "Inspect own synthetic fixture",
				}),
			);
			const wire = countReads(status);
			const { result, client } = mountCounts();
			try {
				await waitFor(() => expect(wire.reads).toHaveLength(4));
				await waitFor(() => expect(client.isFetching()).toBe(0));
				expect(result.current).toStrictEqual(expected);
				await act(async () => {
					await vi.advanceTimersByTimeAsync(MINUTE * 2);
				});
				expect(wire.reads).toHaveLength(4);
			} finally {
				cleanup();
				client.clear();
			}
		},
	);
});
