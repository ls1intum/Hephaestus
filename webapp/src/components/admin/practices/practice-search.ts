import { z } from "zod";
import { detailStackSchema } from "@/components/core/detail-drawer/detail-stack";
import { ARTIFACT_KIND_VALUES } from "@/lib/artifact-kinds";

export const PRACTICE_SEARCH_PARAMS: ("focus" | "library")[] = ["focus", "library"];

export const practiceSearchSchema = z.object({
	focus: z.enum(ARTIFACT_KIND_VALUES).optional().catch(undefined),
	library: z.boolean().optional().catch(undefined),
});

/** The levels Practice setup can render. Anything else in the URL is dropped by the schema. */
export const DETAIL_LEVEL_KINDS = ["catalog-area", "catalog-practice", "practice"] as const;

/**
 * Practice setup additionally owns the detail-drawer stack. `detail` is deliberately absent from
 * {@link PRACTICE_SEARCH_PARAMS}: retaining it would drag an open drawer onto the practice form.
 */
export const practiceSetupSearchSchema = practiceSearchSchema.extend(
	detailStackSchema(DETAIL_LEVEL_KINDS).shape,
);
