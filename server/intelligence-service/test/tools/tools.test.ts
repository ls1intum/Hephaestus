/**
 * Document Tool Tests
 *
 * Tests the actual tool implementations that the mentor uses.
 * These are critical because tool failures break the AI experience.
 */

import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { cleanupTestFixtures, createTestFixtures, type TestFixtures } from "../mocks";

describe("Document Tools", () => {
	let fixtures: TestFixtures;

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	describe("createDocument tool factory", () => {
		it("should create a tool with execute function", async () => {
			const { createDocument } = await import("@/mentor/tools/document-create.tool");

			// Mock dataStream writer
			const mockWriter = {
				write: vi.fn(),
			};

			const tool = createDocument({
				dataStream: mockWriter as unknown as Parameters<typeof createDocument>[0]["dataStream"],
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			// AI SDK tool() returns an object with execute and description
			expect(tool).toBeDefined();
			expect(tool.execute).toBeDefined();
			expect(typeof tool.execute).toBe("function");
		});

		it("should have description on the tool", async () => {
			const { createDocument } = await import("@/mentor/tools/document-create.tool");

			const mockWriter = { write: vi.fn() };
			const tool = createDocument({
				dataStream: mockWriter as unknown as Parameters<typeof createDocument>[0]["dataStream"],
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			// Tool has description
			expect(tool.description).toBeDefined();
			expect(tool.description?.length).toBeGreaterThan(10);
		});

		it("should have inputSchema defined", async () => {
			const { createDocument } = await import("@/mentor/tools/document-create.tool");

			const mockWriter = { write: vi.fn() };
			const tool = createDocument({
				dataStream: mockWriter as unknown as Parameters<typeof createDocument>[0]["dataStream"],
				workspaceId: fixtures.workspace.id,
				userId: fixtures.user.id,
			});

			expect(tool.inputSchema).toBeDefined();
		});
	});

	describe("updateDocument tool factory", () => {
		it("should create a tool with execute function", async () => {
			const { updateDocument } = await import("@/mentor/tools/document-update.tool");

			const mockWriter = { write: vi.fn() };
			const tool = updateDocument({
				dataStream: mockWriter as unknown as Parameters<typeof updateDocument>[0]["dataStream"],
			});

			expect(tool).toBeDefined();
			expect(tool.execute).toBeDefined();
			expect(typeof tool.execute).toBe("function");
		});
	});
});

describe("Issue Tools", () => {
	describe("getIssues tool", () => {
		it("should be a valid tool definition", async () => {
			const { getIssues } = await import("@/mentor/tools/issue-list.tool");

			expect(getIssues).toBeDefined();
			expect(getIssues.description).toBeDefined();
			expect(getIssues.execute).toBeDefined();
			expect(getIssues.inputSchema).toBeDefined();
		});
	});

	describe("getIssueDetails tool", () => {
		it("should be a valid tool definition", async () => {
			const { getIssueDetails } = await import("@/mentor/tools/issues.tool");

			expect(getIssueDetails).toBeDefined();
			expect(getIssueDetails.description).toBeDefined();
			expect(getIssueDetails.execute).toBeDefined();
			expect(getIssueDetails.inputSchema).toBeDefined();
		});
	});
});

describe("Pull Request Tools", () => {
	describe("getPullRequests tool", () => {
		it("should be a valid tool definition", async () => {
			const { getPullRequests } = await import("@/mentor/tools/pull-request-list.tool");

			expect(getPullRequests).toBeDefined();
			expect(getPullRequests.description).toBeDefined();
			expect(getPullRequests.execute).toBeDefined();
			expect(getPullRequests.inputSchema).toBeDefined();
		});
	});

	describe("getPullRequestDetails tool", () => {
		it("should be a valid tool definition", async () => {
			const { getPullRequestDetails } = await import("@/mentor/tools/pull-request.tool");

			expect(getPullRequestDetails).toBeDefined();
			expect(getPullRequestDetails.description).toBeDefined();
			expect(getPullRequestDetails.execute).toBeDefined();
			expect(getPullRequestDetails.inputSchema).toBeDefined();
		});
	});

	describe("getPullRequestBadPractices tool", () => {
		it("should be a valid tool definition", async () => {
			const { getPullRequestBadPractices } = await import(
				"@/mentor/tools/pull-request-review.tool"
			);

			expect(getPullRequestBadPractices).toBeDefined();
			expect(getPullRequestBadPractices.description).toBeDefined();
			expect(getPullRequestBadPractices.execute).toBeDefined();
			expect(getPullRequestBadPractices.inputSchema).toBeDefined();
		});
	});
});

describe("Tool Schema Validation", () => {
	it("all tools should have non-empty descriptions", async () => {
		const [
			{ getIssues },
			{ getIssueDetails },
			{ getPullRequests },
			{ getPullRequestDetails },
			{ getPullRequestBadPractices },
		] = await Promise.all([
			import("@/mentor/tools/issue-list.tool"),
			import("@/mentor/tools/issues.tool"),
			import("@/mentor/tools/pull-request-list.tool"),
			import("@/mentor/tools/pull-request.tool"),
			import("@/mentor/tools/pull-request-review.tool"),
		]);

		const tools = [
			getIssues,
			getIssueDetails,
			getPullRequests,
			getPullRequestDetails,
			getPullRequestBadPractices,
		];

		for (const tool of tools) {
			expect(tool.description).toBeDefined();
			expect(tool.description?.length).toBeGreaterThan(10);
		}
	});

	it("all tools should have execute functions", async () => {
		const [
			{ getIssues },
			{ getIssueDetails },
			{ getPullRequests },
			{ getPullRequestDetails },
			{ getPullRequestBadPractices },
		] = await Promise.all([
			import("@/mentor/tools/issue-list.tool"),
			import("@/mentor/tools/issues.tool"),
			import("@/mentor/tools/pull-request-list.tool"),
			import("@/mentor/tools/pull-request.tool"),
			import("@/mentor/tools/pull-request-review.tool"),
		]);

		const tools = [
			getIssues,
			getIssueDetails,
			getPullRequests,
			getPullRequestDetails,
			getPullRequestBadPractices,
		];

		for (const tool of tools) {
			expect(tool.execute).toBeDefined();
			expect(typeof tool.execute).toBe("function");
		}
	});
});
