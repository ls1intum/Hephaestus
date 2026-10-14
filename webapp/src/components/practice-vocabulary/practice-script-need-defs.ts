import { AsteriskIcon } from "lucide-react";

import type { PracticeScriptNeed } from "@/api/types.gen";

import type { StatusDef } from "@/components/common/status-def";

export type PracticeScriptNeedKind = PracticeScriptNeed["need"];

// Optional models do not block a script; reserve need warnings for required models.
export const PRACTICE_SCRIPT_REQUIRED_DEF: StatusDef = {
	label: "Required",
	icon: AsteriskIcon,
	badgeVariant: "secondary",
	description: "Without this model the practice script does not run.",
};

export function practiceScriptNeedBadge(need: PracticeScriptNeedKind): StatusDef | null {
	return need === "required" ? PRACTICE_SCRIPT_REQUIRED_DEF : null;
}
