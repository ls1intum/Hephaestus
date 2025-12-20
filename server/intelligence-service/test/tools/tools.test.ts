/**
 * Activity Tool Tests
 *
 * Tests the mentor's activity tools that enable AI-assisted reflection.
 * All tools are designed to be:
 * 1. Parallel-safe - can be called simultaneously
 * 2. User-context injected - never ask for username
 * 3. Workspace-scoped - only relevant repositories
 */

import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { cleanupTestFixtures, createTestFixtures, type TestFixtures } from "../mocks";
import { createToolOptions, execToolWithDefaults } from "../utils/tool-helpers";

describe("Document Tools", () => {
	let fixtures: TestFixtures;

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	describe("createDocument", () => {
		it("should create a tool with required properties", async () => {
			const { createDocument } = await import("@/mentor/tools/document-create.tool");
			const mockWriter = { write: vi.fn() };

			const tool = createDocument({
				dataStream: mockWriter as unknown as Parameters<typeof createDocument>[0]["dataStream"],
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			expect(tool.description).toBeDefined();
			expect(tool.execute).toBeDefined();
			expect(tool.inputSchema).toBeDefined();
		});
	});

	describe("updateDocument", () => {
		it("should create a tool with required properties", async () => {
			const { updateDocument } = await import("@/mentor/tools/document-update.tool");
			const mockWriter = { write: vi.fn() };

			const tool = updateDocument({
				dataStream: mockWriter as unknown as Parameters<typeof updateDocument>[0]["dataStream"],
			});

			expect(tool.description).toBeDefined();
			expect(tool.execute).toBeDefined();
			expect(tool.inputSchema).toBeDefined();
		});
	});
});

describe("Activity Tools - Input Defaults", () => {
	/**
	 * These tests verify that tools apply correct defaults when executed.
	 * We test execution behavior rather than schema parsing to ensure
	 * the full tool pipeline works with AI SDK's FlexibleSchema.
	 */
	let fixtures: TestFixtures;

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	const getToolContext = () => ({
		userId: fixtures.user.id,
		userLogin: fixtures.user.login,
		userName: fixtures.user.name ?? fixtures.user.login,
		workspaceId: fixtures.workspace.id,
	});

	it("getPullRequests should work with explicit input (strict mode)", async () => {
		const { createGetPullRequestsTool } = await import("@/mentor/tools");
		const tool = createGetPullRequestsTool(getToolContext());

		// Execute with explicit input - strict mode requires all fields
		const result = (await tool.execute?.(
			{ state: "all", limit: 10, sinceDays: 14 },
			createToolOptions(),
		)) as { user: string; count: number };

		expect(result.user).toBe(fixtures.user.login);
		expect(typeof result.count).toBe("number");
	});

	it("getPullRequests should respect custom state filter", async () => {
		const { createGetPullRequestsTool } = await import("@/mentor/tools");
		const tool = createGetPullRequestsTool(getToolContext());

		const result = (await tool.execute?.(
			{ state: "merged", limit: 5, sinceDays: 7 },
			createToolOptions(),
		)) as { count: number };

		// Should not throw and should return valid result
		expect(typeof result.count).toBe("number");
	});

	it("getIssues should work with explicit input (strict mode)", async () => {
		const { createGetIssuesTool } = await import("@/mentor/tools");
		const tool = createGetIssuesTool(getToolContext());

		const result = (await tool.execute?.({ state: "all", limit: 10 }, createToolOptions())) as {
			user: string;
			count: number;
		};

		expect(result.user).toBe(fixtures.user.login);
		expect(typeof result.count).toBe("number");
	});

	it("getActivitySummary should work with empty input", async () => {
		const { createGetActivitySummaryTool } = await import("@/mentor/tools");
		const tool = createGetActivitySummaryTool(getToolContext());

		const result = await execToolWithDefaults(tool);

		expect(result.user.login).toBe(fixtures.user.login);
		expect(result.thisWeek).toBeDefined();
	});
});

describe("Activity Tools - Integration", () => {
	let fixtures: TestFixtures;

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	describe("getActivitySummary", () => {
		it("should return structured summary with insights (Hattie feed-forward)", async () => {
			const { createGetActivitySummaryTool } = await import("@/mentor/tools");

			const tool = createGetActivitySummaryTool({
				userId: fixtures.user.id,
				userLogin: fixtures.user.login,
				userName: fixtures.user.name ?? fixtures.user.login,
				workspaceId: fixtures.workspace.id,
			});

			const result = await execToolWithDefaults(tool);

			expect(result.user.login).toBe(fixtures.user.login);
			// Week-over-week comparison for COPES internal feedback
			expect(result.thisWeek).toHaveProperty("prsMerged");
			expect(result.thisWeek).toHaveProperty("reviewsGiven");
			expect(result.lastWeek).toHaveProperty("prsMerged");
			// Feed-forward insights per Hattie
			expect(result.insights).toBeInstanceOf(Array);
			expect(result.suggestedReflectionTopics).toBeInstanceOf(Array);
		});
	});

	describe("getPullRequests", () => {
		it("should return array of PRs with user context", async () => {
			const { createGetPullRequestsTool } = await import("@/mentor/tools");

			const tool = createGetPullRequestsTool({
				userId: fixtures.user.id,
				userLogin: fixtures.user.login,
				userName: fixtures.user.name ?? fixtures.user.login,
				workspaceId: fixtures.workspace.id,
			});

			const result = await execToolWithDefaults(tool);

			expect(result.user).toBe(fixtures.user.login);
			expect(result.pullRequests).toBeInstanceOf(Array);
			expect(typeof result.count).toBe("number");
		});

		it("should filter by state", async () => {
			const { createGetPullRequestsTool } = await import("@/mentor/tools");

			const tool = createGetPullRequestsTool({
				userId: fixtures.user.id,
				userLogin: fixtures.user.login,
				userName: fixtures.user.name ?? fixtures.user.login,
				workspaceId: fixtures.workspace.id,
			});

			const openResult = (await tool.execute?.(
				{ state: "open", limit: 10, sinceDays: 30 },
				createToolOptions(),
			)) as { count: number };
			const mergedResult = (await tool.execute?.(
				{ state: "merged", limit: 10, sinceDays: 30 },
				createToolOptions(),
			)) as { count: number };

			expect(typeof openResult.count).toBe("number");
			expect(typeof mergedResult.count).toBe("number");
		});
	});

	describe("getIssues", () => {
		it("should return array of issues with user context", async () => {
			const { createGetIssuesTool } = await import("@/mentor/tools");

			const tool = createGetIssuesTool({
				userId: fixtures.user.id,
				userLogin: fixtures.user.login,
				userName: fixtures.user.name ?? fixtures.user.login,
				workspaceId: fixtures.workspace.id,
			});

			const result = await execToolWithDefaults(tool);

			expect(result.user).toBe(fixtures.user.login);
			expect(result.issues).toBeInstanceOf(Array);
		});
	});

	describe("getSessionHistory", () => {
		it("should return past mentor sessions", async () => {
			const { createGetSessionHistoryTool } = await import("@/mentor/tools");

			const tool = createGetSessionHistoryTool({
				userId: fixtures.user.id,
				userLogin: fixtures.user.login,
				userName: fixtures.user.name ?? fixtures.user.login,
				workspaceId: fixtures.workspace.id,
			});

			const result = await execToolWithDefaults(tool);

			expect(result.user).toBe(fixtures.user.login);
			expect(result.sessions).toBeInstanceOf(Array);
		});
	});

	describe("getDocuments", () => {
		it("should return documents with preview", async () => {
			const { createGetDocumentsTool } = await import("@/mentor/tools");

			const tool = createGetDocumentsTool({
				userId: fixtures.user.id,
				userLogin: fixtures.user.login,
				userName: fixtures.user.name ?? fixtures.user.login,
				workspaceId: fixtures.workspace.id,
			});

			const result = await execToolWithDefaults(tool);

			expect(result.user).toBe(fixtures.user.login);
			expect(result.documents).toBeInstanceOf(Array);
		});
	});
});

describe("Activity Tools - Parallel Execution", () => {
	let fixtures: TestFixtures;

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	it("should allow all tools to run in parallel without conflicts", async () => {
		const { createActivityTools } = await import("@/mentor/tools");

		const ctx = {
			userId: fixtures.user.id,
			userLogin: fixtures.user.login,
			userName: fixtures.user.name ?? fixtures.user.login,
			workspaceId: fixtures.workspace.id,
		};

		const tools = createActivityTools(ctx);
		const options = createToolOptions();

		// Execute all in parallel - this should not throw
		const results = await Promise.all([
			execToolWithDefaults(tools.getActivitySummary, options),
			execToolWithDefaults(tools.getPullRequests, options),
			execToolWithDefaults(tools.getIssues, options),
			execToolWithDefaults(tools.getReviewsGiven, options),
			execToolWithDefaults(tools.getFeedbackReceived, options),
			execToolWithDefaults(tools.getAssignedWork, options),
			execToolWithDefaults(tools.getSessionHistory, options),
			execToolWithDefaults(tools.getDocuments, options),
		]);

		// All should return valid results
		expect(results).toHaveLength(8);
		for (const result of results) {
			// getActivitySummary returns { user: { login, name } }, others return { user: string }
			expect(result).toHaveProperty("user");
		}
	});
});
