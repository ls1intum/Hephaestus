import { CircleAlertIcon, InfoIcon, OctagonAlertIcon, TriangleAlertIcon } from "lucide-react";

import type { Problem } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type Severity = Problem["severity"];

// Result severity is determined by its worst problem.
export const SEVERITY_DEFS: StatusDefs<Severity> = {
	CRITICAL: {
		label: "Critical",
		icon: OctagonAlertIcon,
		badgeVariant: "destructive",
		description: "Serious enough to hold up the work until it is addressed.",
	},
	MAJOR: {
		label: "Major",
		icon: TriangleAlertIcon,
		badgeVariant: "destructive",
		description: "Worth fixing before this work is considered done.",
	},
	MINOR: {
		label: "Minor",
		icon: CircleAlertIcon,
		badgeVariant: "warning",
		description: "Worth knowing about, but it does not block anything.",
	},
	TRIVIAL: {
		label: "Trivial",
		icon: InfoIcon,
		badgeVariant: "secondary",
		description: "A small gap. Fix it when it is cheap.",
	},
};
