import type { PracticeAutonomy } from "@/lib/practice-autonomy";

import { AUTONOMY_DEFS } from "./autonomy-defs";
import { StatusBadge, type StatusBadgeProps } from "./StatusBadge";

export interface AutonomyBadgeProps extends Omit<StatusBadgeProps, "def"> {
	autonomy: PracticeAutonomy;
}

export function AutonomyBadge({ autonomy, ...props }: AutonomyBadgeProps) {
	return <StatusBadge def={AUTONOMY_DEFS[autonomy]} {...props} />;
}
