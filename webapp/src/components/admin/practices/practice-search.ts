import { z } from "zod";
import { detailStackSchema } from "@/components/core/detail-drawer/detail-stack";
import { ARTIFACT_KIND_VALUES } from "@/lib/artifact-kinds";

export const PRACTICE_SEARCH_PARAMS: ("focus" | "library")[] = ["focus", "library"];

/**
 * What the practice form carries so that returning from it restores the surface you left.
 *
 * `detail` is included even though the form renders no drawer — only the index route does — so the
 * param rides along inert and the stack is still open when you come back.
 */
export const PRACTICE_SETUP_SEARCH_PARAMS: ("focus" | "library" | "detail")[] = [
	"focus",
	"library",
	"detail",
];

export const practiceSearchSchema = z.object({
	focus: z.enum(ARTIFACT_KIND_VALUES).optional().catch(undefined),
	library: z.boolean().optional().catch(undefined),
});

/** The levels Practice setup can render. Anything else in the URL is dropped by the schema. */
export const DETAIL_LEVEL_KINDS = ["catalog-area", "catalog-practice", "practice"] as const;

/**
 * Practice setup additionally owns the detail-drawer stack. `detail` is deliberately absent from
 * {@link PRACTICE_SETUP_SEARCH_PARAMS}.
 */
export const practiceSetupSearchSchema = practiceSearchSchema.extend(
	detailStackSchema(DETAIL_LEVEL_KINDS).shape,
);
