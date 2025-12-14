import { v4 as uuidv4, v5 as uuidv5 } from "uuid";

export const TOOL_CALL_NAMESPACE = "11111111-2222-3333-4444-555555555555";

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
