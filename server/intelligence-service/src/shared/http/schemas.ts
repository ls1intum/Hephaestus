import { z } from "@hono/zod-openapi";

/**
 * Common HTTP response schemas for consistent error handling.
 *
 * Use these schemas instead of inline `z.object({ error: z.string() })` definitions
 * to eliminate duplication and ensure consistent error response structure.
 *
 * @example
 * ```ts
 * import { ErrorResponseSchema } from "@/shared/http/schemas";
 *
 * export const myRoute = createRoute({
 *   responses: {
 *     [HttpStatusCodes.BAD_REQUEST]: jsonContent(ErrorResponseSchema, "Bad request"),
 *   }
 * });
 * ```
 */

/**
 * Standard error response schema.
 * Used for all HTTP error responses (4xx, 5xx).
 */
export const ErrorResponseSchema = z
	.object({
		error: z.string().openapi({ description: "Human-readable error message" }),
	})
	.openapi("ErrorResponse");

export type ErrorResponse = z.infer<typeof ErrorResponseSchema>;
