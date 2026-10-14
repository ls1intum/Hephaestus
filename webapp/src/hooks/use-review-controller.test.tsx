import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";

import { getReviewOverviewQueryKey, listReviewsQueryKey } from "@/api/@tanstack/react-query.gen";
import type { ReviewDetail } from "@/api/types.gen";
import { reviewDetailCompleted } from "@/components/admin/practice-reviews/fixtures";
import { server } from "@/mocks/server";

import { type ReviewController, useReviewController } from "./use-review-controller";

const reviewId = reviewDetailCompleted.id;

function withProcessing(review: ReviewDetail, processingStatus: "PENDING" | "DONE"): ReviewDetail {
	return {
		...review,
		runtime: {
			jobs: (review.runtime?.jobs ?? []).map((job) => ({ ...job, processingStatus })),
		},
	};
}

function render(client: QueryClient) {
	return renderHook(() => useReviewController("demo", reviewId), {
		wrapper: ({ children }: { children: ReactNode }) => (
			<QueryClientProvider client={client}>{children}</QueryClientProvider>
		),
	});
}

function processingOf(controller: ReviewController): string | undefined {
	return controller.review.status === "ready"
		? controller.review.review.runtime?.jobs[0]?.processingStatus
		: undefined;
}

describe("a review whose results are still processed", () => {
	it("polls until processing ends, then reads its feedback checks and row again", async () => {
		const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		const rowKey = listReviewsQueryKey({
			path: { workspaceSlug: "demo" },
			query: { reviewId, size: 1 },
		});
		let reviewReads = 0;
		let checksRead = 0;
		server.use(
			http.get(
				"*/workspaces/demo/practices/reviews/:reviewId",
				() => {
					reviewReads += 1;
					return HttpResponse.json(withProcessing(reviewDetailCompleted, "PENDING"));
				},
				{ once: true },
			),
			http.get("*/workspaces/demo/practices/reviews/:reviewId", () => {
				reviewReads += 1;
				return HttpResponse.json(withProcessing(reviewDetailCompleted, "DONE"));
			}),
			http.get("*/workspaces/demo/practices/reviews/:reviewId/feedback-checks", () => {
				checksRead += 1;
				return HttpResponse.json([]);
			}),
		);
		const { result, unmount } = render(client);
		try {
			await waitFor(() =>
				expect(result.current.feedbackChecks).toStrictEqual({ status: "ready", checks: [] }),
			);
			await waitFor(() => expect(processingOf(result.current)).toBe("PENDING"));
			const before = checksRead;
			client.setQueryData(rowKey, { content: [] });
			await waitFor(() => expect(processingOf(result.current)).toBe("DONE"), { timeout: 7000 });
			await waitFor(() => expect(checksRead).toBeGreaterThan(before));
			expect(client.getQueryState(rowKey)?.isInvalidated).toBe(true);
		} finally {
			unmount();
			client.clear();
		}
	}, 10_000);
});

describe("cancelling a review", () => {
	it("reads every review read again, since the answer is the review's summary", async () => {
		const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		const overviewKey = getReviewOverviewQueryKey({ path: { workspaceSlug: "demo" } });
		let cancelled = 0;
		server.use(
			http.get("*/workspaces/demo/practices/reviews/:reviewId", () =>
				HttpResponse.json(reviewDetailCompleted),
			),
			http.get("*/workspaces/demo/practices/reviews/:reviewId/feedback-checks", () =>
				HttpResponse.json([]),
			),
			http.post("*/workspaces/demo/practices/reviews/:reviewId/cancel", () => {
				cancelled += 1;
				return HttpResponse.json(reviewDetailCompleted);
			}),
		);
		const { result, unmount } = render(client);
		try {
			await waitFor(() => expect(result.current.review.status).toBe("ready"));
			client.setQueryData(overviewKey, {});
			act(() => result.current.onCancel());
			await waitFor(() => expect(cancelled).toBe(1));
			await waitFor(() => expect(client.getQueryState(overviewKey)?.isInvalidated).toBe(true));
		} finally {
			unmount();
			client.clear();
		}
	});
});

describe("an unknown review", () => {
	it("asks once and reports not found without retrying", async () => {
		// Disable retry for a 404; repeating a denied or removed review read cannot recover it.
		const client = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } });
		let reviewReads = 0;
		server.use(
			http.get("*/workspaces/demo/practices/reviews/:reviewId", () => {
				reviewReads += 1;
				return HttpResponse.json({ status: 404 }, { status: 404 });
			}),
			http.get("*/workspaces/demo/practices/reviews/:reviewId/feedback-checks", () =>
				HttpResponse.json({ status: 404 }, { status: 404 }),
			),
		);
		const { result, unmount } = render(client);
		try {
			await waitFor(() => expect(result.current.review.status).toBe("error"));
			await waitFor(() => expect(result.current.feedbackChecks.status).toBe("error"));
			expect(reviewReads).toBe(1);
		} finally {
			unmount();
			client.clear();
		}
	});
});
