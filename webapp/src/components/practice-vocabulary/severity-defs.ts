import { CircleAlertIcon, InfoIcon, OctagonAlertIcon, TriangleAlertIcon } from "lucide-react";

import type { Observation } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type Severity = Observation["severity"];

/**
 * How much a gap costs, ordered worst first. Every value is a real gap: a result's severity is at
 * least that of its worst problem, and each problem carries its own.
 *
 * `CRITICAL` and `MAJOR` share the destructive variant, so the icon is what ranks them: an octagon
 * (a stop sign) outranks a triangle.
 */
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
	INFO: {
		label: "Trivial",
		icon: InfoIcon,
		badgeVariant: "secondary",
		description: "A small gap. Fix it when it is cheap.",
	},
};
