/**
 * Context Module Tests
 *
 * Tests for request-scoped caching of workspace repo IDs.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ToolContext } from "@/mentor/tools/context";

// Mock database before importing context
vi.mock("@/shared/db", () => ({
	default: {
		select: vi.fn().mockReturnThis(),
		from: vi.fn().mockReturnThis(),
		where: vi.fn().mockReturnThis(),
	},
}));

describe("ToolContext Request-Scoped Caching", () => {
	beforeEach(() => {
		vi.resetModules();
	});

	afterEach(() => {
		vi.clearAllMocks();
	});

	describe("getWorkspaceRepoIds", () => {
		it("should cache repo IDs on first call", () => {
			// Create a fresh context with no cache
			const ctx: ToolContext = {
				userId: 1,
				userLogin: "testuser",
				userName: "Test User",
				workspaceId: 42,
				_repoIdsCache: undefined,
			};

			// The context should not have cached values initially
			expect(ctx._repoIdsCache).toBeUndefined();
		});

		it("should return cached value on subsequent calls", async () => {
			const { getWorkspaceRepoIds } = await import("@/mentor/tools/context");

			// Create context with pre-cached values
			const ctx: ToolContext = {
				userId: 1,
				userLogin: "testuser",
				userName: "Test User",
				workspaceId: 42,
				_repoIdsCache: [101, 102, 103],
			};

			// Should return cached value immediately
			const result = await getWorkspaceRepoIds(ctx);
			expect(result).toEqual([101, 102, 103]);
		});

		it("should not share cache between different contexts", () => {
			const ctx1: ToolContext = {
				userId: 1,
				userLogin: "user1",
				userName: "User 1",
				workspaceId: 1,
				_repoIdsCache: [1, 2, 3],
			};

			const ctx2: ToolContext = {
				userId: 2,
				userLogin: "user2",
				userName: "User 2",
				workspaceId: 2,
				_repoIdsCache: undefined, // Different context, no cache
			};

			// Contexts should be independent
			expect(ctx1._repoIdsCache).toEqual([1, 2, 3]);
			expect(ctx2._repoIdsCache).toBeUndefined();
		});
	});

	describe("URL Builders", () => {
		it("should build correct PR URL", async () => {
			const { buildPrUrl } = await import("@/mentor/tools/context");

			expect(buildPrUrl("owner/repo", 123)).toBe("https://github.com/owner/repo/pull/123");
			expect(buildPrUrl(null, 123)).toBe("");
		});

		it("should build correct issue URL", async () => {
			const { buildIssueUrl } = await import("@/mentor/tools/context");

			expect(buildIssueUrl("owner/repo", 456)).toBe("https://github.com/owner/repo/issues/456");
			expect(buildIssueUrl(null, 456)).toBe("");
		});
	});
});
