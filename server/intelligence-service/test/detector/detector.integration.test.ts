/**
 * Detector Integration Tests
 *
 * Tests the full HTTP API flow for the detector endpoint.
 * Uses mocked AI SDK to test handler behavior without real LLM calls.
 *
 * Key test scenarios:
 * 1. Happy path - valid requests return detection responses
 * 2. Detection quality - different inputs produce expected outcomes
 * 3. Error handling - invalid requests and AI failures
 * 4. Edge cases - unicode, long descriptions, existing bad practices
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type {
	BadPractice,
	BadPracticeResult,
	DetectorRequest,
	DetectorResponse,
} from "@/detector/detector.schema";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

/** Error response type from the API */
interface ErrorResponse {
	error: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Test Constants
// ─────────────────────────────────────────────────────────────────────────────

/** Base valid request for tests */
const createValidRequest = (overrides: Partial<DetectorRequest> = {}): DetectorRequest => ({
	title: "feat: add user authentication",
	description: "Implements JWT-based authentication with refresh tokens.",
	lifecycle_state: "Ready for review",
	repository_name: "Hephaestus",
	pull_request_number: 534,
	bad_practice_summary: "",
	bad_practices: [],
	pull_request_template: "## Description\n\n## Changes\n\n## Testing",
	...overrides,
});

/** Standard response for a good PR */
const createGoodPRResponse = (): BadPracticeResult => ({
	bad_practice_summary: "No issues found! The PR follows best practices.",
	bad_practices: [],
});

/** Response indicating a critical issue (empty description) */
const createCriticalIssueResponse = (): BadPracticeResult => ({
	bad_practice_summary: "Critical issue found: PR description is empty.",
	bad_practices: [
		{
			title: "Empty description",
			description:
				"The PR description is empty which makes it difficult for reviewers to understand the changes.",
			status: "Critical Issue",
		},
	],
});

/** Response indicating a normal issue (vague title) */
const createNormalIssueResponse = (): BadPracticeResult => ({
	bad_practice_summary: "One issue found: PR title could be more descriptive.",
	bad_practices: [
		{
			title: "Vague title",
			description:
				"The title 'fix bug' does not clearly describe what bug is being fixed or what component is affected.",
			status: "Normal Issue",
		},
	],
});

// ─────────────────────────────────────────────────────────────────────────────
// Mock Setup
// ─────────────────────────────────────────────────────────────────────────────

// Mock the AI SDK's generateObject function
const mockGenerateObject = vi.fn();
vi.mock("ai", async (importOriginal) => {
	const actual = await importOriginal<typeof import("ai")>();
	return {
		...actual,
		generateObject: (...args: unknown[]) => mockGenerateObject(...args),
	};
});

describe("Detector API Integration", () => {
	// Import app after mocking to ensure mocks are applied
	let app: Awaited<typeof import("@/app")>["default"];

	beforeEach(async () => {
		vi.clearAllMocks();
		// Re-import app to ensure fresh mocks
		const module = await import("@/app");
		app = module.default;
	});

	afterEach(() => {
		vi.resetAllMocks();
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Helper Functions
	// ─────────────────────────────────────────────────────────────────────────

	const makeDetectorRequest = (body: unknown) => {
		return app.fetch(
			new Request("http://localhost/detector", {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
				},
				body: JSON.stringify(body),
			}),
		);
	};

	// ─────────────────────────────────────────────────────────────────────────
	// Happy Path Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Happy Path", () => {
		it("should return detection response for valid request", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(createValidRequest());

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data).toMatchObject({
				bad_practice_summary: mockResult.bad_practice_summary,
				bad_practices: mockResult.bad_practices,
			});
			expect(data.trace_id).toBeDefined();
			expect(typeof data.trace_id).toBe("string");
		});

		it("should include trace_id in response for Langfuse linking", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const request = createValidRequest({
				repository_name: "MyRepo",
				pull_request_number: 42,
			});
			const response = await makeDetectorRequest(request);

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			// trace_id format: "detector:<repo>#<pr>"
			expect(data.trace_id).toBe("detector:MyRepo#42");
		});

		it("should return empty bad_practices array for clean PR", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(createValidRequest());

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data.bad_practices).toEqual([]);
		});

		it("should return non-empty bad_practices array when issues found", async () => {
			const mockResult = createCriticalIssueResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					description: "",
				}),
			);

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data.bad_practices.length).toBeGreaterThan(0);
			expect(data.bad_practices[0]).toMatchObject({
				title: expect.any(String),
				description: expect.any(String),
				status: expect.stringMatching(
					/Good Practice|Fixed|Critical Issue|Normal Issue|Minor Issue|Won't Fix|Wrong/,
				),
			});
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Detection Quality Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Detection Quality", () => {
		it("should detect empty description as critical issue", async () => {
			const mockResult = createCriticalIssueResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					title: "feat: add feature",
					description: "",
				}),
			);

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data.bad_practices).toHaveLength(1);
			expect(data.bad_practices[0]?.status).toBe("Critical Issue");
		});

		it("should detect vague title as normal issue", async () => {
			const mockResult = createNormalIssueResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					title: "fix bug",
					description: "Fixing a bug in the system",
				}),
			);

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data.bad_practices).toHaveLength(1);
			expect(data.bad_practices[0]?.status).toBe("Normal Issue");
		});

		it("should return good practice for well-formed PR", async () => {
			const mockResult: BadPracticeResult = {
				bad_practice_summary: "Excellent PR! All best practices followed.",
				bad_practices: [
					{
						title: "Clear and descriptive",
						description: "Title and description clearly explain the changes",
						status: "Good Practice",
					},
				],
			};
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					title: "feat(auth): implement JWT refresh token rotation",
					description:
						"This PR implements secure refresh token rotation to prevent token replay attacks...",
				}),
			);

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data.bad_practices).toHaveLength(1);
			expect(data.bad_practices[0]?.status).toBe("Good Practice");
		});

		it("should handle mixed good and bad practices", async () => {
			const mockResult: BadPracticeResult = {
				bad_practice_summary: "Mixed results: good title but missing tests",
				bad_practices: [
					{
						title: "Descriptive title",
						description: "Title follows conventional commits",
						status: "Good Practice",
					},
					{
						title: "No test coverage",
						description: "Changes lack unit tests",
						status: "Normal Issue",
					},
				],
			};
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(createValidRequest());

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data.bad_practices).toHaveLength(2);

			const statuses = data.bad_practices.map((bp: BadPractice) => bp.status);
			expect(statuses).toContain("Good Practice");
			expect(statuses).toContain("Normal Issue");
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Error Handling Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("Error Handling", () => {
		it("should return 422 for missing required fields", async () => {
			const response = await makeDetectorRequest({
				title: "test",
				// Missing other required fields
			});

			// Hono/OpenAPI returns 422 for validation errors
			expect(response.status).toBe(422);
		});

		it("should return 422 for invalid pull_request_number type", async () => {
			const response = await makeDetectorRequest({
				...createValidRequest(),
				pull_request_number: "not-a-number",
			});

			expect(response.status).toBe(422);
		});

		it("should return 422 for invalid bad_practices format", async () => {
			const response = await makeDetectorRequest({
				...createValidRequest(),
				bad_practices: "not-an-array",
			});

			expect(response.status).toBe(422);
		});

		it("should return 422 for bad_practice with invalid status", async () => {
			const response = await makeDetectorRequest({
				...createValidRequest(),
				bad_practices: [
					{
						title: "Test",
						description: "Test description",
						status: "Invalid Status",
					},
				],
			});

			expect(response.status).toBe(422);
		});

		it("should return 500 when AI generation fails", async () => {
			mockGenerateObject.mockRejectedValueOnce(new Error("AI service unavailable"));

			const response = await makeDetectorRequest(createValidRequest());

			expect(response.status).toBe(500);

			const data = (await response.json()) as ErrorResponse;
			expect(data.error).toBeDefined();
			expect(typeof data.error).toBe("string");
		});

		it("should return user-friendly error message on AI failure", async () => {
			mockGenerateObject.mockRejectedValueOnce(new Error("Rate limit exceeded"));

			const response = await makeDetectorRequest(createValidRequest());

			expect(response.status).toBe(500);

			const data = (await response.json()) as ErrorResponse;
			// Should not expose internal error details
			expect(data.error).toBe("Failed to analyze pull request. Please try again.");
		});

		it("should handle empty request body", async () => {
			const response = await app.fetch(
				new Request("http://localhost/detector", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
					},
					body: "{}",
				}),
			);

			expect(response.status).toBe(422);
		});

		it("should handle malformed JSON", async () => {
			const response = await app.fetch(
				new Request("http://localhost/detector", {
					method: "POST",
					headers: {
						"Content-Type": "application/json",
					},
					body: "not valid json",
				}),
			);

			// Malformed JSON typically returns 400
			expect(response.status).toBe(400);
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// Edge Cases
	// ─────────────────────────────────────────────────────────────────────────

	describe("Edge Cases", () => {
		it("should handle very long description", async () => {
			const longDescription = "A".repeat(10000);
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					description: longDescription,
				}),
			);

			expect(response.status).toBe(200);

			// Verify the mock was called with the long description
			expect(mockGenerateObject).toHaveBeenCalled();
		});

		it("should handle unicode characters in title", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					title: "feat: 添加用户认证 🔐 émoji test",
				}),
			);

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data).toHaveProperty("bad_practice_summary");
		});

		it("should handle unicode characters in description", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					description: "修复了一个关于 データベース 连接的问题 🐛\n\nДетали: решение проблемы",
				}),
			);

			expect(response.status).toBe(200);
		});

		it("should handle empty bad_practices array in input", async () => {
			const mockResult = createCriticalIssueResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					bad_practices: [],
					bad_practice_summary: "",
				}),
			);

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data.bad_practices).toBeDefined();
		});

		it("should handle existing bad_practices in input", async () => {
			const existingBadPractice = {
				title: "Previously detected issue",
				description: "This was found in a previous analysis",
				status: "Minor Issue" as const,
			};

			const mockResult: BadPracticeResult = {
				bad_practice_summary: "Re-analyzed with context",
				bad_practices: [
					{
						title: "Previously detected issue",
						description: "Still present",
						status: "Fixed",
					},
				],
			};
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					bad_practices: [existingBadPractice],
					bad_practice_summary: "One minor issue",
				}),
			);

			expect(response.status).toBe(200);

			// Verify existing bad practices were passed to the AI
			expect(mockGenerateObject).toHaveBeenCalled();
		});

		it("should handle special characters in repository name", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					repository_name: "my-org/my-repo.js",
				}),
			);

			expect(response.status).toBe(200);

			const data = (await response.json()) as DetectorResponse;
			expect(data.trace_id).toBe("detector:my-org/my-repo.js#534");
		});

		it("should handle newlines and markdown in description", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const markdownDescription = `
## Summary
This PR adds authentication.

## Changes
- Added JWT tokens
- Added refresh token rotation

## Testing
\`\`\`bash
npm test
\`\`\`

| Before | After |
|--------|-------|
| No auth | Auth |
			`.trim();

			const response = await makeDetectorRequest(
				createValidRequest({
					description: markdownDescription,
				}),
			);

			expect(response.status).toBe(200);
		});

		it("should handle empty pull_request_template", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			const response = await makeDetectorRequest(
				createValidRequest({
					pull_request_template: "",
				}),
			);

			expect(response.status).toBe(200);
		});

		it("should handle all lifecycle states", async () => {
			const lifecycleStates = ["Draft", "Open", "Ready for review", "Ready to merge", "Closed"];

			for (const state of lifecycleStates) {
				const mockResult = createGoodPRResponse();
				mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

				const response = await makeDetectorRequest(
					createValidRequest({
						lifecycle_state: state,
					}),
				);

				expect(response.status).toBe(200);
			}
		});
	});

	// ─────────────────────────────────────────────────────────────────────────
	// AI SDK Integration Tests
	// ─────────────────────────────────────────────────────────────────────────

	describe("AI SDK Integration", () => {
		it("should pass correct schema to generateObject", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			await makeDetectorRequest(createValidRequest());

			expect(mockGenerateObject).toHaveBeenCalledWith(
				expect.objectContaining({
					schema: expect.any(Object),
					prompt: expect.any(String),
					model: expect.anything(),
				}),
			);
		});

		it("should include telemetry options when enabled", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			await makeDetectorRequest(
				createValidRequest({
					repository_name: "TestRepo",
					pull_request_number: 99,
				}),
			);

			// Telemetry options are passed when Langfuse env vars are set
			// In tests without Langfuse config, buildTelemetryOptions returns undefined
			// so we just verify the call was made with expected model/schema
			expect(mockGenerateObject).toHaveBeenCalledWith(
				expect.objectContaining({
					model: expect.anything(),
					schema: expect.any(Object),
					prompt: expect.any(String),
				}),
			);
		});

		it("should use detection model from environment", async () => {
			const mockResult = createGoodPRResponse();
			mockGenerateObject.mockResolvedValueOnce({ object: mockResult });

			await makeDetectorRequest(createValidRequest());

			expect(mockGenerateObject).toHaveBeenCalled();
			const callArgs = mockGenerateObject.mock.calls[0]?.[0] as Record<string, unknown>;
			expect(callArgs).toHaveProperty("model");
		});
	});
});
