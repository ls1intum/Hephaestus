import { z } from "zod";

export const CURATED_CATALOG_SEARCH_PARAMS: Array<keyof CuratedCatalogSearch> = [
	"q",
	"status",
	"artifact",
	"review",
];

export const curatedCatalogSearchSchema = z.object({
	q: z.string().max(200).optional().catch(undefined),
	status: z.enum(["OFFERED", "NOT_OFFERED"]).optional().catch(undefined),
	artifact: z.enum(["PULL_REQUEST", "ISSUE", "CONVERSATION_THREAD"]).optional().catch(undefined),
	review: z.literal(true).optional().catch(undefined),
});

export type CuratedCatalogSearch = z.infer<typeof curatedCatalogSearchSchema>;
