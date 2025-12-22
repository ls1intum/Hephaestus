/**
 * AI SDK Error Handler
 *
 * Provides type-safe error discrimination and user-friendly error messages
 * for all AI SDK error types. Designed for production use with proper logging.
 *
 * @see https://ai-sdk.dev/docs/ai-sdk-core/error-handling
 */

import {
	AISDKError,
	APICallError,
	InvalidToolInputError,
	NoContentGeneratedError,
	RetryError,
} from "ai";
import type { HandlerLogger } from "@/shared/utils";

/**
 * Categorized error types for error handling decisions.
 */
export type AIErrorCategory =
	| "api_call" // External API failures (rate limits, auth, network)
	| "tool_input" // Invalid tool arguments from model
	| "no_content" // Model didn't generate content
	| "retry_exhausted" // All retry attempts failed
	| "unknown"; // Unrecognized error type

/**
 * Structured result from error handling.
 */
export interface AIErrorResult {
	/** User-friendly message (safe for display) */
	userMessage: string;
	/** Error category for programmatic handling */
	category: AIErrorCategory;
	/** Whether the operation might succeed on retry */
	isRetryable: boolean;
	/** HTTP status code suggestion */
	suggestedStatus: number;
	/** Additional context for logging (may contain sensitive data) */
	details: Record<string, unknown>;
}

/**
 * Error handler factory for AI SDK errors.
 *
 * Creates an error handler that:
 * - Discriminates between AI SDK error types
 * - Returns user-friendly messages
 * - Logs detailed error information
 * - Provides retry and status code suggestions
 *
 * @param logger - Logger instance for detailed error logging
 * @returns Error handler function
 *
 * @example
 * ```typescript
 * const handleError = createAIErrorHandler(logger);
 *
 * try {
 *   await streamText({ ... });
 * } catch (error) {
 *   const result = handleError(error);
 *   return context.json({ error: result.userMessage }, result.suggestedStatus);
 * }
 * ```
 */
export function createAIErrorHandler(logger: HandlerLogger) {
	return function handleAIError(error: unknown): AIErrorResult {
		// API Call Errors - External service failures
		if (APICallError.isInstance(error)) {
			return handleAPICallError(error, logger);
		}

		// Invalid Tool Input - Model generated bad tool arguments
		if (InvalidToolInputError.isInstance(error)) {
			return handleInvalidToolInputError(error, logger);
		}

		// No Content Generated - Model returned empty response
		if (NoContentGeneratedError.isInstance(error)) {
			return handleNoContentGeneratedError(error, logger);
		}

		// Retry Error - All retry attempts exhausted
		if (RetryError.isInstance(error)) {
			return handleRetryError(error, logger);
		}

		// Generic AI SDK Error - Catch-all for other AI SDK errors
		if (AISDKError.isInstance(error)) {
			return handleGenericAISDKError(error, logger);
		}

		// Unknown error type
		return handleUnknownError(error, logger);
	};
}

/**
 * Handle API call errors from LLM providers.
 */
function handleAPICallError(error: APICallError, logger: HandlerLogger): AIErrorResult {
	const { statusCode, url, isRetryable, responseBody, data } = error;

	// Log detailed error info
	logger.error(
		{
			errorType: "APICallError",
			statusCode,
			url,
			isRetryable,
			responseBody: responseBody?.slice(0, 500), // Truncate for logging
			data,
		},
		`API call failed: ${error.message}`,
	);

	// Determine user message based on status code
	let userMessage: string;
	let suggestedStatus: number;

	if (statusCode === 401 || statusCode === 403) {
		userMessage = "Unable to connect to AI service. Please try again later.";
		suggestedStatus = 503;
	} else if (statusCode === 429) {
		userMessage = "The AI service is currently busy. Please wait a moment and try again.";
		suggestedStatus = 429;
	} else if (statusCode === 408 || statusCode === 504) {
		userMessage = "The request timed out. Please try again.";
		suggestedStatus = 504;
	} else if (statusCode !== undefined && statusCode >= 500) {
		userMessage = "The AI service is temporarily unavailable. Please try again shortly.";
		suggestedStatus = 503;
	} else {
		userMessage = "Something went wrong while processing your request. Please try again.";
		suggestedStatus = 500;
	}

	return {
		userMessage,
		category: "api_call",
		isRetryable,
		suggestedStatus,
		details: {
			statusCode,
			url,
			responseBody: responseBody?.slice(0, 500),
		},
	};
}

/**
 * Handle invalid tool input errors.
 *
 * These occur when the model generates tool arguments that don't match
 * the expected schema. Usually indicates a prompt or schema issue.
 */
function handleInvalidToolInputError(
	error: InvalidToolInputError,
	logger: HandlerLogger,
): AIErrorResult {
	const { toolName, toolInput, cause } = error;

	logger.error(
		{
			errorType: "InvalidToolInputError",
			toolName,
			toolInput: toolInput.slice(0, 500), // Truncate for logging
			cause,
		},
		`Invalid tool input for ${toolName}: ${error.message}`,
	);

	return {
		userMessage:
			"I had trouble understanding how to help with that. Could you try rephrasing your request?",
		category: "tool_input",
		isRetryable: true, // Different phrasing might work
		suggestedStatus: 400,
		details: {
			toolName,
			toolInput: toolInput.slice(0, 200),
		},
	};
}

/**
 * Handle no content generated errors.
 *
 * Occurs when the model returns an empty response. May indicate
 * content filtering, context issues, or model limitations.
 */
function handleNoContentGeneratedError(
	error: NoContentGeneratedError,
	logger: HandlerLogger,
): AIErrorResult {
	logger.warn(
		{
			errorType: "NoContentGeneratedError",
			message: error.message,
		},
		"Model generated no content",
	);

	return {
		userMessage: "I couldn't generate a response. Could you try asking in a different way?",
		category: "no_content",
		isRetryable: true,
		suggestedStatus: 200, // Not really an error from user's perspective
		details: {
			originalMessage: error.message,
		},
	};
}

/**
 * Handle retry errors when all attempts are exhausted.
 */
function handleRetryError(error: RetryError, logger: HandlerLogger): AIErrorResult {
	const { reason, errors, lastError } = error;

	logger.error(
		{
			errorType: "RetryError",
			reason,
			attemptCount: errors.length,
			lastError:
				lastError instanceof Error
					? { message: lastError.message, name: lastError.name }
					: lastError,
		},
		`All retry attempts exhausted (${reason}): ${error.message}`,
	);

	let userMessage: string;
	switch (reason) {
		case "maxRetriesExceeded":
			userMessage =
				"The request failed after multiple attempts. The AI service may be experiencing issues.";
			break;
		case "abort":
			userMessage = "The request was cancelled.";
			break;
		case "errorNotRetryable":
			userMessage = "The request failed and cannot be automatically retried.";
			break;
		default:
			userMessage = "The request failed after multiple attempts. Please try again later.";
	}

	return {
		userMessage,
		category: "retry_exhausted",
		isRetryable: reason === "maxRetriesExceeded", // Only retry if it was a transient issue
		suggestedStatus: 503,
		details: {
			reason,
			attemptCount: errors.length,
		},
	};
}

/**
 * Handle generic AI SDK errors not covered by specific handlers.
 */
function handleGenericAISDKError(error: AISDKError, logger: HandlerLogger): AIErrorResult {
	logger.error(
		{
			errorType: "AISDKError",
			name: error.name,
			message: error.message,
			cause: error.cause,
		},
		`AI SDK error: ${error.message}`,
	);

	return {
		userMessage: "Something went wrong while processing your request. Please try again.",
		category: "unknown",
		isRetryable: true,
		suggestedStatus: 500,
		details: {
			errorName: error.name,
			message: error.message,
		},
	};
}

/**
 * Handle completely unknown errors.
 */
function handleUnknownError(error: unknown, logger: HandlerLogger): AIErrorResult {
	const message = error instanceof Error ? error.message : String(error);
	const name = error instanceof Error ? error.name : "Unknown";

	logger.error(
		{
			errorType: "UnknownError",
			name,
			message,
			error,
		},
		`Unexpected error: ${message}`,
	);

	return {
		userMessage: "An unexpected error occurred. Please try again.",
		category: "unknown",
		isRetryable: true,
		suggestedStatus: 500,
		details: {
			errorName: name,
			message,
		},
	};
}

/**
 * Type guard to check if an error is any AI SDK error type.
 */
export function isAIError(error: unknown): boolean {
	return (
		APICallError.isInstance(error) ||
		InvalidToolInputError.isInstance(error) ||
		NoContentGeneratedError.isInstance(error) ||
		RetryError.isInstance(error) ||
		AISDKError.isInstance(error)
	);
}

/**
 * Get a safe error message for streaming responses.
 *
 * Unlike `createAIErrorHandler`, this is designed for use in stream
 * error handlers where you need to return a simple string.
 *
 * @param error - The error to convert
 * @param isProduction - Whether to hide detailed errors
 * @returns User-safe error message string
 */
export function getStreamErrorMessage(error: unknown, isProduction = true): string {
	if (APICallError.isInstance(error)) {
		const { statusCode } = error;
		if (statusCode === 429) {
			return "The AI service is busy. Please wait a moment.";
		}
		if (statusCode !== undefined && statusCode >= 500) {
			return "The AI service is temporarily unavailable.";
		}
	}

	if (NoContentGeneratedError.isInstance(error)) {
		return "I couldn't generate a response. Please try again.";
	}

	if (InvalidToolInputError.isInstance(error)) {
		return "I had trouble processing that. Could you rephrase?";
	}

	if (RetryError.isInstance(error)) {
		return "The request failed after multiple attempts.";
	}

	// Generic fallback
	if (isProduction) {
		return "An error occurred";
	}

	return error instanceof Error ? error.message : "An error occurred";
}
