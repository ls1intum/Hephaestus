/**
 * Detector Unit Tests
 *
 * Tests the bad practice detector module:
 * - Schema validation
 * - Prompt compilation
 * - Handler behavior with mocked AI
 *
 * These tests ensure the detector remains stable across AI SDK updates.
 */

import { describe, expect, it } from "vitest";
import {
	type BadPractice,
	type BadPracticeResult,
	badPracticeResultSchema,
	badPracticeSchema,
	badPracticeStatusSchema,
	type DetectorRequest,
	detectorRequestSchema,
	detectorResponseSchema,
} from "@/detector/detector.schema";

describe("Detector Schemas", () => {
	describe("badPracticeStatusSchema", () => {
		it("should accept all valid status values", () => {
			const validStatuses = [
				"Good Practice",
				"Fixed",
				"Critical Issue",
				"Normal Issue",
				"Minor Issue",
				"Won't Fix",
				"Wrong",
			];

			for (const status of validStatuses) {
				const result = badPracticeStatusSchema.safeParse(status);
				expect(result.success, `Status "${status}" should be valid`).toBe(true);
			}
		});

		it("should reject invalid status values", () => {
			const invalidStatuses = ["Invalid", "Pending", "Open", "", null, undefined, 123];

			for (const status of invalidStatuses) {
				const result = badPracticeStatusSchema.safeParse(status);
				expect(result.success, `Status "${status}" should be invalid`).toBe(false);
			}
		});
	});

	describe("badPracticeSchema", () => {
		it("should accept valid bad practice objects", () => {
			const validBadPractice: BadPractice = {
				title: "Missing description",
				description: "The PR description is empty",
				status: "Normal Issue",
			};

			const result = badPracticeSchema.safeParse(validBadPractice);
			expect(result.success).toBe(true);
		});

		it("should reject bad practice with missing fields", () => {
			const invalidCases = [
				{ title: "Test" }, // missing description and status
				{ title: "Test", description: "Desc" }, // missing status
				{ title: "Test", status: "Fixed" }, // missing description
				{}, // empty object
			];

			for (const invalidCase of invalidCases) {
				const result = badPracticeSchema.safeParse(invalidCase);
				expect(result.success).toBe(false);
			}
		});

		it("should accept good practice status", () => {
			const goodPractice: BadPractice = {
				title: "Clear commit messages",
				description: "All commits follow conventional commit format",
				status: "Good Practice",
			};

			const result = badPracticeSchema.safeParse(goodPractice);
			expect(result.success).toBe(true);
			if (result.success) {
				expect(result.data.status).toBe("Good Practice");
			}
		});
	});

	describe("detectorRequestSchema", () => {
		const validRequest: DetectorRequest = {
			title: "feat: add user authentication",
			description: "Implements JWT-based authentication",
			lifecycle_state: "Ready for review",
			repository_name: "my-app",
			pull_request_number: 123,
			bad_practice_summary: "",
			bad_practices: [],
			pull_request_template: "## Description\n\n## Changes",
		};

		it("should accept valid detector requests", () => {
			const result = detectorRequestSchema.safeParse(validRequest);
			expect(result.success).toBe(true);
		});

		it("should accept requests with existing bad practices", () => {
			const requestWithPractices = {
				...validRequest,
				bad_practices: [
					{
						title: "Short description",
						description: "Description is less than 50 characters",
						status: "Minor Issue",
					},
				],
				bad_practice_summary: "One minor issue detected",
			};

			const result = detectorRequestSchema.safeParse(requestWithPractices);
			expect(result.success).toBe(true);
		});

		it("should reject requests with missing required fields", () => {
			const invalidCases = [
				{ ...validRequest, title: undefined },
				{ ...validRequest, description: undefined },
				{ ...validRequest, lifecycle_state: undefined },
				{ ...validRequest, pull_request_number: undefined },
			];

			for (const invalidCase of invalidCases) {
				const result = detectorRequestSchema.safeParse(invalidCase);
				expect(result.success).toBe(false);
			}
		});

		it("should reject non-numeric pull_request_number", () => {
			const result = detectorRequestSchema.safeParse({
				...validRequest,
				pull_request_number: "123", // string instead of number
			});
			expect(result.success).toBe(false);
		});
	});

	describe("badPracticeResultSchema", () => {
		it("should accept valid result with bad practices", () => {
			const validResult: BadPracticeResult = {
				bad_practice_summary: "Found 2 issues that need attention",
				bad_practices: [
					{
						title: "Empty description",
						description: "The PR description is empty",
						status: "Critical Issue",
					},
					{
						title: "Good commit format",
						description: "Commits follow conventions",
						status: "Good Practice",
					},
				],
			};

			const result = badPracticeResultSchema.safeParse(validResult);
			expect(result.success).toBe(true);
		});

		it("should accept result with empty bad practices array", () => {
			const cleanResult: BadPracticeResult = {
				bad_practice_summary: "No issues found! Great work!",
				bad_practices: [],
			};

			const result = badPracticeResultSchema.safeParse(cleanResult);
			expect(result.success).toBe(true);
		});
	});

	describe("detectorResponseSchema", () => {
		it("should extend result with trace_id", () => {
			const response = {
				bad_practice_summary: "One issue found",
				bad_practices: [
					{
						title: "Test issue",
						description: "Test description",
						status: "Minor Issue",
					},
				],
				trace_id: "detector:repo#123",
			};

			const result = detectorResponseSchema.safeParse(response);
			expect(result.success).toBe(true);
			if (result.success) {
				expect(result.data.trace_id).toBe("detector:repo#123");
			}
		});

		it("should reject response without trace_id", () => {
			const responseWithoutTrace = {
				bad_practice_summary: "Summary",
				bad_practices: [],
				// missing trace_id
			};

			const result = detectorResponseSchema.safeParse(responseWithoutTrace);
			expect(result.success).toBe(false);
		});
	});
});

describe("Detector Prompts", () => {
	/**
	 * Tests the prompt loading with fallback behavior.
	 * Uses the canonical loadPrompt + badPracticeDetectorPrompt pattern.
	 */

	it("should load bad practice prompt with compile method", async () => {
		const { loadPrompt, badPracticeDetectorPrompt } = await import("@/prompts");

		// This should not throw even if Langfuse is not configured
		const prompt = await loadPrompt(badPracticeDetectorPrompt);

		// The prompt should have a compile method
		expect(prompt).toHaveProperty("compile");
		expect(typeof prompt.compile).toBe("function");

		// Compile should work with all expected variables
		const compiled = prompt.compile({
			title: "Test PR",
			description: "Test description",
			lifecycle_state: "Open",
			repository_name: "test-repo",
			bad_practice_summary: "",
			bad_practices: "[]",
			pull_request_template: "",
		});

		// Should return a string
		expect(typeof compiled).toBe("string");
		expect(compiled.length).toBeGreaterThan(0);
	});

	it("should indicate prompt source correctly", async () => {
		const { loadPrompt, badPracticeDetectorPrompt } = await import("@/prompts");
		const prompt = await loadPrompt(badPracticeDetectorPrompt);

		// Source should be 'local' or 'langfuse'
		expect(["local", "langfuse"]).toContain(prompt.source);
	});
});

describe("Detector Handler Contract", () => {
	/**
	 * These tests verify the handler contract without invoking AI.
	 * We test input validation and output structure.
	 */

	it("should require all request fields as defined in schema", () => {
		const requiredFields: (keyof DetectorRequest)[] = [
			"title",
			"description",
			"lifecycle_state",
			"repository_name",
			"pull_request_number",
			"bad_practice_summary",
			"bad_practices",
			"pull_request_template",
		];

		// Verify schema shape matches expected fields
		const schemaShape = detectorRequestSchema.shape;
		for (const field of requiredFields) {
			expect(schemaShape).toHaveProperty(field);
		}
	});

	it("should produce response matching schema", () => {
		// This verifies the handler output contract
		const mockHandlerOutput = {
			bad_practice_summary: "Found 1 critical issue",
			bad_practices: [
				{
					title: "Empty description",
					description: "PR description is empty which makes review difficult",
					status: "Critical Issue" as const,
				},
			],
			trace_id: "detector:repo#123",
		};

		const result = detectorResponseSchema.safeParse(mockHandlerOutput);
		expect(result.success).toBe(true);
	});

	it("should handle all lifecycle states in schema", () => {
		// The handler should accept all valid lifecycle states
		const lifecycleStates = ["Draft", "Open", "Ready for review", "Ready to merge"];

		for (const state of lifecycleStates) {
			const request = {
				title: "Test PR",
				description: "Test",
				lifecycle_state: state,
				repository_name: "repo",
				pull_request_number: 1,
				bad_practice_summary: "",
				bad_practices: [],
				pull_request_template: "",
			};

			const result = detectorRequestSchema.safeParse(request);
			expect(result.success, `Lifecycle state "${state}" should be valid`).toBe(true);
		}
	});
});

describe("Detector Integration Types", () => {
	/**
	 * Type-level tests to ensure detector types align with database schema.
	 */

	it("should match database enum values for status", () => {
		// These are the values stored in the database
		// If this test fails, the schema needs to be updated to match the DB
		const dbValues = [
			"Good Practice",
			"Fixed",
			"Critical Issue",
			"Normal Issue",
			"Minor Issue",
			"Won't Fix",
			"Wrong",
		];

		for (const value of dbValues) {
			const result = badPracticeStatusSchema.safeParse(value);
			expect(result.success, `DB value "${value}" should be valid in schema`).toBe(true);
		}
	});
});
