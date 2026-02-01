/**
 * AI Error Handler Tests
 *
 * Tests for the AI SDK error handler module, covering all error types
 * and utility functions for proper error handling.
 */

import {
	AISDKError,
	APICallError,
	InvalidToolInputError,
	NoContentGeneratedError,
	RetryError,
} from "ai";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
	type AIErrorResult,
	createAIErrorHandler,
	getStreamErrorMessage,
	isAIError,
} from "@/shared/ai/error-handler";
import type { HandlerLogger } from "@/shared/utils";

// ─────────────────────────────────────────────────────────────────────────────
// Mock Logger Factory
// ─────────────────────────────────────────────────────────────────────────────

function createMockLogger(): HandlerLogger {
	return {
		info: vi.fn(),
		warn: vi.fn(),
		error: vi.fn(),
		debug: vi.fn(),
		trace: vi.fn(),
		fatal: vi.fn(),
		child: vi.fn(() => createMockLogger()),
		silent: vi.fn(),
		level: "info",
		isLevelEnabled: vi.fn(() => true),
	} as unknown as HandlerLogger;
}

// ─────────────────────────────────────────────────────────────────────────────
// Test Helpers for AI SDK v6
// ─────────────────────────────────────────────────────────────────────────────

const TEST_URL = "https://api.openai.com/v1/chat/completions";

/**
 * Create an APICallError with required AI SDK v6 fields.
 * requestBodyValues is now required in AI SDK v6.
 */
function createAPICallError(opts: {
	message: string;
	statusCode?: number;
	isRetryable?: boolean;
	responseBody?: string;
}): APICallError {
	return new APICallError({
		...opts,
		url: TEST_URL,
		requestBodyValues: {}, // Required in AI SDK v6
	});
}

// ─────────────────────────────────────────────────────────────────────────────
// createAIErrorHandler
// ─────────────────────────────────────────────────────────────────────────────

describe("createAIErrorHandler", () => {
	it("should return a handler function", () => {
		const logger = createMockLogger();
		const handleError = createAIErrorHandler(logger);

		expect(typeof handleError).toBe("function");
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// handleError - APICallError
// ─────────────────────────────────────────────────────────────────────────────

describe("handleError with APICallError", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should extract message and mark as retryable for 429 errors", () => {
		const error = createAPICallError({
			message: "Rate limit exceeded",
			statusCode: 429,
			isRetryable: true,
		});

		const result = handleError(error);

		expect(result.category).toBe("api_call");
		expect(result.isRetryable).toBe(true);
		expect(result.suggestedStatus).toBe(429);
		expect(result.userMessage).toContain("busy");
		expect(result.details.statusCode).toBe(429);
	});

	it("should handle 401 auth errors with 503 status", () => {
		const error = createAPICallError({
			message: "Unauthorized",
			statusCode: 401,
			isRetryable: false,
		});

		const result = handleError(error);

		expect(result.category).toBe("api_call");
		expect(result.suggestedStatus).toBe(503);
		expect(result.userMessage).toContain("Unable to connect");
	});

	it("should handle 403 forbidden errors with 503 status", () => {
		const error = createAPICallError({
			message: "Forbidden",
			statusCode: 403,
			isRetryable: false,
		});

		const result = handleError(error);

		expect(result.suggestedStatus).toBe(503);
		expect(result.userMessage).toContain("Unable to connect");
	});

	it("should handle 408 timeout errors with 504 status", () => {
		const error = createAPICallError({
			message: "Request Timeout",
			statusCode: 408,
			isRetryable: true,
		});

		const result = handleError(error);

		expect(result.suggestedStatus).toBe(504);
		expect(result.userMessage).toContain("timed out");
	});

	it("should handle 504 gateway timeout errors", () => {
		const error = createAPICallError({
			message: "Gateway Timeout",
			statusCode: 504,
			isRetryable: true,
		});

		const result = handleError(error);

		expect(result.suggestedStatus).toBe(504);
		expect(result.userMessage).toContain("timed out");
	});

	it("should handle 5xx server errors with 503 status", () => {
		const error = createAPICallError({
			message: "Internal Server Error",
			statusCode: 500,
			isRetryable: true,
		});

		const result = handleError(error);

		expect(result.suggestedStatus).toBe(503);
		expect(result.userMessage).toContain("temporarily unavailable");
	});

	it("should handle unknown status codes with 500 status", () => {
		const error = createAPICallError({
			message: "Bad Request",
			statusCode: 400,
			isRetryable: false,
		});

		const result = handleError(error);

		expect(result.suggestedStatus).toBe(500);
		expect(result.userMessage).toContain("Something went wrong");
	});

	it("should handle undefined status code", () => {
		const error = createAPICallError({
			message: "Network error",
			isRetryable: true,
		});

		const result = handleError(error);

		expect(result.category).toBe("api_call");
		expect(result.suggestedStatus).toBe(500);
	});

	it("should truncate long response body in details", () => {
		const longBody = "x".repeat(1000);
		const error = createAPICallError({
			message: "Error with long body",
			statusCode: 500,
			responseBody: longBody,
			isRetryable: true,
		});

		const result = handleError(error);

		expect((result.details.responseBody as string).length).toBeLessThanOrEqual(500);
	});

	it("should log error details", () => {
		const error = createAPICallError({
			message: "API failure",
			statusCode: 500,
			isRetryable: true,
		});

		handleError(error);

		expect(logger.error).toHaveBeenCalledWith(
			expect.objectContaining({
				errorType: "APICallError",
				statusCode: 500,
			}),
			expect.stringContaining("API call failed"),
		);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// handleError - InvalidToolInputError
// ─────────────────────────────────────────────────────────────────────────────

describe("handleError with InvalidToolInputError", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should extract tool name and input", () => {
		const error = new InvalidToolInputError({
			toolName: "search_documents",
			toolInput: '{"query": "test", "limit": "invalid"}',
			cause: new Error("Invalid type for limit"),
		});

		const result = handleError(error);

		expect(result.category).toBe("tool_input");
		expect(result.details.toolName).toBe("search_documents");
		expect(result.details.toolInput).toContain("query");
	});

	it("should be marked as retryable", () => {
		const error = new InvalidToolInputError({
			toolName: "get_user",
			toolInput: '{"userId": null}',
			cause: new Error("userId cannot be null"),
		});

		const result = handleError(error);

		expect(result.isRetryable).toBe(true);
		expect(result.suggestedStatus).toBe(400);
	});

	it("should return user-friendly message", () => {
		const error = new InvalidToolInputError({
			toolName: "fetch_data",
			toolInput: "{}",
			cause: new Error("Missing required field"),
		});

		const result = handleError(error);

		expect(result.userMessage).toContain("trouble understanding");
		expect(result.userMessage).toContain("rephras");
	});

	it("should truncate long tool input in details", () => {
		const longInput = JSON.stringify({ data: "x".repeat(500) });
		const error = new InvalidToolInputError({
			toolName: "process_data",
			toolInput: longInput,
			cause: new Error("Too large"),
		});

		const result = handleError(error);

		expect((result.details.toolInput as string).length).toBeLessThanOrEqual(200);
	});

	it("should log error with tool details", () => {
		const error = new InvalidToolInputError({
			toolName: "my_tool",
			toolInput: '{"field": "value"}',
			cause: new Error("Validation failed"),
		});

		handleError(error);

		expect(logger.error).toHaveBeenCalledWith(
			expect.objectContaining({
				errorType: "InvalidToolInputError",
				toolName: "my_tool",
			}),
			expect.stringContaining("Invalid tool input"),
		);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// handleError - NoContentGeneratedError
// ─────────────────────────────────────────────────────────────────────────────

describe("handleError with NoContentGeneratedError", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should return appropriate message", () => {
		const error = new NoContentGeneratedError({
			message: "Model returned empty response",
		});

		const result = handleError(error);

		expect(result.category).toBe("no_content");
		expect(result.userMessage).toContain("couldn't generate");
		expect(result.userMessage).toContain("different way");
	});

	it("should return 200 status (not really an error)", () => {
		const error = new NoContentGeneratedError({
			message: "Empty response",
		});

		const result = handleError(error);

		expect(result.suggestedStatus).toBe(200);
	});

	it("should be marked as retryable", () => {
		const error = new NoContentGeneratedError({
			message: "No content",
		});

		const result = handleError(error);

		expect(result.isRetryable).toBe(true);
	});

	it("should include original message in details", () => {
		const originalMessage = "The model refused to generate content";
		const error = new NoContentGeneratedError({
			message: originalMessage,
		});

		const result = handleError(error);

		expect(result.details.originalMessage).toBe(originalMessage);
	});

	it("should log as warning level", () => {
		const error = new NoContentGeneratedError({
			message: "Empty response",
		});

		handleError(error);

		expect(logger.warn).toHaveBeenCalledWith(
			expect.objectContaining({
				errorType: "NoContentGeneratedError",
			}),
			"Model generated no content",
		);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// handleError - RetryError
// ─────────────────────────────────────────────────────────────────────────────

describe("handleError with RetryError", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should extract retry info for maxRetriesExceeded", () => {
		const error = new RetryError({
			message: "Max retries exceeded",
			reason: "maxRetriesExceeded",
			errors: [new Error("Attempt 1"), new Error("Attempt 2"), new Error("Attempt 3")],
		});

		const result = handleError(error);

		expect(result.category).toBe("retry_exhausted");
		expect(result.details.reason).toBe("maxRetriesExceeded");
		expect(result.details.attemptCount).toBe(3);
		expect(result.isRetryable).toBe(true);
	});

	it("should handle abort reason", () => {
		const error = new RetryError({
			message: "Request aborted",
			reason: "abort",
			errors: [],
		});

		const result = handleError(error);

		expect(result.userMessage).toContain("cancelled");
		expect(result.isRetryable).toBe(false);
	});

	it("should handle errorNotRetryable reason", () => {
		const error = new RetryError({
			message: "Non-retryable error",
			reason: "errorNotRetryable",
			errors: [],
		});

		const result = handleError(error);

		expect(result.userMessage).toContain("cannot be automatically retried");
		expect(result.isRetryable).toBe(false);
	});

	it("should return 503 status", () => {
		const error = new RetryError({
			message: "Retries exhausted",
			reason: "maxRetriesExceeded",
			errors: [],
		});

		const result = handleError(error);

		expect(result.suggestedStatus).toBe(503);
	});

	it("should log error with attempt count", () => {
		const errors = [new Error("1"), new Error("2"), new Error("3")];
		const error = new RetryError({
			message: "All attempts failed",
			reason: "maxRetriesExceeded",
			errors,
		});

		handleError(error);

		expect(logger.error).toHaveBeenCalledWith(
			expect.objectContaining({
				errorType: "RetryError",
				reason: "maxRetriesExceeded",
				attemptCount: 3,
			}),
			expect.stringContaining("All retry attempts exhausted"),
		);
	});

	it("should handle unknown reason gracefully", () => {
		const error = new RetryError({
			message: "Unknown reason",
			reason: "unknownReason" as "maxRetriesExceeded",
			errors: [],
		});

		const result = handleError(error);

		expect(result.userMessage).toContain("failed after multiple attempts");
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// handleError - Generic AISDKError
// ─────────────────────────────────────────────────────────────────────────────

describe("handleError with generic AISDKError", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should handle unknown SDK errors", () => {
		// Create a generic AI SDK error using the base class
		const error = new AISDKError({
			name: "UnknownAIError",
			message: "Something unexpected happened in AI SDK",
		});

		const result = handleError(error);

		expect(result.category).toBe("unknown");
		expect(result.isRetryable).toBe(true);
		expect(result.suggestedStatus).toBe(500);
	});

	it("should include error name in details", () => {
		const error = new AISDKError({
			name: "CustomSDKError",
			message: "Custom error message",
		});

		const result = handleError(error);

		expect(result.details.errorName).toBe("CustomSDKError");
		expect(result.details.message).toBe("Custom error message");
	});

	it("should return generic user message", () => {
		const error = new AISDKError({
			name: "InternalError",
			message: "Internal processing error",
		});

		const result = handleError(error);

		expect(result.userMessage).toContain("Something went wrong");
		expect(result.userMessage).toContain("try again");
	});

	it("should log error with name and message", () => {
		const error = new AISDKError({
			name: "TestError",
			message: "Test message",
		});

		handleError(error);

		expect(logger.error).toHaveBeenCalledWith(
			expect.objectContaining({
				errorType: "AISDKError",
				name: "TestError",
				message: "Test message",
			}),
			expect.stringContaining("AI SDK error"),
		);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// handleError - Standard Error
// ─────────────────────────────────────────────────────────────────────────────

describe("handleError with standard Error", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should wrap as internal error", () => {
		const error = new Error("Database connection failed");

		const result = handleError(error);

		expect(result.category).toBe("unknown");
		expect(result.suggestedStatus).toBe(500);
		expect(result.details.errorName).toBe("Error");
		expect(result.details.message).toBe("Database connection failed");
	});

	it("should be marked as retryable", () => {
		const error = new Error("Temporary failure");

		const result = handleError(error);

		expect(result.isRetryable).toBe(true);
	});

	it("should return safe user message", () => {
		const error = new Error("Internal server details exposed");

		const result = handleError(error);

		expect(result.userMessage).toBe("An unexpected error occurred. Please try again.");
		expect(result.userMessage).not.toContain("Internal server details");
	});

	it("should preserve error name for custom errors", () => {
		class CustomError extends Error {
			constructor(message: string) {
				super(message);
				this.name = "CustomError";
			}
		}
		const error = new CustomError("Custom failure");

		const result = handleError(error);

		expect(result.details.errorName).toBe("CustomError");
	});

	it("should log as unexpected error", () => {
		const error = new Error("Unexpected failure");

		handleError(error);

		expect(logger.error).toHaveBeenCalledWith(
			expect.objectContaining({
				errorType: "UnknownError",
			}),
			expect.stringContaining("Unexpected error"),
		);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// handleError - Unknown Error Types
// ─────────────────────────────────────────────────────────────────────────────

describe("handleError with unknown error type", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should handle string errors gracefully", () => {
		const result = handleError("Something bad happened");

		expect(result.category).toBe("unknown");
		expect(result.details.message).toBe("Something bad happened");
		expect(result.details.errorName).toBe("Unknown");
	});

	it("should handle number errors", () => {
		const result = handleError(42);

		expect(result.category).toBe("unknown");
		expect(result.details.message).toBe("42");
	});

	it("should handle null", () => {
		const result = handleError(null);

		expect(result.category).toBe("unknown");
		expect(result.details.message).toBe("null");
	});

	it("should handle undefined", () => {
		const result = handleError(undefined);

		expect(result.category).toBe("unknown");
		expect(result.details.message).toBe("undefined");
	});

	it("should handle plain objects", () => {
		const result = handleError({ code: 500, message: "Failed" });

		expect(result.category).toBe("unknown");
		expect(result.details.message).toBe("[object Object]");
	});

	it("should return safe defaults", () => {
		const result = handleError(Symbol("test"));

		expect(result.userMessage).toBe("An unexpected error occurred. Please try again.");
		expect(result.isRetryable).toBe(true);
		expect(result.suggestedStatus).toBe(500);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// isAIError Type Guard
// ─────────────────────────────────────────────────────────────────────────────

describe("isAIError type guard", () => {
	it("should return true for APICallError", () => {
		const error = createAPICallError({
			message: "API error",
			statusCode: 500,
			isRetryable: true,
		});

		expect(isAIError(error)).toBe(true);
	});

	it("should return true for InvalidToolInputError", () => {
		const error = new InvalidToolInputError({
			toolName: "test",
			toolInput: "{}",
			cause: new Error("Invalid"),
		});

		expect(isAIError(error)).toBe(true);
	});

	it("should return true for NoContentGeneratedError", () => {
		const error = new NoContentGeneratedError({
			message: "No content",
		});

		expect(isAIError(error)).toBe(true);
	});

	it("should return true for RetryError", () => {
		const error = new RetryError({
			message: "Retry failed",
			reason: "maxRetriesExceeded",
			errors: [],
		});

		expect(isAIError(error)).toBe(true);
	});

	it("should return true for generic AISDKError", () => {
		const error = new AISDKError({
			name: "CustomError",
			message: "Custom message",
		});

		expect(isAIError(error)).toBe(true);
	});

	it("should return false for standard Error", () => {
		const error = new Error("Standard error");

		expect(isAIError(error)).toBe(false);
	});

	it("should return false for string", () => {
		expect(isAIError("error string")).toBe(false);
	});

	it("should return false for null", () => {
		expect(isAIError(null)).toBe(false);
	});

	it("should return false for undefined", () => {
		expect(isAIError(undefined)).toBe(false);
	});

	it("should return false for plain object", () => {
		expect(isAIError({ message: "error" })).toBe(false);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// getStreamErrorMessage - Production Mode
// ─────────────────────────────────────────────────────────────────────────────

describe("getStreamErrorMessage in production", () => {
	it("should return user-safe message for APICallError 429", () => {
		const error = createAPICallError({
			message: "Rate limit exceeded",
			statusCode: 429,
			isRetryable: true,
		});

		const message = getStreamErrorMessage(error, true);

		expect(message).toBe("The AI service is busy. Please wait a moment.");
	});

	it("should return user-safe message for APICallError 5xx", () => {
		const error = createAPICallError({
			message: "Internal server error",
			statusCode: 500,
			isRetryable: true,
		});

		const message = getStreamErrorMessage(error, true);

		expect(message).toBe("The AI service is temporarily unavailable.");
	});

	it("should return generic message for other APICallErrors", () => {
		const error = createAPICallError({
			message: "Bad request details",
			statusCode: 400,
			isRetryable: false,
		});

		const message = getStreamErrorMessage(error, true);

		expect(message).toBe("An error occurred");
	});

	it("should return message for NoContentGeneratedError", () => {
		const error = new NoContentGeneratedError({
			message: "Empty response",
		});

		const message = getStreamErrorMessage(error, true);

		expect(message).toBe("I couldn't generate a response. Please try again.");
	});

	it("should return message for InvalidToolInputError", () => {
		const error = new InvalidToolInputError({
			toolName: "test",
			toolInput: "{}",
			cause: new Error("Invalid"),
		});

		const message = getStreamErrorMessage(error, true);

		expect(message).toBe("I had trouble processing that. Could you rephrase?");
	});

	it("should return message for RetryError", () => {
		const error = new RetryError({
			message: "All retries failed",
			reason: "maxRetriesExceeded",
			errors: [],
		});

		const message = getStreamErrorMessage(error, true);

		expect(message).toBe("The request failed after multiple attempts.");
	});

	it("should return generic message for standard Error in production", () => {
		const error = new Error("Sensitive internal details");

		const message = getStreamErrorMessage(error, true);

		expect(message).toBe("An error occurred");
		expect(message).not.toContain("Sensitive");
	});

	it("should return generic message for unknown errors in production", () => {
		const message = getStreamErrorMessage("string error", true);

		expect(message).toBe("An error occurred");
	});

	it("should default to production mode", () => {
		const error = new Error("Should be hidden");

		const message = getStreamErrorMessage(error);

		expect(message).toBe("An error occurred");
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// getStreamErrorMessage - Development Mode
// ─────────────────────────────────────────────────────────────────────────────

describe("getStreamErrorMessage in development", () => {
	it("should return detailed message for standard Error", () => {
		const error = new Error("Detailed debug information");

		const message = getStreamErrorMessage(error, false);

		expect(message).toBe("Detailed debug information");
	});

	it("should still return AI-specific messages for AI errors", () => {
		const error = createAPICallError({
			message: "Rate limit exceeded",
			statusCode: 429,
			isRetryable: true,
		});

		const message = getStreamErrorMessage(error, false);

		expect(message).toBe("The AI service is busy. Please wait a moment.");
	});

	it("should return error message for non-Error in development", () => {
		// Non-Error values should return generic message even in dev
		const message = getStreamErrorMessage("string error", false);

		expect(message).toBe("An error occurred");
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// HTTP Status Code Mapping
// ─────────────────────────────────────────────────────────────────────────────

describe("HTTP status code mapping", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should map APICallError 401 to 503", () => {
		const error = createAPICallError({
			message: "Unauthorized",
			statusCode: 401,
			isRetryable: false,
		});

		expect(handleError(error).suggestedStatus).toBe(503);
	});

	it("should map APICallError 403 to 503", () => {
		const error = createAPICallError({
			message: "Forbidden",
			statusCode: 403,
			isRetryable: false,
		});

		expect(handleError(error).suggestedStatus).toBe(503);
	});

	it("should map APICallError 429 to 429", () => {
		const error = createAPICallError({
			message: "Too Many Requests",
			statusCode: 429,
			isRetryable: true,
		});

		expect(handleError(error).suggestedStatus).toBe(429);
	});

	it("should map APICallError 408 to 504", () => {
		const error = createAPICallError({
			message: "Timeout",
			statusCode: 408,
			isRetryable: true,
		});

		expect(handleError(error).suggestedStatus).toBe(504);
	});

	it("should map APICallError 504 to 504", () => {
		const error = createAPICallError({
			message: "Gateway Timeout",
			statusCode: 504,
			isRetryable: true,
		});

		expect(handleError(error).suggestedStatus).toBe(504);
	});

	it("should map APICallError 500 to 503", () => {
		const error = createAPICallError({
			message: "Internal Server Error",
			statusCode: 500,
			isRetryable: true,
		});

		expect(handleError(error).suggestedStatus).toBe(503);
	});

	it("should map InvalidToolInputError to 400", () => {
		const error = new InvalidToolInputError({
			toolName: "test",
			toolInput: "{}",
			cause: new Error("Invalid"),
		});

		expect(handleError(error).suggestedStatus).toBe(400);
	});

	it("should map NoContentGeneratedError to 200", () => {
		const error = new NoContentGeneratedError({
			message: "Empty",
		});

		expect(handleError(error).suggestedStatus).toBe(200);
	});

	it("should map RetryError to 503", () => {
		const error = new RetryError({
			message: "Retries exhausted",
			reason: "maxRetriesExceeded",
			errors: [],
		});

		expect(handleError(error).suggestedStatus).toBe(503);
	});

	it("should map generic AISDKError to 500", () => {
		const error = new AISDKError({
			name: "Unknown",
			message: "Unknown error",
		});

		expect(handleError(error).suggestedStatus).toBe(500);
	});

	it("should map standard Error to 500", () => {
		const error = new Error("Unknown");

		expect(handleError(error).suggestedStatus).toBe(500);
	});
});

// ─────────────────────────────────────────────────────────────────────────────
// Category Assignment
// ─────────────────────────────────────────────────────────────────────────────

describe("category assignment", () => {
	let logger: HandlerLogger;
	let handleError: (error: unknown) => AIErrorResult;

	beforeEach(() => {
		logger = createMockLogger();
		handleError = createAIErrorHandler(logger);
	});

	it("should assign api_call category to APICallError", () => {
		const error = createAPICallError({
			message: "API error",
			statusCode: 500,
			isRetryable: true,
		});

		expect(handleError(error).category).toBe("api_call");
	});

	it("should assign tool_input category to InvalidToolInputError", () => {
		const error = new InvalidToolInputError({
			toolName: "test",
			toolInput: "{}",
			cause: new Error("Invalid"),
		});

		expect(handleError(error).category).toBe("tool_input");
	});

	it("should assign no_content category to NoContentGeneratedError", () => {
		const error = new NoContentGeneratedError({
			message: "Empty",
		});

		expect(handleError(error).category).toBe("no_content");
	});

	it("should assign retry_exhausted category to RetryError", () => {
		const error = new RetryError({
			message: "Retries exhausted",
			reason: "maxRetriesExceeded",
			errors: [],
		});

		expect(handleError(error).category).toBe("retry_exhausted");
	});

	it("should assign unknown category to generic AISDKError", () => {
		const error = new AISDKError({
			name: "Unknown",
			message: "Unknown error",
		});

		expect(handleError(error).category).toBe("unknown");
	});

	it("should assign unknown category to standard Error", () => {
		const error = new Error("Standard error");

		expect(handleError(error).category).toBe("unknown");
	});

	it("should assign unknown category to non-Error values", () => {
		expect(handleError("string").category).toBe("unknown");
		expect(handleError(123).category).toBe("unknown");
		expect(handleError(null).category).toBe("unknown");
	});
});
