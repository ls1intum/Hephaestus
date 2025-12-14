import type { TypedResponse } from "hono";
import type { StatusCode } from "hono/utils/http-status";

/**
 * Narrow a Web Response to Hono's TypedResponse without repeating the cast everywhere.
 */
export function asTyped<
	T,
	S extends StatusCode = 200,
	F extends "text" | "json" | "stream" = "text",
>(res: Response) {
	return res as unknown as TypedResponse<T, S, F>;
}
