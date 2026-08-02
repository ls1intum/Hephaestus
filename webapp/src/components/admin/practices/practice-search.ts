import { z } from "zod";

export const PRACTICE_SEARCH_PARAMS: "focus"[] = ["focus"];

export const practiceSearchSchema = z.object({
	focus: z.enum(["PULL_REQUEST", "ISSUE", "CONVERSATION_THREAD"]).optional().catch(undefined),
});
