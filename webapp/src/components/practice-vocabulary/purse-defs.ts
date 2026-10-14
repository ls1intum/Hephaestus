import { KeyRoundIcon, type LucideIcon, ServerIcon } from "lucide-react";

import { statusValues } from "@/components/common/status-def";

export type Purse = "SHARED" | "OWN_PROVIDER";

interface PurseDef {
	label: string;
	icon: LucideIcon;
	description: string;
}

export const PURSE_DEFS = {
	SHARED: {
		label: "Shared models",
		icon: ServerIcon,
		description: "Set by an instance admin",
	},
	OWN_PROVIDER: {
		label: "Own provider",
		icon: KeyRoundIcon,
		description: "Billed to you by your provider",
	},
} satisfies Record<Purse, PurseDef>;

export const PURSES = statusValues(PURSE_DEFS);
