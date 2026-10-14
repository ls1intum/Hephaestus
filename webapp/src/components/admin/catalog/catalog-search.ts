import { z } from "zod";

import {
	type DetailStackEntry,
	detailStackSchema,
} from "@/components/layout/detail-drawer/detail-stack";
import { ITEM_KIND_VALUES } from "@/lib/item-kinds";

export const CATALOG_SEARCH_PARAMS: (keyof CatalogSearch)[] = ["q", "status", "kind", "review"];

/** The levels the instance catalog can render. Anything else in the URL is dropped by the schema. */
export const CATALOG_LEVEL_KINDS = [
	"practice-edit",
	"practice-new",
	"group-edit",
	"group-new",
] as const;

export type CatalogLevelKind = (typeof CATALOG_LEVEL_KINDS)[number];

/** What each level is called: the last crumb of its path, its title, and its crumb once covered. */
export const CATALOG_LEVEL_LABELS = {
	"practice-edit": "Edit practice",
	"practice-new": "Create practice",
	"group-edit": "Edit group",
	"group-new": "Create group",
} as const satisfies Record<CatalogLevelKind, string>;

/** Every level here is an editor, so every one holds a draft — see `GUARDED_LEVEL_KINDS`. */
export const GUARDED_CATALOG_LEVEL_KINDS = CATALOG_LEVEL_KINDS;

/** A stack id is always a slug; a practice or group that does not exist yet has none. */
const NEW_ENTRY_ID = "draft";

export function catalogPracticeLevel(slug?: string): DetailStackEntry<CatalogLevelKind> {
	return slug === undefined
		? { kind: "practice-new", id: NEW_ENTRY_ID }
		: { kind: "practice-edit", id: slug };
}

export function catalogGroupLevel(slug?: string): DetailStackEntry<CatalogLevelKind> {
	return slug === undefined
		? { kind: "group-new", id: NEW_ENTRY_ID }
		: { kind: "group-edit", id: slug };
}

const catalogFilterSchema = z.object({
	q: z.string().max(200).optional().catch(undefined),
	status: z.enum(["OFFERED", "NOT_OFFERED"]).optional().catch(undefined),
	kind: z.enum(ITEM_KIND_VALUES).optional().catch(undefined),
	review: z.literal(true).optional().catch(undefined),
});

export const catalogSearchSchema = catalogFilterSchema.extend(
	detailStackSchema(CATALOG_LEVEL_KINDS).shape,
);

export type CatalogSearch = z.infer<typeof catalogFilterSchema>;
