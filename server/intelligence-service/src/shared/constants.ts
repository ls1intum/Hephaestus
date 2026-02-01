/**
 * HTTP error response messages for consistent error handling.
 * Use these constants instead of magic strings in handlers.
 */
export const ERROR_MESSAGES = {
	// Generic errors
	NOT_FOUND: "Not found",
	INTERNAL_ERROR: "Internal error",
	SERVICE_UNAVAILABLE: "Service temporarily unavailable",
	INVALID_REQUEST: "Invalid request body",
	MISSING_CONTEXT: "Missing required context (userId or workspaceId)",

	// Resource-specific errors
	THREAD_NOT_FOUND: "Thread not found",
	MESSAGE_NOT_FOUND: "Message not found",
	DOCUMENT_NOT_FOUND: "Document not found",
	VOTE_RETRIEVAL_FAILED: "Vote retrieval failed",

	// Operation errors
	INSERT_FAILED: "Insert failed",
} as const;

/**
 * HTTP status codes as constants for clarity.
 */
export const HTTP_STATUS = {
	OK: 200,
	CREATED: 201,
	NO_CONTENT: 204,
	BAD_REQUEST: 400,
	NOT_FOUND: 404,
	INTERNAL_SERVER_ERROR: 500,
	SERVICE_UNAVAILABLE: 503,
} as const;
