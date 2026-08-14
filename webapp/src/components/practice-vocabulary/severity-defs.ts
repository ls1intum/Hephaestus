import { CircleAlertIcon, InfoIcon, OctagonAlertIcon, TriangleAlertIcon } from "lucide-react";
import type { ReviewObservation } from "@/api/types.gen";
import type { StatusDefs } from "./status-def";

export type Severity = NonNullable<ReviewObservation["severity"]>;

/**
 * How much a missed practice costs, ordered worst first.
 *
 * `CRITICAL` and `MAJOR` share the destructive variant because they are both "fix this", and
 * inventing a sixth badge colour to separate them would put two reds next to each other that nobody
 * could rank. The icon is what ranks them: an octagon (a stop sign) outranks a triangle.
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
		label: "Informational",
		icon: InfoIcon,
		badgeVariant: "secondary",
		description: "Context for the author, with nothing being asked of them.",
	},
};
