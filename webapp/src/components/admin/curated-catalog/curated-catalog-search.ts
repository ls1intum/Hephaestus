import { z } from "zod";
import { ARTIFACT_KIND_VALUES } from "@/lib/artifact-kinds";

export const CURATED_CATALOG_SEARCH_PARAMS: Array<keyof CuratedCatalogSearch> = [
	"q",
	"status",
	"artifact",
	"review",
];

export const curatedCatalogSearchSchema = z.object({
	q: z.string().max(200).optional().catch(undefined),
	status: z.enum(["OFFERED", "NOT_OFFERED"]).optional().catch(undefined),
	artifact: z.enum(ARTIFACT_KIND_VALUES).optional().catch(undefined),
	review: z.literal(true).optional().catch(undefined),
});

export type CuratedCatalogSearch = z.infer<typeof curatedCatalogSearchSchema>;
