import { z } from "zod";

export const CURATED_CATALOG_SEARCH_PARAMS: Array<keyof CuratedCatalogSearch> = [
	"q",
	"status",
	"artifact",
];

export const curatedCatalogSearchSchema = z.object({
	q: z.string().max(200).optional().catch(undefined),
	status: z.enum(["RETIRED", "ALL"]).optional().catch(undefined),
	artifact: z.enum(["PULL_REQUEST", "ISSUE", "CONVERSATION_THREAD"]).optional().catch(undefined),
});

export type CuratedCatalogSearch = z.infer<typeof curatedCatalogSearchSchema>;
