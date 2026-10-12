import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";

import { listPracticeReviewsQueryKey } from "@/api/@tanstack/react-query.gen";
import type { AgentJob } from "@/api/types.gen";
import { reviewJob } from "@/components/admin/practice-reviews/fixtures";
import { server } from "@/mocks/server";

import { type ReviewRunController, useReviewRunController } from "./use-review-run-controller";

const jobId = "11111111-1111-1111-1111-111111111111";

describe("review result-processing retry", () => {
	it("polls pending processing and reads the review's delivery checks and row again once it settles", async () => {
		const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
		// The review's row as the sheet reads it, which the review settling has to renew.
		const rowKey = listPracticeReviewsQueryKey({
			path: { workspaceSlug: "demo" },
			query: { agentJobId: jobId, size: 1 },
		});
		const job: AgentJob = { ...reviewJob(jobId), deliveryStatus: "FAILED" };
		let checksRead = 0;
		server.use(
			http.get("*/workspaces/demo/agents/jobs/:jobId", () => HttpResponse.json(job)),
			http.post("*/workspaces/demo/agents/jobs/:jobId/delivery/retry", () => {
				job.deliveryStatus = "DELIVERED";
				return HttpResponse.json({ ...job, deliveryStatus: "PENDING" });
			}),
			http.get("*/workspaces/demo/practices/reviews/runs/:jobId/delivery-checks", () => {
				checksRead += 1;
				return HttpResponse.json([]);
			}),
			http.get("*/workspaces/demo/practices/trace/*", () => HttpResponse.json({}, { status: 404 })),
		);
		const { result, unmount } = renderHook(() => useReviewRunController("demo", jobId), {
			wrapper: ({ children }: { children: ReactNode }) => (
				<QueryClientProvider client={client}>{children}</QueryClientProvider>
			),
		});
		try {
			await waitFor(() =>
				expect(result.current.deliveryChecks).toStrictEqual({ status: "ready", checks: [] }),
			);
			const before = checksRead;
			act(() => result.current.onRetryResultProcessing());
			await waitFor(() => expect(jobOf(result.current)?.deliveryStatus).toBe("PENDING"));
			// Written after the retry's own invalidation, so only the settling can mark it.
			client.setQueryData(rowKey, { content: [] });
			await waitFor(() => expect(jobOf(result.current)?.deliveryStatus).toBe("DELIVERED"), {
				timeout: 7000,
			});
			await waitFor(() => expect(checksRead).toBeGreaterThan(before));
			expect(client.getQueryState(rowKey)?.isInvalidated).toBe(true);
		} finally {
			unmount();
			client.clear();
		}
	}, 10_000);
});

describe("an unknown review", () => {
	it("asks once and reports not found without retrying", async () => {
		// The client's own default retries: the hook has to opt out of them for a 404 itself.
		const client = new QueryClient({ defaultOptions: { queries: { retryDelay: 0 } } });
		let jobReads = 0;
		let usageReads = 0;
		server.use(
			http.get("*/workspaces/demo/agents/jobs/:jobId", () => {
				jobReads += 1;
				return HttpResponse.json({ status: 404 }, { status: 404 });
			}),
			http.get("*/workspaces/demo/agents/jobs/:jobId/usage", () => {
				usageReads += 1;
				return HttpResponse.json({ status: 404 }, { status: 404 });
			}),
			http.get("*/workspaces/demo/practices/reviews/runs/:jobId/delivery-checks", () =>
				HttpResponse.json([]),
			),
		);
		const { result, unmount } = renderHook(() => useReviewRunController("demo", jobId), {
			wrapper: ({ children }: { children: ReactNode }) => (
				<QueryClientProvider client={client}>{children}</QueryClientProvider>
			),
		});
		try {
			await waitFor(() => expect(result.current.job.status).toBe("error"));
			await waitFor(() => expect(result.current.usage.status).toBe("error"));
			expect(jobReads).toBe(1);
			expect(usageReads).toBe(1);
		} finally {
			unmount();
			client.clear();
		}
	});
});

function jobOf(controller: ReviewRunController): AgentJob | undefined {
	return controller.job.status === "ready" ? controller.job.job : undefined;
}
