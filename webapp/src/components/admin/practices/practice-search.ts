import { z } from "zod";
import { ARTIFACT_KIND_VALUES } from "@/lib/artifact-kinds";

export const PRACTICE_SEARCH_PARAMS: ("focus" | "library")[] = ["focus", "library"];

export const practiceSearchSchema = z.object({
	focus: z.enum(ARTIFACT_KIND_VALUES).optional().catch(undefined),
	library: z.boolean().optional().catch(undefined),
});
