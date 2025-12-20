/**
 * Tool Registry Tests
 *
 * Verifies the tool registry creates all tools correctly
 * and that tools are consistent across the codebase.
 */

import { afterAll, beforeAll, describe, expect, it } from "vitest";
import {
	ACTIVITY_TOOL_NAMES,
	type ActivityToolName,
	type ActivityTools,
	createActivityTools,
} from "@/mentor/tools";
import { cleanupTestFixtures, createTestFixtures, type TestFixtures } from "../mocks";
import { execToolWithDefaults } from "../utils/tool-helpers";

describe("Tool Registry", () => {
	let fixtures: TestFixtures;
	let tools: ActivityTools;

	beforeAll(async () => {
		fixtures = await createTestFixtures();
		tools = createActivityTools({
			userId: fixtures.user.id,
			userLogin: fixtures.user.login,
			userName: fixtures.user.name ?? fixtures.user.login,
			workspaceId: fixtures.workspace.id,
		});
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	describe("createActivityTools", () => {
		it("should create all activity tools", () => {
			expect(Object.keys(tools)).toHaveLength(ACTIVITY_TOOL_NAMES.length);

			for (const toolName of ACTIVITY_TOOL_NAMES) {
				expect(tools).toHaveProperty(toolName);
				expect(tools[toolName]).toBeDefined();
			}
		});

		it("should create tools with execute functions", () => {
			for (const toolName of ACTIVITY_TOOL_NAMES) {
				const tool = tools[toolName];
				expect(tool.execute, `${toolName} should have execute`).toBeDefined();
				expect(typeof tool.execute).toBe("function");
			}
		});

		it("should create tools with descriptions", () => {
			for (const toolName of ACTIVITY_TOOL_NAMES) {
				const tool = tools[toolName];
				expect(tool.description, `${toolName} should have description`).toBeDefined();
				expect(tool.description?.length).toBeGreaterThan(10);
			}
		});

		it("should create tools with input schemas", () => {
			for (const toolName of ACTIVITY_TOOL_NAMES) {
				const tool = tools[toolName];
				expect(tool.inputSchema, `${toolName} should have inputSchema`).toBeDefined();
			}
		});
	});

	describe("ACTIVITY_TOOL_NAMES", () => {
		it("should contain expected tool names", () => {
			const expectedTools: ActivityToolName[] = [
				"getActivitySummary",
				"getPullRequests",
				"getIssues",
				"getAssignedWork",
				"getFeedbackReceived",
				"getReviewsGiven",
				"getSessionHistory",
				"getDocuments",
			];

			expect(ACTIVITY_TOOL_NAMES).toEqual(expectedTools);
		});

		it("should match keys of created tools", () => {
			const createdToolNames = Object.keys(tools).sort();
			const declaredNames = [...ACTIVITY_TOOL_NAMES].sort();

			expect(createdToolNames).toEqual(declaredNames);
		});
	});

	describe("Tool Type Safety", () => {
		it("should execute tools correctly via registry", async () => {
			// Execute using the type-safe helper
			const result = await execToolWithDefaults(tools.getActivitySummary);

			// The result should have the expected structure
			expect(result).toHaveProperty("user");
			expect(result).toHaveProperty("thisWeek");
		});
	});
});

describe("Tool Context Injection", () => {
	let fixtures: TestFixtures;

	beforeAll(async () => {
		fixtures = await createTestFixtures();
	});

	afterAll(async () => {
		await cleanupTestFixtures(fixtures);
	});

	it("should inject user context into all tools", async () => {
		const ctx = {
			userId: fixtures.user.id,
			userLogin: fixtures.user.login,
			userName: "Test Developer",
			workspaceId: fixtures.workspace.id,
		};

		const tools = createActivityTools(ctx);

		// Execute a tool and verify user context is in result
		const result = await execToolWithDefaults(tools.getActivitySummary);

		expect(result.user.login).toBe(fixtures.user.login);
	});

	it("should use different context for different tool instances", () => {
		const ctx1 = {
			userId: 1,
			userLogin: "user1",
			userName: "User One",
			workspaceId: 1,
		};

		const ctx2 = {
			userId: 2,
			userLogin: "user2",
			userName: "User Two",
			workspaceId: 2,
		};

		const tools1 = createActivityTools(ctx1);
		const tools2 = createActivityTools(ctx2);

		// These should be separate instances
		expect(tools1.getActivitySummary).not.toBe(tools2.getActivitySummary);
	});
});
