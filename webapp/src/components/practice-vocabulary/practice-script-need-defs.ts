import { AsteriskIcon } from "lucide-react";

import type { PracticeScriptNeed } from "@/api/types.gen";

import type { StatusDef } from "@/components/common/status-def";

export type PracticeScriptNeedKind = PracticeScriptNeed["need"];

/**
 * A model without which a practice's script does not run. An optional model is the
 * normal case and draws nothing, so only the required one has words, an icon and a tone.
 */
export const PRACTICE_SCRIPT_REQUIRED_DEF: StatusDef = {
	label: "Required",
	icon: AsteriskIcon,
	badgeVariant: "secondary",
	description: "Without this model the practice script does not run.",
};

/** The badge a need wears, or null for an optional one, which wears none. */
export function practiceScriptNeedBadge(need: PracticeScriptNeedKind): StatusDef | null {
	return need === "required" ? PRACTICE_SCRIPT_REQUIRED_DEF : null;
}
