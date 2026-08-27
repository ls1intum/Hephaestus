import { CircleAlert, CircleCheck, CornerDownLeft, Plus } from "lucide-react";
import type { CatalogGroupPracticeAction } from "@/api/types.gen";
import type { StatusDefs } from "./status-def";

export type CatalogGroupAction = CatalogGroupPracticeAction["action"];

/**
 * What adding a group does to each practice in it. Every row states its own outcome from here, so
 * the panel needs no prose explaining which of four lists a name ended up in.
 */
export const CATALOG_GROUP_ACTION_DEFS: StatusDefs<CatalogGroupAction> = {
	ADD: {
		label: "Adds",
		icon: Plus,
		badgeVariant: "secondary",
		description: "A new copy this workspace owns.",
	},
	MOVE_TO_GROUP: {
		label: "Moves back",
		icon: CornerDownLeft,
		badgeVariant: "secondary",
		description: "An unassigned copy returns here. Local edits are kept.",
	},
	KEEP: {
		label: "Already here",
		icon: CircleCheck,
		badgeVariant: "outline",
		description: "Nothing changes.",
	},
	BLOCKED: {
		label: "Blocked",
		icon: CircleAlert,
		badgeVariant: "warning",
		description: "Another practice already uses this name.",
	},
};

/** The actions that change the workspace, so a preview can count them without re-listing them. */
export const CATALOG_GROUP_CHANGE_ACTIONS: readonly CatalogGroupAction[] = ["ADD", "MOVE_TO_GROUP"];
