import { v4 as uuidv4, v5 as uuidv5 } from "uuid";

/**
 * Namespace for generating deterministic UUIDs from tool call IDs.
 * This must be a valid UUID v4 for uuidv5 to work correctly.
 */
export const TOOL_CALL_NAMESPACE = "893f5233-af7e-467b-aee1-264558855979";

/**
 * Convert an AI SDK tool call ID to a deterministic UUID.
 * This ensures the same tool call always produces the same document ID.
 *
 * @param callId - The tool call ID from the AI SDK
 * @returns A deterministic UUID, or a random UUID if the input is invalid
 */
export function toolCallIdToUuid(callId: string | null | undefined): string {
	if (!callId || typeof callId !== "string") {
		return uuidv4();
	}
	try {
		return uuidv5(callId, TOOL_CALL_NAMESPACE);
	} catch {
		return uuidv4();
	}
}
